package com.amisayem.kothabolbo

import android.content.Context
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

/** Google Drive appDataFolder backup using an explicit OAuth Bearer header. */
object DriveBackupManager {
    private const val BACKUP_NAME = "kothabolbo_backup.json"
    private val executor = Executors.newSingleThreadExecutor()

    fun upload(context: Context, json: String, callback: (Boolean, String) -> Unit) {
        executor.execute {
            try {
                val account = GoogleSignIn.getLastSignedInAccount(context)
                    ?: throw IllegalStateException("Connect Google account first")
                val email = account.email ?: throw IllegalStateException("Google account email is missing")
                val token = GoogleAuthUtil.getToken(context, email, "oauth2:https://www.googleapis.com/auth/drive.appdata")
                if (token.isBlank()) throw IllegalStateException("Google Drive access token is empty")

                val existingId = findBackupId(token)
                if (existingId == null) createBackup(token, json) else updateBackup(token, existingId, json)
                callback(true, "Backup completed")
            } catch (e: Exception) {
                android.util.Log.e("KothaDriveBackup", "Drive backup failed", e)
                val raw = "${e.message ?: ""} ${e.cause?.message ?: ""}"
                val friendly = when {
                    raw.contains("storageQuotaExceeded", ignoreCase = true) -> "DRIVE_STORAGE_FULL"
                    raw.contains("403", ignoreCase = true) || raw.contains("permission", ignoreCase = true) -> "DRIVE_PERMISSION_ERROR"
                    else -> "DRIVE_BACKUP_ERROR"
                }
                callback(false, friendly)
            }
        }
    }

    fun download(context: Context, callback: (Boolean, String) -> Unit) {
        executor.execute {
            try {
                val account = GoogleSignIn.getLastSignedInAccount(context)
                    ?: throw IllegalStateException("Connect Google account first")
                val email = account.email ?: throw IllegalStateException("Google account email is missing")
                val token = GoogleAuthUtil.getToken(context, email, "oauth2:https://www.googleapis.com/auth/drive.appdata")
                val id = findBackupId(token) ?: throw IllegalStateException("DRIVE_BACKUP_NOT_FOUND")
                val connection = (URL("https://www.googleapis.com/drive/v3/files/${URLEncoder.encode(id, "UTF-8")}?alt=media").openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"; setRequestProperty("Authorization", "Bearer $token")
                    connectTimeout = 20_000; readTimeout = 30_000
                }
                val json = readResponse(connection)
                if (connection.responseCode !in 200..299) throw IllegalStateException("Drive download failed ${connection.responseCode}: $json")
                if (json.isBlank()) throw IllegalStateException("DRIVE_BACKUP_EMPTY")
                callback(true, json)
            } catch (e: Exception) {
                android.util.Log.e("KothaDriveBackup", "Drive restore download failed", e)
                val raw = "${e.message ?: ""} ${e.cause?.message ?: ""}"
                val friendly = when {
                    raw.contains("DRIVE_BACKUP_NOT_FOUND") -> "DRIVE_BACKUP_NOT_FOUND"
                    raw.contains("403") || raw.contains("permission", true) -> "DRIVE_PERMISSION_ERROR"
                    else -> "DRIVE_RESTORE_ERROR"
                }
                callback(false, friendly)
            }
        }
    }

    fun delete(context: Context, callback: (Boolean, String) -> Unit) {
        executor.execute {
            try {
                val account = GoogleSignIn.getLastSignedInAccount(context) ?: throw IllegalStateException("Connect Google account first")
                val email = account.email ?: throw IllegalStateException("Google account email is missing")
                val token = GoogleAuthUtil.getToken(context, email, "oauth2:https://www.googleapis.com/auth/drive.appdata")
                val id = findBackupId(token) ?: run { callback(true, "No backup found"); return@execute }
                val connection = (URL("https://www.googleapis.com/drive/v3/files/${URLEncoder.encode(id, "UTF-8")}").openConnection() as HttpURLConnection).apply {
                    requestMethod = "DELETE"; setRequestProperty("Authorization", "Bearer $token")
                }
                val response = readResponse(connection)
                if (connection.responseCode !in 200..299 && connection.responseCode != 404) throw IllegalStateException("Drive delete failed ${connection.responseCode}: $response")
                callback(true, "Backup deleted")
            } catch (e: Exception) { callback(false, e.message ?: "Backup delete failed") }
        }
    }

    private fun findBackupId(token: String): String? {
        val query = URLEncoder.encode("name='$BACKUP_NAME' and trashed=false", "UTF-8")
        val url = URL("https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&fields=files(id,name)&q=$query")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $token")
            connectTimeout = 20_000
            readTimeout = 20_000
        }
        val response = readResponse(connection)
        if (connection.responseCode !in 200..299) throw IllegalStateException("Drive list failed ${connection.responseCode}: $response")
        val files = JSONObject(response).optJSONArray("files") ?: return null
        return if (files.length() > 0) files.getJSONObject(0).optString("id").ifBlank { null } else null
    }

    private fun createBackup(token: String, json: String) {
        val boundary = "KothaBolboBoundary${System.currentTimeMillis()}"
        val metadata = JSONObject().put("name", BACKUP_NAME).put("parents", org.json.JSONArray().put("appDataFolder")).toString()
        val body = "--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$metadata\r\n--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$json\r\n--$boundary--\r\n"
        val connection = (URL("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
        }
        writeBody(connection, body.toByteArray(Charsets.UTF_8))
        val response = readResponse(connection)
        if (connection.responseCode !in 200..299) throw IllegalStateException("Drive create failed ${connection.responseCode}: $response")
    }

    private fun updateBackup(token: String, fileId: String, json: String) {
        val connection = (URL("https://www.googleapis.com/upload/drive/v3/files/${URLEncoder.encode(fileId, "UTF-8")}?uploadType=media").openConnection() as HttpURLConnection).apply {
            requestMethod = "PATCH"; doOutput = true
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        }
        writeBody(connection, json.toByteArray(Charsets.UTF_8))
        val response = readResponse(connection)
        if (connection.responseCode !in 200..299) throw IllegalStateException("Drive update failed ${connection.responseCode}: $response")
    }

    private fun writeBody(connection: HttpURLConnection, bytes: ByteArray) {
        connection.outputStream.use { BufferedOutputStream(it).use { out -> out.write(bytes) } }
    }

    private fun readResponse(connection: HttpURLConnection): String {
        val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
        return stream?.bufferedReader()?.use { it.readText() } ?: ""
    }
}
