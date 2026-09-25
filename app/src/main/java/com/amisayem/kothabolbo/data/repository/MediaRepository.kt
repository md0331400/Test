package com.amisayem.kothabolbo.data.repository

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import com.amisayem.kothabolbo.core.AppConstants
import com.amisayem.kothabolbo.core.AppResult
import com.amisayem.kothabolbo.data.local.KothaDao
import com.amisayem.kothabolbo.data.remote.VercelApi
import com.amisayem.kothabolbo.domain.model.MediaUpload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.max

class MediaRepository(
    private val context: Context,
    private val authRepository: AuthRepository,
    private val api: VercelApi,
    private val dao: KothaDao
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun uploadImage(uri: Uri, folder: String): AppResult<MediaUpload> = runCatching {
        val bytes = compressImage(uri)
        require(bytes.size <= AppConstants.MAX_IMAGE_BYTES) { "Image must be 1 MB or smaller after compression." }
        upload(bytes, "image/jpeg", safeName(uri, "jpg"), folder)
    }.fold({ AppResult.Success(it) }, { AppResult.Error(it.message ?: "Image upload failed", cause = it) })

    suspend fun uploadFile(uri: Uri, folder: String, maxBytes: Long): AppResult<MediaUpload> = runCatching {
        val filePath = uri.path?.takeIf { uri.scheme == "file" }
        val size = filePath?.let { java.io.File(it).length() } ?: querySize(uri)
        if (size > maxBytes) error("File is too large. Maximum is ${maxBytes / (1024 * 1024)} MB.")
        val bytes = withContext(Dispatchers.IO) {
            if (filePath != null) java.io.File(filePath).readBytes()
            else context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } ?: error("Unable to read selected file")
        require(bytes.isNotEmpty()) { "The selected file is empty." }
        require(bytes.size.toLong() <= maxBytes) { "File is too large. Maximum is ${maxBytes / (1024 * 1024)} MB." }
        val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
        upload(bytes, mime, safeName(uri, mime.substringAfter('/').substringBefore(';')), folder)
    }.fold({ AppResult.Success(it) }, { AppResult.Error(it.message ?: "Media upload failed", cause = it) })

    suspend fun uploadBytes(bytes: ByteArray, mime: String, extension: String, folder: String): AppResult<MediaUpload> = runCatching {
        upload(bytes, mime, "kb_${System.currentTimeMillis()}_${UUID.randomUUID()}.$extension", folder)
    }.fold({ AppResult.Success(it) }, { AppResult.Error(it.message ?: "Upload failed", cause = it) })

    private suspend fun upload(bytes: ByteArray, mime: String, fileName: String, folder: String): MediaUpload = withContext(Dispatchers.IO) {
        val idToken = authRepository.idToken() ?: error("Your session expired. Please sign in again.")
        val signature = api.imageKitAuth(idToken)
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", fileName, bytes.toRequestBody(mime.toMediaTypeOrNull()))
            .addFormDataPart("fileName", fileName)
            .addFormDataPart("publicKey", AppConstants.IMAGEKIT_PUBLIC_KEY)
            .addFormDataPart("folder", folder)
            .addFormDataPart("signature", signature.signature)
            .addFormDataPart("token", signature.token)
            .addFormDataPart("expire", signature.expire.toString())
            .build()
        val request = Request.Builder().url("https://upload.imagekit.io/api/v1/files/upload").post(body).build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IllegalStateException(
                runCatching { JSONObject(text).optString("message") }.getOrDefault("Upload failed (${response.code})")
            )
            val json = JSONObject(text)
            MediaUpload(json.getString("url"), json.getString("fileId"), json.optString("name", fileName))
        }
    }

    suspend fun drainExpiredQueue(): Int {
        val token = authRepository.idToken() ?: return 0
        var deleted = 0
        dao.expiredMedia(System.currentTimeMillis()).forEach { item ->
            runCatching { api.deleteImageKitFile(token, item.fileId) }
                .onSuccess { dao.removeMedia(item.fileId); deleted++ }
                .onFailure { dao.bumpMediaAttempt(item.fileId) }
        }
        runCatching { api.cleanupOldMedia(token) }
        return deleted
    }

    suspend fun deleteNow(fileId: String): Boolean {
        if (fileId.isBlank()) return true
        val token = authRepository.idToken() ?: return false
        return runCatching { api.deleteImageKitFile(token, fileId); dao.removeMedia(fileId); true }.getOrDefault(false)
    }

    private suspend fun compressImage(uri: Uri): ByteArray = withContext(Dispatchers.Default) {
        val resolver = context.contentResolver
        val original = resolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Unable to read image")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(original, 0, original.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Unsupported image format" }
        var sample = 1
        while (bounds.outWidth / sample > 2048 || bounds.outHeight / sample > 2048) sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(original, 0, original.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: error("Unable to decode image")
        var current = bitmap
        var quality = 90
        var output = encodeJpeg(current, quality)
        while (output.size > AppConstants.MAX_IMAGE_BYTES && quality > 45) {
            quality -= 8
            output = encodeJpeg(current, quality)
        }
        while (output.size > AppConstants.MAX_IMAGE_BYTES && current.width > 480 && current.height > 480) {
            val scaled = Bitmap.createScaledBitmap(current, max(480, (current.width * .82).toInt()), max(480, (current.height * .82).toInt()), true)
            if (current !== bitmap) current.recycle()
            current = scaled
            output = encodeJpeg(current, 72)
        }
        if (current !== bitmap) current.recycle()
        bitmap.recycle()
        require(output.size <= AppConstants.MAX_IMAGE_BYTES) { "Image could not be compressed below 1 MB." }
        output
    }

    private fun encodeJpeg(bitmap: Bitmap, quality: Int): ByteArray = ByteArrayOutputStream().use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out); out.toByteArray()
    }

    private fun querySize(uri: Uri): Long = context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use {
        if (it.moveToFirst()) it.getLong(0) else -1L
    } ?: -1L

    private fun safeName(uri: Uri, extension: String): String {
        if (uri.scheme == "file") return uri.path?.let { java.io.File(it).name } ?: "kb_${System.currentTimeMillis()}.$extension"
        val queried = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
        val base = queried?.substringBeforeLast('.')?.replace(Regex("[^A-Za-z0-9_-]"), "_")?.take(60)
            ?: "kb_${System.currentTimeMillis()}"
        return "$base.$extension"
    }
}
