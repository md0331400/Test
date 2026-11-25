package com.amisayem.kothabolbo

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class DailyReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val launch = PendingIntent.getActivity(context, 60019, Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val body = "Hi friends 👋, I'm \"Kotha Bolbo\". Don't forget me. Have you talked to anyone today? If not, start a conversation now!"
        val appAvatar = BitmapFactory.decodeResource(context.resources, R.drawable.kb_app_icon)
        val notification = NotificationCompat.Builder(context, KothaBolboApplication.CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_stat_chat)
            .setLargeIcon(appAvatar)
            .setContentTitle("Kotha Bolbo")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(launch)
            .build()
        NotificationManagerCompat.from(context).notify(60018, notification)
        // Set tomorrow's 6:00 PM alarm after today's notification fires.
        DailyReminderScheduler.schedule(context)
    }
}
