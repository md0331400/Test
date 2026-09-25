package com.amisayem.kothabolbo.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.amisayem.kothabolbo.KothaBolboApplication
import com.amisayem.kothabolbo.core.AppConstants
import com.amisayem.kothabolbo.core.AppResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val senderId = intent.getStringExtra(AppConstants.EXTRA_SENDER_ID).orEmpty()
        val senderName = intent.getStringExtra(AppConstants.EXTRA_SENDER_NAME).orEmpty().ifBlank { "this chat" }
        val text = when (intent.action) {
            AppConstants.ACTION_LIKE -> "👍"
            AppConstants.ACTION_REPLY -> RemoteInput.getResultsFromIntent(intent)
                ?.getCharSequence(AppConstants.NOTIFICATION_REPLY_KEY)?.toString()?.trim().orEmpty()
            else -> ""
        }
        if (senderId.isBlank() || text.isBlank()) { pending.finish(); return }
        val app = context.applicationContext as KothaBolboApplication
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val uid = app.container.authRepository.currentUid
                val me = uid?.let { app.container.userRepository.get(it) }
                val other = app.container.userRepository.get(senderId)
                val result = if (me != null && other != null) app.container.messageRepository.send(me, other, text)
                else AppResult.Error("Signed-out session")
                if (result is AppResult.Success) NotificationHelper.cancel(context, senderId)
                else NotificationHelper.showActionFailure(context, senderId, senderName, text)
            } catch (_: Throwable) {
                NotificationHelper.showActionFailure(context, senderId, senderName, text)
            } finally {
                pending.finish()
            }
        }
    }
}
