package com.amisayem.kothabolbo.data.repository

import com.amisayem.kothabolbo.core.AppConstants
import com.amisayem.kothabolbo.core.AppResult
import com.amisayem.kothabolbo.data.local.KothaDao
import com.amisayem.kothabolbo.data.local.MediaDeleteEntity
import com.amisayem.kothabolbo.data.remote.toPost
import com.amisayem.kothabolbo.domain.model.MediaUpload
import com.amisayem.kothabolbo.domain.model.Post
import com.amisayem.kothabolbo.domain.model.UserProfile
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FeedRepository(
    private val firestore: FirebaseFirestore,
    private val mediaRepository: MediaRepository,
    private val dao: KothaDao
) {
    fun observe(uid: String): Flow<List<Post>> = callbackFlow {
        val registration = firestore.collection(AppConstants.POSTS)
            .addSnapshotListener { snapshot, error ->
                if (error != null) close(error) else {
                    val visible = snapshot?.documents?.mapNotNull { it.toPost() }.orEmpty()
                        .filter { it.privacy == "public" || it.authorId == uid }
                        .sortedByDescending { it.createdAtMs }
                    trySend(visible)
                }
            }
        awaitClose { registration.remove() }
    }

    suspend fun create(author: UserProfile, text: String, media: MediaUpload?, privacy: String): AppResult<String> = runCatching {
        require(text.length <= AppConstants.MAX_TEXT_LENGTH) { "Post text cannot exceed 5,000 characters." }
        require(text.isNotBlank() || media != null) { "Add text or an image to your post." }
        val ref = firestore.collection(AppConstants.POSTS).document()
        ref.set(mapOf(
            "authorId" to author.uid,
            "authorName" to author.displayName,
            "authorPhoto" to author.avatar,
            "text" to text.trim(),
            "imageUrl" to (media?.url ?: ""),
            "imageFileId" to (media?.fileId ?: ""),
            "createdAt" to FieldValue.serverTimestamp(),
            "privacy" to privacy,
            "likes" to emptyList<String>(),
            "likeCount" to 0
        )).await()
        ref.id
    }.fold({ AppResult.Success(it) }, { AppResult.Error(it.message ?: "Post could not be published", cause = it) })

    suspend fun toggleLike(post: Post, uid: String): AppResult<Unit> = runCatching {
        val ref = firestore.collection(AppConstants.POSTS).document(post.id)
        firestore.runTransaction { tx ->
            val snap = tx.get(ref)
            val likes = (snap.get("likes") as? List<*>)?.filterIsInstance<String>().orEmpty().toMutableSet()
            if (!likes.add(uid)) likes.remove(uid)
            tx.update(ref, mapOf("likes" to likes.toList(), "likeCount" to likes.size.coerceAtLeast(0)))
        }.await(); Unit
    }.fold({ AppResult.Success(Unit) }, { AppResult.Error(it.message ?: "Like could not be updated", cause = it) })

    suspend fun delete(post: Post, uid: String): AppResult<Unit> = runCatching {
        require(post.authorId == uid) { "You can only delete your own posts." }
        firestore.collection(AppConstants.POSTS).document(post.id).delete().await()
        if (post.imageFileId.isNotBlank()) {
            if (!mediaRepository.deleteNow(post.imageFileId)) dao.queueMedia(MediaDeleteEntity(post.imageFileId, uid, System.currentTimeMillis(), "feed"))
        }
        Unit
    }.fold({ AppResult.Success(Unit) }, { AppResult.Error(it.message ?: "Post could not be deleted", cause = it) })
}
