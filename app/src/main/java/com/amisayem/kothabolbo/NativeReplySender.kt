package com.amisayem.kothabolbo

import android.content.Context
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

/** Sends Reply/Like directly from the notification using the signed-in Firebase Web session. */
object NativeReplySender {
    private const val API_KEY = "AIzaSyC9z4IV2jr9c53MlD5NUp5t8BPhHJ0_QjY"
    private const val PROJECT = "kotha-bolbo-aso"
    private const val PUSH_URL = "https://kothabolbo.vercel.app/api/send-notification"

    fun send(context: Context, receiverUid: String?, text: String): Boolean {
        if (receiverUid.isNullOrBlank() || text.isBlank()) return false
        val prefs = context.getSharedPreferences("native_session", Context.MODE_PRIVATE)
        val uid = prefs.getString("uid", null) ?: return false
        val refresh = prefs.getString("refreshToken", null) ?: return false
        return try {
            val tokenData = refreshToken(refresh) ?: return false
            prefs.edit().putString("idToken", tokenData.first).putString("refreshToken", tokenData.second).apply()
            val idToken = tokenData.first
            val fields = JSONObject()
                .put("senderId", stringField(uid))
                .put("receiverId", stringField(receiverUid))
                .put("text", stringField(text))
                .put("timestamp", JSONObject().put("timestampValue", Instant.now().toString()))
                .put("delivered", JSONObject().put("booleanValue", false))
                .put("read", JSONObject().put("booleanValue", false))
                .put("seen", JSONObject().put("booleanValue", false))
            val firestore = JSONObject().put("fields", fields)
            val firestoreCode = post("https://firestore.googleapis.com/v1/projects/$PROJECT/databases/(default)/documents/messages", firestore.toString(), idToken)
            if (firestoreCode !in 200..299) return false

            val name = prefs.getString("displayName", "Kotha Bolbo") ?: "Kotha Bolbo"
            val photo = prefs.getString("photoUrl", "") ?: ""
            val push = JSONObject()
                .put("receiverId", receiverUid).put("title", name).put("body", text)
                .put("icon", photo).put("image", "")
                .put("url", "/?chat=$uid").put("messageType", "text")
            post(PUSH_URL, push.toString(), idToken)
            true
        } catch (_: Exception) { false }
    }

    private fun refreshToken(refreshToken: String): Pair<String, String>? {
        val url = URL("https://securetoken.googleapis.com/v1/token?key=$API_KEY")
        val c = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 12000; readTimeout = 12000
            doOutput = true; setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        }
        c.outputStream.use { it.write("grant_type=refresh_token&refresh_token=${java.net.URLEncoder.encode(refreshToken, "UTF-8")}".toByteArray()) }
        if (c.responseCode !in 200..299) return null
        val body = c.inputStream.bufferedReader().use { it.readText() }
        val result = JSONObject(body)
        return result.optString("id_token").takeIf { it.isNotBlank() }?.let { it to result.optString("refresh_token", refreshToken) }
    }

    private fun post(url: String, body: String, idToken: String): Int {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 12000; readTimeout = 12000; doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $idToken")
        }
        OutputStreamWriter(c.outputStream, Charsets.UTF_8).use { it.write(body) }
        return c.responseCode
    }
    private fun stringField(value: String) = JSONObject().put("stringValue", value)
}
