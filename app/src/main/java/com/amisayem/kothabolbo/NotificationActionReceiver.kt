package com.amisayem.kothabolbo

import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.concurrent.Executors

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reply = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(EXTRA_REMOTE_REPLY)?.toString().orEmpty()
        val action = intent.action ?: ACTION_REPLY
        val chat = intent.getStringExtra("chat")
        val text = if (action == ACTION_LIKE) "👍" else reply
        val pending = goAsync()
        Executors.newSingleThreadExecutor().execute {
            try {
                // This works without opening the UI. If an old/invalid session exists, fall back to the chat screen.
                if (!NativeReplySender.send(context.applicationContext, chat, text)) openChatFallback(context, chat, action, reply)
            } finally { pending.finish() }
        }
    }

    private fun openChatFallback(context: Context, chat: String?, action: String, reply: String) {
        context.startActivity(Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("chat", chat); putExtra("notificationAction", action); putExtra("notificationReply", reply)
        })
    }
    companion object {
        const val ACTION_REPLY = "com.amisayem.kothabolbo.REPLY"
        const val ACTION_LIKE = "com.amisayem.kothabolbo.LIKE"
        const val EXTRA_REMOTE_REPLY = "native_reply"
    }
}
