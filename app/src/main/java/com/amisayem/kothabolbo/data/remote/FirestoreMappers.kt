package com.amisayem.kothabolbo.data.remote

import com.amisayem.kothabolbo.domain.model.ChatContact
import com.amisayem.kothabolbo.domain.model.ChatMessage
import com.amisayem.kothabolbo.domain.model.Post
import com.amisayem.kothabolbo.domain.model.ReplyReference
import com.amisayem.kothabolbo.domain.model.Story
import com.amisayem.kothabolbo.domain.model.UserProfile
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot

internal fun DocumentSnapshot.toUser(): UserProfile? {
    if (!exists()) return null
    return UserProfile(
        uid = getString("uid") ?: id,
        displayName = getString("displayName").orEmpty(),
        email = getString("email").orEmpty(),
        phone = getString("phone").orEmpty(),
        gender = getString("gender").orEmpty(),
        birthDay = getLong("birthDay")?.toInt() ?: 0,
        birthMonth = getLong("birthMonth")?.toInt() ?: 0,
        birthYear = getLong("birthYear")?.toInt() ?: 0,
        dobPublicDay = getBoolean("dobPublicDay") ?: false,
        dobPublicMonth = getBoolean("dobPublicMonth") ?: false,
        dobPublicYear = getBoolean("dobPublicYear") ?: false,
        emailPublic = getBoolean("emailPublic") ?: false,
        phonePublic = getBoolean("phonePublic") ?: false,
        bio = getString("bio").orEmpty(),
        photoURL = getString("photoURL").orEmpty(),
        photoFileId = getString("photoFileId").orEmpty(),
        coverURL = getString("coverURL").orEmpty(),
        coverFileId = getString("coverFileId").orEmpty(),
        createdAt = getTimestamp("createdAt"),
        lastProfileEdit = getTimestamp("lastProfileEdit"),
        fcmToken = getString("fcmToken").orEmpty(),
        fcmTokens = (get("fcmTokens") as? List<*>)?.filterIsInstance<String>().orEmpty(),
        googleLinked = getBoolean("googleLinked") ?: false
    )
}

@Suppress("UNCHECKED_CAST")
internal fun DocumentSnapshot.toMessage(): ChatMessage? {
    if (!exists()) return null
    val reply = get("replyTo") as? Map<String, Any?>
    return ChatMessage(
        id = id,
        senderId = getString("senderId").orEmpty(),
        receiverId = getString("receiverId").orEmpty(),
        text = getString("text").orEmpty(),
        type = getString("type") ?: "text",
        imageUrl = getString("imageUrl").orEmpty(),
        imageFileId = getString("imageFileId").orEmpty(),
        voiceUrl = getString("voiceUrl").orEmpty(),
        voiceFileId = getString("voiceFileId").orEmpty(),
        voiceDuration = getLong("voiceDuration") ?: 0,
        timestamp = getTimestamp("timestamp"),
        clientCreatedAt = getLong("clientCreatedAt") ?: 0,
        delivered = getBoolean("delivered") ?: false,
        read = getBoolean("read") ?: false,
        seen = getBoolean("seen") ?: false,
        edited = getBoolean("edited") ?: false,
        replyTo = reply?.let {
            ReplyReference(
                id = it["id"] as? String ?: "",
                text = it["text"] as? String ?: "",
                senderName = it["senderName"] as? String ?: ""
            )
        },
        deletedFor = (get("deletedFor") as? List<*>)?.filterIsInstance<String>().orEmpty(),
        deletedBySender = getBoolean("deletedBySender") ?: false,
        deletedByReceiver = getBoolean("deletedByReceiver") ?: false,
        pending = metadata.hasPendingWrites()
    )
}

internal fun DocumentSnapshot.toContact(): ChatContact? = if (!exists()) null else ChatContact(
    uid = getString("uid") ?: id,
    displayName = getString("displayName").orEmpty(),
    photoURL = getString("photoURL").orEmpty(),
    addedAt = getTimestamp("addedAt"),
    removed = getBoolean("removed") ?: false,
    removedAt = getTimestamp("removedAt")
)

internal fun DocumentSnapshot.toPost(): Post? = if (!exists()) null else Post(
    id = id,
    authorId = getString("authorId").orEmpty(),
    authorName = getString("authorName").orEmpty(),
    authorPhoto = getString("authorPhoto").orEmpty(),
    text = getString("text").orEmpty(),
    imageUrl = getString("imageUrl").orEmpty(),
    imageFileId = getString("imageFileId").orEmpty(),
    createdAt = getTimestamp("createdAt"),
    privacy = getString("privacy") ?: "public",
    likes = (get("likes") as? List<*>)?.filterIsInstance<String>().orEmpty(),
    likeCount = getLong("likeCount") ?: 0
)

internal fun DocumentSnapshot.toStory(): Story? = if (!exists()) null else Story(
    id = id,
    authorId = getString("authorId").orEmpty(),
    authorName = getString("authorName").orEmpty(),
    authorPhoto = getString("authorPhoto").orEmpty(),
    type = getString("mediaType") ?: getString("type") ?: "text",
    text = getString("text").orEmpty(),
    caption = getString("caption").orEmpty(),
    mediaUrl = getString("mediaUrl") ?: getString("imageUrl").orEmpty(),
    mediaFileId = getString("fileId") ?: getString("mediaFileId").orEmpty(),
    createdAt = getTimestamp("createdAt"),
    expiresAt = getTimestamp("expiresAt"),
    privacy = getString("privacy") ?: "public"
)

internal fun timestampOrNow(value: Timestamp?): Long = value?.toDate()?.time ?: System.currentTimeMillis()
