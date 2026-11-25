package com.amisayem.kothabolbo

import android.app.PendingIntent
import androidx.core.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Shader
import android.util.LruCache
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.graphics.drawable.IconCompat
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

object NotificationHelper {
    private val executor = Executors.newCachedThreadPool()
    // Small in-memory avatar cache so we do not re-download the same photo for every message.
    private val avatarCache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {} // ~8MB

    fun show(context: Context, data: Map<String, String>, fallbackTitle: String? = null, fallbackBody: String? = null) {
        val chatId = data["chat"] ?: data["senderId"] ?: chatFromUrl(data["url"])
        val title = data["title"] ?: fallbackTitle ?: "Kotha Bolbo"
        val body = data["body"] ?: fallbackBody ?: "You have a new message"
        // Stable per-conversation identity (never random per message).
        val sessionKey = data["senderId"] ?: chatId ?: data["tag"] ?: title
        val notificationId = sessionKey.hashCode()

        // Fetch the sender avatar asynchronously so we never block the main thread.
        executor.execute {
            val remoteAvatar = data["icon"] ?: data["avatar"] ?: data["photoURL"]
                ?: "https://api.dicebear.com/7.x/avataaars/png?seed=" + java.net.URLEncoder.encode(title, "UTF-8")
            val avatar = downloadBitmap(remoteAvatar)?.let(::circleAvatar)
            NotificationManagerCompat.from(context).notify(
                notificationId,
                buildMessaging(context, chatId, sessionKey, title, body, avatar, notificationId, data)
            )
        }
    }

    private fun buildMessaging(context: Context, chatId: String?, sessionKey: String, title: String, body: String, avatar: Bitmap?, notificationId: Int, data: Map<String, String>): android.app.Notification {
        val isGroup = !data["groupId"].isNullOrBlank() || !data["group"].isNullOrBlank()
        val groupTitle = data["groupName"] ?: data["groupTitle"] ?: title
        // The sender of this incoming message (Person carries name + avatar).
        val senderBuilder = Person.Builder().setName(title)
        if (avatar != null) senderBuilder.setIcon(IconCompat.createWithBitmap(avatar))
        val sender: Person = senderBuilder.build()
        // "Self" person for MessagingStyle — the app user.
        val self = Person.Builder().setName("Me").build()

        val style = NotificationCompat.MessagingStyle(self)
            .addMessage(MessagingMessage(body, System.currentTimeMillis(), sender))
        if (isGroup && groupTitle.isNotBlank()) {
            style.setConversationTitle(groupTitle)
            style.setGroupConversation(true)
        }

        val open = Intent(context, MainActivity::class.java).apply {
            putExtra("chat", chatId)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentPending = PendingIntent.getActivity(context, notificationId, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val remoteInput = RemoteInput.Builder(NotificationActionReceiver.EXTRA_REMOTE_REPLY).setLabel("Reply to $title").build()
        val replyIntent = Intent(context, NotificationActionReceiver::class.java).apply { action = NotificationActionReceiver.ACTION_REPLY; putExtra("chat", chatId) }
        val replyPending = PendingIntent.getBroadcast(context, notificationId + 1, replyIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
        val likeIntent = Intent(context, NotificationActionReceiver::class.java).apply { action = NotificationActionReceiver.ACTION_LIKE; putExtra("chat", chatId) }
        val likePending = PendingIntent.getBroadcast(context, notificationId + 2, likeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val sound = android.net.Uri.parse("android.resource://${context.packageName}/${R.raw.notification_sound}")

        val builder = NotificationCompat.Builder(context, KothaBolboApplication.CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_stat_chat)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setStyle(style)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setSound(sound)
            .setVibrate(longArrayOf(0, 180, 80, 180))
            .setContentIntent(contentPending)
            .addAction(NotificationCompat.Action.Builder(0, "Reply", replyPending).addRemoteInput(remoteInput).setAllowGeneratedReplies(true).build())
            .addAction(NotificationCompat.Action.Builder(0, "Like", likePending).build())
        // Use the sender's avatar as the large icon when available so the OS conversation
        // header shows the sender, keeping the small icon as the app icon.
        if (avatar != null) builder.setLargeIcon(avatar)
        return builder.build()
    }

    private fun downloadBitmap(url: String?): Bitmap? {
        if (url.isNullOrBlank() || !url.startsWith("https://")) return null
        avatarCache.get(url)?.let { return it }
        return try {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 6000
            c.readTimeout = 6000
            c.instanceFollowRedirects = true
            val bmp = c.inputStream.use { BitmapFactory.decodeStream(it) }
            if (bmp != null) avatarCache.put(url, bmp)
            bmp
        } catch (_: Exception) {
            null
        }
    }

    private fun circleAvatar(source: Bitmap): Bitmap {
        val size = minOf(source.width, source.height)
        val left = (source.width - size) / 2
        val top = (source.height - size) / 2
        val square = Bitmap.createBitmap(source, left, top, size, size)
        return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { output ->
            val canvas = Canvas(output)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = BitmapShader(square, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            }
            canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        }
    }

    private fun chatFromUrl(url: String?): String? = try { android.net.Uri.parse(url).getQueryParameter("chat") } catch (_: Exception) { null }
}

// Thin alias to keep the code readable while using androidx MessagingStyle.Message.
private typealias MessagingMessage = androidx.core.app.NotificationCompat.MessagingStyle.Message
