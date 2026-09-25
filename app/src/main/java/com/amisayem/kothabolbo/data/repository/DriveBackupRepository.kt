package com.amisayem.kothabolbo.data.repository

import android.accounts.Account
import android.content.Context
import com.amisayem.kothabolbo.core.AppConstants
import com.amisayem.kothabolbo.core.AppResult
import com.amisayem.kothabolbo.data.local.KothaDao
import com.amisayem.kothabolbo.data.local.RestoredMessageEntity
import com.amisayem.kothabolbo.domain.model.ChatMessage
import com.amisayem.kothabolbo.domain.model.ReplyReference
import com.amisayem.kothabolbo.domain.model.UserProfile
import com.google.android.gms.auth.GoogleAuthException
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.firebase.Timestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.time.Instant
import java.util.Date
import java.util.concurrent.TimeUnit

class DriveBackupRepository(
    private val context: Context,
    private val dao: KothaDao,
    private val userRepository: UserRepository,
    private val messageRepository: MessageRepository
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    suspend fun backup(account: Account, uid: String): AppResult<DriveBackupInfo> = withContext(Dispatchers.IO) {
        try {
            val user = userRepository.get(uid) ?: throw DriveFailure("DRIVE_BACKUP_ERROR", "Profile not found")
            val messages = messageRepository.messagesForBackup(uid)
            val payload = payloadJson(user, messages).toString()
            val token = token(account)
            val existing = findBackup(token)
            if (existing == null) createBackup(token, payload) else updateBackup(token, existing, payload)
            AppResult.Success(DriveBackupInfo(messages.size, System.currentTimeMillis(), account.name))
        } catch (t: Throwable) {
            driveError(t, restore = false)
        }
    }

    suspend fun restore(account: Account, uid: String): AppResult<DriveRestoreInfo> = withContext(Dispatchers.IO) {
        try {
            val token = token(account)
            val fileId = findBackup(token) ?: throw DriveFailure("DRIVE_BACKUP_NOT_FOUND", "No Kotha Bolbo backup was found in this Google account.")
            val json = download(token, fileId)
            if (json.isBlank()) throw DriveFailure("DRIVE_BACKUP_EMPTY", "The Drive backup is empty.")
            val root = JSONObject(json)
            val backupUid = root.optJSONObject("user")?.optString("uid").orEmpty()
            if (backupUid != uid) throw DriveFailure("DRIVE_PERMISSION_ERROR", "This backup belongs to a different Kotha Bolbo account.")
            val array = root.optJSONArray("messages") ?: JSONArray()
            val messages = buildList {
                repeat(array.length()) { index -> parseMessage(array.optJSONObject(index))?.let(::add) }
            }
            // Archive-only restore: these rows are never uploaded back to Firestore. IDs dedupe
            // repeated/manual restores, and current cloud messages win during UI merge.
            dao.restoreMessages(messages.map { RestoredMessageEntity.from(uid, it) })
            AppResult.Success(DriveRestoreInfo(messages.size, root.optString("createdAt"), account.name))
        } catch (t: Throwable) {
            driveError(t, restore = true)
        }
    }

    suspend fun deleteBackup(account: Account): AppResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = token(account)
            findBackup(token)?.let { fileId ->
                val request = Request.Builder().url("https://www.googleapis.com/drive/v3/files/$fileId")
                    .header("Authorization", "Bearer $token").delete().build()
                execute(request, allowEmpty = true)
            }
            AppResult.Success(Unit)
        } catch (t: Throwable) {
            driveError(t, restore = false)
        }
    }

    private fun token(account: Account): String = try {
        GoogleAuthUtil.getToken(context, account, "oauth2:${AppConstants.DRIVE_SCOPE}")
    } catch (t: GoogleAuthException) {
        throw DriveFailure("DRIVE_PERMISSION_ERROR", "Google Drive permission is required.", t)
    }

    private fun findBackup(token: String): String? {
        val q = URLEncoder.encode("name='${AppConstants.DRIVE_BACKUP_FILE}' and trashed=false", "UTF-8")
        val url = "https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&q=$q&fields=files(id,name,modifiedTime)&pageSize=10"
        val request = Request.Builder().url(url).header("Authorization", "Bearer $token").get().build()
        val files = JSONObject(execute(request)).optJSONArray("files") ?: return null
        return if (files.length() > 0) files.getJSONObject(0).getString("id") else null
    }

    private fun createBackup(token: String, payload: String) {
        val metadata = JSONObject().put("name", AppConstants.DRIVE_BACKUP_FILE).put("parents", JSONArray().put("appDataFolder"))
        val multipart = MultipartBody.Builder("kb-drive-${System.currentTimeMillis()}")
            .setType("multipart/related".toMediaType())
            .addPart(metadata.toString().toRequestBody(jsonType))
            .addPart(payload.toRequestBody(jsonType))
            .build()
        val request = Request.Builder().url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
            .header("Authorization", "Bearer $token").post(multipart).build()
        execute(request)
    }

    private fun updateBackup(token: String, fileId: String, payload: String) {
        val request = Request.Builder().url("https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media")
            .header("Authorization", "Bearer $token").patch(payload.toRequestBody(jsonType)).build()
        execute(request)
    }

    private fun download(token: String, fileId: String): String {
        val request = Request.Builder().url("https://www.googleapis.com/drive/v3/files/$fileId?alt=media")
            .header("Authorization", "Bearer $token").get().build()
        return execute(request)
    }

    private fun execute(request: Request, allowEmpty: Boolean = false): String {
        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val reason = runCatching {
                        JSONObject(body).optJSONObject("error")?.optJSONArray("errors")?.optJSONObject(0)?.optString("reason")
                    }.getOrNull().orEmpty()
                    when {
                        reason == "storageQuotaExceeded" -> throw DriveFailure("DRIVE_STORAGE_FULL", "Google Drive storage is full.")
                        response.code == 403 || reason.contains("permission", true) -> throw DriveFailure("DRIVE_PERMISSION_ERROR", "Google Drive permission was denied.")
                        response.code == 404 -> throw DriveFailure("DRIVE_BACKUP_NOT_FOUND", "Drive backup was not found.")
                        else -> throw DriveFailure("DRIVE_BACKUP_ERROR", "Google Drive request failed (${response.code}).")
                    }
                }
                if (!allowEmpty && body.isBlank()) return "{}"
                return body
            }
        } catch (e: IOException) {
            throw DriveFailure("DRIVE_BACKUP_ERROR", "Could not connect to Google Drive.", e)
        }
    }

    private fun payloadJson(user: UserProfile, messages: List<ChatMessage>) = JSONObject().apply {
        put("version", 1)
        put("createdAt", Instant.now().toString())
        put("user", JSONObject().apply {
            put("uid", user.uid); put("displayName", user.displayName); put("email", user.email)
            put("phone", user.phone); put("gender", user.gender); put("birthDay", user.birthDay)
            put("birthMonth", user.birthMonth); put("birthYear", user.birthYear); put("bio", user.bio)
            put("photoURL", user.photoURL); put("coverURL", user.coverURL)
        })
        put("messages", JSONArray().apply { messages.forEach { put(messageJson(it)) } })
    }

    private fun messageJson(m: ChatMessage) = JSONObject().apply {
        put("id", m.id); put("senderId", m.senderId); put("receiverId", m.receiverId)
        put("text", m.text); put("type", m.type); put("imageUrl", m.imageUrl); put("imageFileId", m.imageFileId)
        put("voiceUrl", m.voiceUrl); put("voiceFileId", m.voiceFileId); put("voiceDuration", m.voiceDuration)
        put("timestamp", m.sortTime); put("clientCreatedAt", m.clientCreatedAt); put("delivered", m.delivered)
        put("read", m.read); put("seen", m.seen); put("edited", m.edited)
        put("deletedFor", JSONArray(m.deletedFor)); put("deletedBySender", m.deletedBySender); put("deletedByReceiver", m.deletedByReceiver)
        m.replyTo?.let { put("replyTo", JSONObject().put("id", it.id).put("text", it.text).put("senderName", it.senderName)) }
    }

    private fun parseMessage(json: JSONObject?): ChatMessage? {
        json ?: return null
        val id = json.optString("id")
        if (id.isBlank()) return null
        val timestamp = json.optLong("timestamp", json.optLong("clientCreatedAt", 0L))
        val reply = json.optJSONObject("replyTo")?.let { ReplyReference(it.optString("id"), it.optString("text"), it.optString("senderName")) }
        val deleted = json.optJSONArray("deletedFor")?.let { array -> List(array.length()) { array.optString(it) }.filter(String::isNotBlank) }.orEmpty()
        return ChatMessage(
            id = id, senderId = json.optString("senderId"), receiverId = json.optString("receiverId"),
            text = json.optString("text"), type = json.optString("type", "text"),
            imageUrl = json.optString("imageUrl"), imageFileId = json.optString("imageFileId"),
            voiceUrl = json.optString("voiceUrl"), voiceFileId = json.optString("voiceFileId"),
            voiceDuration = json.optLong("voiceDuration"), timestamp = timestamp.takeIf { it > 0 }?.let { Timestamp(Date(it)) },
            clientCreatedAt = json.optLong("clientCreatedAt", timestamp), delivered = json.optBoolean("delivered"),
            read = json.optBoolean("read"), seen = json.optBoolean("seen"), edited = json.optBoolean("edited"),
            replyTo = reply, deletedFor = deleted, deletedBySender = json.optBoolean("deletedBySender"),
            deletedByReceiver = json.optBoolean("deletedByReceiver")
        )
    }

    private fun driveError(t: Throwable, restore: Boolean): AppResult.Error {
        val failure = t as? DriveFailure
        val fallback = if (restore) "DRIVE_RESTORE_ERROR" else "DRIVE_BACKUP_ERROR"
        return AppResult.Error(failure?.message ?: if (restore) "Drive restore failed." else "Drive backup failed.", failure?.code ?: fallback, t)
    }
}

data class DriveBackupInfo(val messageCount: Int, val createdAt: Long, val email: String)
data class DriveRestoreInfo(val messageCount: Int, val backupCreatedAt: String, val email: String)
class DriveFailure(val code: String, override val message: String, cause: Throwable? = null) : Exception(message, cause)
