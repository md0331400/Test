package com.amisayem.kothabolbo

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class KothaFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        getSharedPreferences("native", MODE_PRIVATE).edit().putString("fcm", token).apply()
    }

    override fun onMessageReceived(message: RemoteMessage) {
        // Normalize all profile-photo fields that a backend may send into the field used by NotificationHelper.
        val data = message.data.toMutableMap()
        val notification = message.notification
        if (data["icon"].isNullOrBlank()) {
            data["icon"] = data["avatar"] ?: data["photoURL"] ?: data["profilePhoto"] ?: data["imageUrl"]
                ?: notification?.imageUrl?.toString().orEmpty()
        }
        if (data["title"].isNullOrBlank() && notification?.title != null) data["title"] = notification.title!!
        if (data["body"].isNullOrBlank() && notification?.body != null) data["body"] = notification.body!!
        if (!KothaBolboApplication.instance.isAppInForeground) {
            NotificationHelper.show(this, data, notification?.title, notification?.body)
        }
    }
}
