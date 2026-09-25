package com.amisayem.kothabolbo.data.remote

import com.amisayem.kothabolbo.core.AppConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class VercelApi {
    private val json = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun imageKitAuth(idToken: String): ImageKitAuth = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("${AppConstants.API_BASE}/api/imagekit-auth")
            .header("Authorization", "Bearer $idToken").get().build()
        executeJson(request).let {
            ImageKitAuth(it.getString("token"), it.getString("signature"), it.getLong("expire"))
        }
    }

    suspend fun sendNotification(idToken: String, payload: NotificationPayload) = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("receiverId", payload.receiverId)
            put("title", payload.title)
            put("body", payload.body)
            put("icon", payload.icon)
            put("image", payload.image)
            put("url", payload.url)
            put("messageType", payload.messageType)
            put("messageId", payload.messageId)
        }
        val request = Request.Builder().url("${AppConstants.API_BASE}/api/send-notification")
            .header("Authorization", "Bearer $idToken")
            .post(body.toString().toRequestBody(json)).build()
        executeJson(request); Unit
    }

    suspend fun deleteImageKitFile(idToken: String, fileId: String) = withContext(Dispatchers.IO) {
        val body = JSONObject().put("fileId", fileId)
        val request = Request.Builder().url("${AppConstants.API_BASE}/api/delete-imagekit-file")
            .header("Authorization", "Bearer $idToken")
            .post(body.toString().toRequestBody(json)).build()
        executeJson(request); Unit
    }

    suspend fun cleanupOldMedia(idToken: String) = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("${AppConstants.API_BASE}/api/cleanup-old-media")
            .header("Authorization", "Bearer $idToken")
            .post("{}".toRequestBody(json)).build()
        executeJson(request); Unit
    }

    private fun executeJson(request: Request): JSONObject {
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = runCatching { JSONObject(text).optString("message") }.getOrNull()
                throw ApiException(response.code, message?.takeIf { it.isNotBlank() } ?: "Server request failed (${response.code})")
            }
            return if (text.isBlank()) JSONObject() else JSONObject(text)
        }
    }
}

data class ImageKitAuth(val token: String, val signature: String, val expire: Long)

data class NotificationPayload(
    val receiverId: String,
    val title: String,
    val body: String,
    val icon: String,
    val image: String = "",
    val url: String,
    val messageType: String,
    val messageId: String
)

class ApiException(val statusCode: Int, override val message: String) : Exception(message)
