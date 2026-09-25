package com.amisayem.kothabolbo.data.repository

import com.amisayem.kothabolbo.core.AppConstants
import com.amisayem.kothabolbo.core.AppResult
import com.amisayem.kothabolbo.data.local.KothaDao
import com.amisayem.kothabolbo.data.remote.toMessage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class AccountRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val realtime: FirebaseDatabase,
    private val userRepository: UserRepository,
    private val mediaRepository: MediaRepository,
    private val dao: KothaDao
) {
    suspend fun deleteAccount(typedPhrase: String): AppResult<Unit> {
        if (typedPhrase != AppConstants.DELETE_ACCOUNT_PHRASE) {
            return AppResult.Error("Type the confirmation phrase exactly as shown.")
        }
        val user = auth.currentUser ?: return AppResult.Error("Please sign in again.")
        val uid = user.uid
        return try {
            // Auth deletion requires a recent sign-in. Refreshing a token alone does not make an
            // old authentication recent, so inspect the signed token's auth_time before touching
            // any application data. Firebase uses the same claim for sensitive-operation checks.
            val token = user.getIdToken(true).await()
            val authTimeSeconds = (token.claims["auth_time"] as? Number)?.toLong() ?: 0L
            if (authTimeSeconds == 0L || System.currentTimeMillis() / 1_000L - authTimeSeconds > 5 * 60L) {
                return AppResult.Error(
                    "For security, sign out, sign in again, then retry account deletion.",
                    "requires-recent-login"
                )
            }
            val profile = userRepository.get(uid)
            val sent = firestore.collection(AppConstants.MESSAGES).whereEqualTo("senderId", uid).get().await().documents
            val received = firestore.collection(AppConstants.MESSAGES).whereEqualTo("receiverId", uid).get().await().documents
            val messageDocs = (sent + received).distinctBy { it.id }
            messageDocs.chunked(400).forEach { chunk ->
                val batch = firestore.batch()
                chunk.forEach { batch.delete(it.reference) }
                if (chunk.isNotEmpty()) batch.commit().await()
            }
            // Remove own contact book and every reverse reference to this uid.
            val ownContacts = firestore.collection(AppConstants.CONTACTS).document(uid).collection("contacts").get().await().documents
            ownContacts.chunked(400).forEach { chunk ->
                val batch = firestore.batch(); chunk.forEach { batch.delete(it.reference) }; if (chunk.isNotEmpty()) batch.commit().await()
            }
            runCatching {
                firestore.collectionGroup("contacts").whereEqualTo("uid", uid).get().await().documents.chunked(400).forEach { chunk ->
                    val batch = firestore.batch(); chunk.forEach { batch.delete(it.reference) }; if (chunk.isNotEmpty()) batch.commit().await()
                }
            }
            runCatching { firestore.collection(AppConstants.CONTACTS).document(uid).delete().await() }

            // Best effort ImageKit cleanup; server credentials remain server-side.
            profile?.photoFileId?.takeIf(String::isNotBlank)?.let { runCatching { mediaRepository.deleteNow(it) } }
            profile?.coverFileId?.takeIf(String::isNotBlank)?.let { runCatching { mediaRepository.deleteNow(it) } }
            messageDocs.mapNotNull { it.toMessage() }.flatMap { listOf(it.imageFileId, it.voiceFileId) }
                .filter(String::isNotBlank).distinct().forEach { runCatching { mediaRepository.deleteNow(it) } }

            firestore.collection(AppConstants.USERS).document(uid).delete().await()
            realtime.getReference(AppConstants.STATUS).child(uid).removeValue().await()
            runCatching {
                val typingRoot = realtime.getReference(AppConstants.TYPING)
                val mine = typingRoot.get().await().children.mapNotNull { node ->
                    node.key?.takeIf { key -> key.startsWith("${uid}_") || key.endsWith("_$uid") }
                }
                if (mine.isNotEmpty()) typingRoot.updateChildren(mine.associateWith { null }).await()
            }
            dao.clearChatCache(uid); dao.clearDrafts(uid); dao.clearRestored(uid)
            user.delete().await()
            AppResult.Success(Unit)
        } catch (e: FirebaseAuthRecentLoginRequiredException) {
            AppResult.Error("For security, sign out, sign in again, then retry account deletion.", "requires-recent-login", e)
        } catch (t: Throwable) {
            AppResult.Error(t.message ?: "Account deletion could not be completed.", cause = t)
        }
    }
}
