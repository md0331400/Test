package com.amisayem.kothabolbo.domain.model

import com.google.firebase.Timestamp

data class ReplyReference(
    val id: String = "",
    val text: String = "",
    val senderName: String = ""
)

data class ChatMessage(
    val id: String = "",
    val senderId: String = "",
    val receiverId: String = "",
    val text: String = "",
    val type: String = "text",
    val imageUrl: String = "",
    val imageFileId: String = "",
    val voiceUrl: String = "",
    val voiceFileId: String = "",
    val voiceDuration: Long = 0,
    val timestamp: Timestamp? = null,
    val clientCreatedAt: Long = 0,
    val delivered: Boolean = false,
    val read: Boolean = false,
    val seen: Boolean = false,
    val edited: Boolean = false,
    val replyTo: ReplyReference? = null,
    val deletedFor: List<String> = emptyList(),
    val deletedBySender: Boolean = false,
    val deletedByReceiver: Boolean = false,
    val pending: Boolean = false,
    val failed: Boolean = false
) {
    val sortTime: Long get() = timestamp?.toDate()?.time ?: clientCreatedAt
    fun visibleTo(uid: String): Boolean = uid !in deletedFor &&
        !(uid == senderId && deletedBySender) &&
        !(uid == receiverId && deletedByReceiver)
    fun partnerFor(uid: String): String = if (senderId == uid) receiverId else senderId
}

data class ChatContact(
    val uid: String = "",
    val displayName: String = "",
    val photoURL: String = "",
    val addedAt: Timestamp? = null,
    val removed: Boolean = false,
    val removedAt: Timestamp? = null
)

data class ChatSummary(
    val user: UserProfile,
    val lastMessage: ChatMessage? = null,
    val unread: Int = 0,
    val isOnline: Boolean = false,
    val fromArchive: Boolean = false
) {
    val sortTime: Long get() = lastMessage?.sortTime ?: 0L
}

data class Presence(
    val state: String = "offline",
    val lastChanged: Long = 0L
)

data class TypingState(
    val typing: Boolean = false,
    val updatedAt: Long = 0L
)

enum class MessageStatus(val label: String) {
    SENDING("Sending"), SENT("Sent"), DELIVERED("Delivered"), SEEN("Seen");

    companion object {
        fun of(message: ChatMessage): MessageStatus = when {
            message.pending -> SENDING
            message.seen || message.read -> SEEN
            message.delivered -> DELIVERED
            else -> SENT
        }
    }
}
