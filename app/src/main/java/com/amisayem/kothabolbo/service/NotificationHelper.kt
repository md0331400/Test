package com.amisayem.kothabolbo.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.graphics.drawable.IconCompat
import com.amisayem.kothabolbo.MainActivity
import com.amisayem.kothabolbo.R
import com.amisayem.kothabolbo.core.AppConstants
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap

object NotificationHelper {
    private val histories = ConcurrentHashMap<String, ArrayDeque<HistoryLine>>()
    private val avatarCache = Collections.synchronizedMap(object : LinkedHashMap<String, Bitmap>(24, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean = size > 24
    })

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val sound = Uri.parse("android.resource://${context.packageName}/${R.raw.notification_sound}")
        val audio = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT).build()
        val channel = NotificationChannel(
            AppConstants.NOTIFICATION_CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notification_channel_description)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 220, 90, 220)
            setSound(sound, audio)
            setShowBadge(true)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun show(context: Context, data: Map<String, String>) {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return
        val url = data["url"].orEmpty()
        val senderId = data["senderId"].orEmpty().ifBlank { Regex("[?&]chat=([^&]+)").find(url)?.groupValues?.getOrNull(1).orEmpty() }
        if (senderId.isBlank()) return
        val title = data["title"].orEmpty().ifBlank { "Kotha Bolbo" }
        val body = data["body"].orEmpty().ifBlank { "New message" }
        val iconUrl = data["icon"].orEmpty()
        val messageId = data["messageId"].orEmpty()
        val avatar = loadAvatar(iconUrl)
        val personBuilder = Person.Builder().setName(title).setKey(senderId)
        avatar?.let { personBuilder.setIcon(IconCompat.createWithBitmap(it)) }
        val sender = personBuilder.build()
        val me = Person.Builder().setName(AppConstants.APP_NAME).setKey("me").build()

        val history = histories.getOrPut(senderId) { ArrayDeque() }
        synchronized(history) {
            history.addLast(HistoryLine(body, System.currentTimeMillis(), sender))
            while (history.size > 8) history.removeFirst()
        }
        val style = NotificationCompat.MessagingStyle(me).setConversationTitle(title).setGroupConversation(false)
        synchronized(history) { history.forEach { style.addMessage(it.text, it.at, it.person) } }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(AppConstants.EXTRA_CHAT, senderId)
        }
        val contentIntent = PendingIntent.getActivity(
            context, senderId.hashCode(), openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val actionBase = Intent(context, NotificationActionReceiver::class.java).apply {
            putExtra(AppConstants.EXTRA_SENDER_ID, senderId)
            putExtra(AppConstants.EXTRA_SENDER_NAME, title)
            putExtra(AppConstants.EXTRA_MESSAGE_ID, messageId)
        }
        val replyIntent = Intent(actionBase).setAction(AppConstants.ACTION_REPLY)
        val replyPending = PendingIntent.getBroadcast(
            context, senderId.hashCode(), replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        val remoteInput = RemoteInput.Builder(AppConstants.NOTIFICATION_REPLY_KEY)
            .setLabel(context.getString(R.string.notification_reply_hint)).build()
        val replyAction = NotificationCompat.Action.Builder(
            R.drawable.ic_stat_chat, context.getString(R.string.notification_reply), replyPending
        ).addRemoteInput(remoteInput).setAllowGeneratedReplies(true).build()

        val likePending = PendingIntent.getBroadcast(
            context, senderId.hashCode() xor 0x4B42,
            Intent(actionBase).setAction(AppConstants.ACTION_LIKE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val likeAction = NotificationCompat.Action.Builder(
            0, context.getString(R.string.notification_like), likePending
        ).build()

        val notification = NotificationCompat.Builder(context, AppConstants.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_chat)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(style)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setGroup("kb_chat_$senderId")
            .addAction(replyAction)
            .addAction(likeAction)
            .apply { avatar?.let { setLargeIcon(it) } }
            .build()
        NotificationManagerCompat.from(context).notify(senderId.hashCode(), notification)
    }

    fun showActionFailure(context: Context, senderId: String, senderName: String, pendingText: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(AppConstants.EXTRA_CHAT, senderId)
            putExtra("pendingText", pendingText)
            putExtra("pendingActionId", System.currentTimeMillis().toString())
        }
        val pi = PendingIntent.getActivity(context, senderId.hashCode() xor 0xFA11, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, AppConstants.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_chat)
            .setContentTitle("Couldn’t send to $senderName")
            .setContentText("Tap to open the chat and send your message.")
            .setContentIntent(pi).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_HIGH).build()
        if (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(context).notify(senderId.hashCode() xor 0xFA11, notification)
        }
    }

    fun cancel(context: Context, senderId: String) {
        NotificationManagerCompat.from(context).cancel(senderId.hashCode())
        histories.remove(senderId)
    }

    private fun loadAvatar(url: String): Bitmap? {
        if (url.isBlank()) return null
        avatarCache[url]?.let { return it }
        return runCatching {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 3_500; connection.readTimeout = 3_500
            connection.inputStream.use { BitmapFactory.decodeStream(it) }?.let(::circleCrop)?.also { avatarCache[url] = it }
        }.getOrNull()
    }

    private fun circleCrop(source: Bitmap): Bitmap {
        val size = minOf(source.width, source.height)
        val x = (source.width - size) / 2f
        val y = (source.height - size) / 2f
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val path = Path().apply { addCircle(size / 2f, size / 2f, size / 2f, Path.Direction.CW) }
        canvas.clipPath(path)
        canvas.drawBitmap(source, -x, -y, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        return output
    }

    private data class HistoryLine(val text: String, val at: Long, val person: Person)
}
