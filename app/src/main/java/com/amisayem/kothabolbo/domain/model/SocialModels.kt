package com.amisayem.kothabolbo.domain.model

import com.google.firebase.Timestamp

data class Post(
    val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorPhoto: String = "",
    val text: String = "",
    val imageUrl: String = "",
    val imageFileId: String = "",
    val createdAt: Timestamp? = null,
    val privacy: String = "public",
    val likes: List<String> = emptyList(),
    val likeCount: Long = 0
) {
    val createdAtMs: Long get() = createdAt?.toDate()?.time ?: 0L
}

data class Story(
    val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorPhoto: String = "",
    val type: String = "text",
    val text: String = "",
    val caption: String = "",
    val mediaUrl: String = "",
    val mediaFileId: String = "",
    val createdAt: Timestamp? = null,
    val expiresAt: Timestamp? = null,
    val privacy: String = "public"
) {
    val createdAtMs: Long get() = createdAt?.toDate()?.time ?: 0L
    val expiresAtMs: Long get() = expiresAt?.toDate()?.time ?: createdAtMs + 86_400_000L
}

data class VersionConfig(
    val latestVersionCode: Long = 40,
    val latestVersionName: String = "1.0.40",
    val updateUrl: String = "",
    val forceUpdate: Boolean = false,
    val message: String = "A new version of Kotha Bolbo is available."
)

data class MediaUpload(
    val url: String,
    val fileId: String,
    val fileName: String
)

data class BackupPayload(
    val version: Int = 1,
    val createdAt: String,
    val user: UserProfile,
    val messages: List<ChatMessage>
)
