package com.amisayem.kothabolbo

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner

class KothaBolboApplication : Application(), DefaultLifecycleObserver {
    @Volatile var isAppInForeground = false
        private set

    override fun onCreate() {
        super<Application>.onCreate()
        instance = this
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        val sound = android.net.Uri.parse("android.resource://$packageName/${R.raw.notification_sound}")
        val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT).build()
        val channel = NotificationChannel(CHANNEL_MESSAGES, "Messages", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "New Kotha Bolbo messages"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 220, 90, 220)
            setSound(sound, attributes)
            setShowBadge(true)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
    override fun onStart(owner: LifecycleOwner) { isAppInForeground = true }
    override fun onStop(owner: LifecycleOwner) { isAppInForeground = false }
    fun setForeground(value: Boolean) { isAppInForeground = value }

    companion object {
        const val CHANNEL_MESSAGES = "chat_messages_v3"
        const val CALL_CHANNEL = "incoming_calls_v1"
        lateinit var instance: KothaBolboApplication
            private set
    }
}
