package com.amisayem.kothabolbo.data.repository

import com.amisayem.kothabolbo.core.AppConstants
import com.amisayem.kothabolbo.core.AppResult
import com.amisayem.kothabolbo.data.local.KothaDao
import com.amisayem.kothabolbo.data.local.MediaDeleteEntity
import com.amisayem.kothabolbo.data.remote.toStory
import com.amisayem.kothabolbo.domain.model.MediaUpload
import com.amisayem.kothabolbo.domain.model.Story
import com.amisayem.kothabolbo.domain.model.UserProfile
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Date

class StoryRepository(
    private val firestore: FirebaseFirestore,
    private val mediaRepository: MediaRepository,
    private val dao: KothaDao
) {
    fun observe(uid: String): Flow<List<Story>> = callbackFlow {
        val registration = firestore.collection(AppConstants.STORIES).addSnapshotListener { snapshot, error ->
            if (error != null) close(error) else {
                val now = System.currentTimeMillis()
                trySend(snapshot?.documents?.mapNotNull { it.toStory() }.orEmpty()
                    .filter { it.expiresAtMs > now && (it.privacy == "public" || it.authorId == uid) }
                    .sortedWith(compareBy<Story> { it.authorId == uid }.thenByDescending { it.createdAtMs }))
            }
        }
        awaitClose { registration.remove() }
    }

    suspend fun create(author: UserProfile, type: String, text: String, caption: String, media: MediaUpload?, privacy: String): AppResult<String> = runCatching {
        require(text.length <= AppConstants.MAX_TEXT_LENGTH && caption.length <= AppConstants.MAX_TEXT_LENGTH) { "Story text cannot exceed 5,000 characters." }
        require(type == "text" || media != null) { "Select media for this story." }
        val now = System.currentTimeMillis()
        val ref = firestore.collection(AppConstants.STORIES).document()
        ref.set(mapOf(
            "authorId" to author.uid,
            "authorName" to author.displayName,
            "authorPhoto" to author.avatar,
            "type" to type,
            "mediaType" to type,
            "text" to text.trim(),
            "caption" to caption.trim(),
            "mediaUrl" to (media?.url ?: ""),
            "imageUrl" to (if (type == "image") media?.url.orEmpty() else ""),
            "mediaFileId" to (media?.fileId ?: ""),
            "fileId" to (media?.fileId ?: ""),
            "createdAt" to FieldValue.serverTimestamp(),
            "expiresAt" to Timestamp(Date(now + AppConstants.STORY_LIFETIME_MS)),
            "privacy" to privacy
        )).await()
        media?.fileId?.takeIf { it.isNotBlank() }?.let {
            dao.queueMedia(MediaDeleteEntity(it, author.uid, now + AppConstants.STORY_LIFETIME_MS, "story"))
        }
        ref.id
    }.fold({ AppResult.Success(it) }, { AppResult.Error(it.message ?: "Story could not be published", cause = it) })

    suspend fun cleanupExpired(uid: String): Int {
        val now = System.currentTimeMillis()
        val expired = runCatching { firestore.collection(AppConstants.STORIES).get().await().documents.mapNotNull { it.toStory() }.filter { it.expiresAtMs <= now } }.getOrDefault(emptyList())
        expired.chunked(400).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { batch.delete(firestore.collection(AppConstants.STORIES).document(it.id)) }
            if (chunk.isNotEmpty()) runCatching { batch.commit().await() }
            chunk.filter { it.mediaFileId.isNotBlank() }.forEach { dao.queueMedia(MediaDeleteEntity(it.mediaFileId, uid, now, "story")) }
        }
        return expired.size
    }

    suspend fun delete(story: Story, uid: String): AppResult<Unit> = runCatching {
        require(story.authorId == uid) { "You can only delete your own story." }
        firestore.collection(AppConstants.STORIES).document(story.id).delete().await()
        if (story.mediaFileId.isNotBlank() && !mediaRepository.deleteNow(story.mediaFileId)) {
            dao.queueMedia(MediaDeleteEntity(story.mediaFileId, uid, System.currentTimeMillis(), "story"))
        }
        Unit
    }.fold({ AppResult.Success(Unit) }, { AppResult.Error(it.message ?: "Story could not be deleted", cause = it) })
}
