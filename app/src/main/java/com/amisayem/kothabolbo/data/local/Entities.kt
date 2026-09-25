package com.amisayem.kothabolbo.data.local

import androidx.room.Entity
import com.amisayem.kothabolbo.domain.model.ChatMessage
import com.amisayem.kothabolbo.domain.model.ReplyReference
import com.amisayem.kothabolbo.domain.model.UserProfile
import com.google.firebase.Timestamp
import java.util.Date

@Entity(tableName = "restored_messages", primaryKeys = ["ownerUid", "messageId"])
data class RestoredMessageEntity(
    val ownerUid: String,
    val messageId: String,
    val senderId: String,
    val receiverId: String,
    val text: String,
    val type: String,
    val imageUrl: String,
    val imageFileId: String,
    val voiceUrl: String,
    val voiceFileId: String,
    val voiceDuration: Long,
    val timestampMs: Long,
    val clientCreatedAt: Long,
    val delivered: Boolean,
    val read: Boolean,
    val seen: Boolean,
    val edited: Boolean,
    val replyId: String,
    val replyText: String,
    val replySenderName: String,
    val deletedForCsv: String,
    val deletedBySender: Boolean,
    val deletedByReceiver: Boolean
) {
    fun toDomain(): ChatMessage = ChatMessage(
        id = messageId,
        senderId = senderId,
        receiverId = receiverId,
        text = text,
        type = type,
        imageUrl = imageUrl,
        imageFileId = imageFileId,
        voiceUrl = voiceUrl,
        voiceFileId = voiceFileId,
        voiceDuration = voiceDuration,
        timestamp = timestampMs.takeIf { it > 0 }?.let { Timestamp(Date(it)) },
        clientCreatedAt = clientCreatedAt,
        delivered = delivered,
        read = read,
        seen = seen,
        edited = edited,
        replyTo = replyId.takeIf { it.isNotBlank() }?.let { ReplyReference(it, replyText, replySenderName) },
        deletedFor = deletedForCsv.split('|').filter { it.isNotBlank() },
        deletedBySender = deletedBySender,
        deletedByReceiver = deletedByReceiver
    )

    companion object {
        fun from(ownerUid: String, m: ChatMessage) = RestoredMessageEntity(
            ownerUid, m.id, m.senderId, m.receiverId, m.text, m.type,
            m.imageUrl, m.imageFileId, m.voiceUrl, m.voiceFileId, m.voiceDuration,
            m.sortTime, m.clientCreatedAt, m.delivered, m.read, m.seen, m.edited,
            m.replyTo?.id.orEmpty(), m.replyTo?.text.orEmpty(), m.replyTo?.senderName.orEmpty(),
            m.deletedFor.joinToString("|"), m.deletedBySender, m.deletedByReceiver
        )
    }
}

@Entity(tableName = "media_delete_queue")
data class MediaDeleteEntity(
    @androidx.room.PrimaryKey val fileId: String,
    val ownerUid: String,
    val expiresAt: Long,
    val kind: String,
    val attempts: Int = 0
)

@Entity(tableName = "chat_cache", primaryKeys = ["ownerUid", "partnerUid"])
data class ChatCacheEntity(
    val ownerUid: String,
    val partnerUid: String,
    val displayName: String,
    val photoURL: String,
    val lastText: String,
    val lastType: String,
    val lastTimestamp: Long,
    val unread: Int
)

@Entity(tableName = "profile_cache")
data class ProfileCacheEntity(
    @androidx.room.PrimaryKey val uid: String,
    val displayName: String,
    val email: String,
    val phone: String,
    val gender: String,
    val bio: String,
    val photoURL: String,
    val coverURL: String,
    val birthDay: Int,
    val birthMonth: Int,
    val birthYear: Int,
    val emailPublic: Boolean,
    val phonePublic: Boolean,
    val dobPublicDay: Boolean,
    val dobPublicMonth: Boolean,
    val dobPublicYear: Boolean,
    val cachedAt: Long
) {
    fun toDomain() = UserProfile(
        uid = uid, displayName = displayName, email = email, phone = phone,
        gender = gender, bio = bio, photoURL = photoURL, coverURL = coverURL,
        birthDay = birthDay, birthMonth = birthMonth, birthYear = birthYear,
        emailPublic = emailPublic, phonePublic = phonePublic,
        dobPublicDay = dobPublicDay, dobPublicMonth = dobPublicMonth, dobPublicYear = dobPublicYear
    )

    companion object {
        fun from(p: UserProfile) = ProfileCacheEntity(
            p.uid, p.displayName, p.email, p.phone, p.gender, p.bio, p.photoURL, p.coverURL,
            p.birthDay, p.birthMonth, p.birthYear, p.emailPublic, p.phonePublic,
            p.dobPublicDay, p.dobPublicMonth, p.dobPublicYear, System.currentTimeMillis()
        )
    }
}

@Entity(tableName = "drafts", primaryKeys = ["ownerUid", "partnerUid"])
data class DraftEntity(
    val ownerUid: String,
    val partnerUid: String,
    val text: String,
    val updatedAt: Long
)
