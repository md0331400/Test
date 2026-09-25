package com.amisayem.kothabolbo.service

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.amisayem.kothabolbo.KothaBolboApplication
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class KothaMessagingService : FirebaseMessagingService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(message: RemoteMessage) {
        // Data-only push is required for reliable Reply/Like and foreground behavior.
        val data = message.data.toMutableMap().apply {
            message.notification?.let { n ->
                putIfAbsent("title", n.title.orEmpty())
                putIfAbsent("body", n.body.orEmpty())
                putIfAbsent("image", n.imageUrl?.toString().orEmpty())
            }
            putIfAbsent("icon", get("avatar") ?: get("photoURL") ?: get("profilePhoto") ?: get("imageUrl").orEmpty())
        }
        val foreground = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        if (!foreground) NotificationHelper.show(this, data)
    }

    override fun onNewToken(token: String) {
        val container = (application as KothaBolboApplication).container
        val uid = container.authRepository.currentUid ?: return
        scope.launch { runCatching { container.userRepository.saveFcmToken(uid, token) } }
    }
}
