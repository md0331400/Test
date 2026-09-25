package com.amisayem.kothabolbo.data.repository

import com.amisayem.kothabolbo.core.AppConstants
import com.amisayem.kothabolbo.core.AppResult
import com.amisayem.kothabolbo.data.local.KothaDao
import com.amisayem.kothabolbo.data.local.ProfileCacheEntity
import com.amisayem.kothabolbo.data.remote.toUser
import com.amisayem.kothabolbo.domain.model.UserProfile
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.Locale

class UserRepository(
    private val firestore: FirebaseFirestore,
    private val dao: KothaDao
) {
    fun observe(uid: String): Flow<UserProfile?> = callbackFlow {
        val registration = firestore.collection(AppConstants.USERS).document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) close(error) else trySend(snapshot?.toUser())
            }
        awaitClose { registration.remove() }
    }.distinctUntilChanged().onEach { it?.let { profile -> dao.cacheProfile(ProfileCacheEntity.from(profile)) } }

    fun cached(uid: String): Flow<UserProfile?> = dao.observeProfileCache(uid).map { row -> row?.toDomain() }

    suspend fun get(uid: String): UserProfile? = firestore.collection(AppConstants.USERS).document(uid).get().await().toUser()

    suspend fun emailExists(email: String, exceptUid: String? = null): Boolean =
        firestore.collection(AppConstants.USERS)
            .whereEqualTo("email", email.trim().lowercase(Locale.ROOT)).limit(2).get().await()
            .documents.any { it.id != exceptUid }

    suspend fun phoneExists(phone: String, exceptUid: String? = null): Boolean =
        firestore.collection(AppConstants.USERS).whereEqualTo("phone", phone).limit(2).get().await()
            .documents.any { it.id != exceptUid }

    suspend fun search(query: String, currentUid: String): AppResult<List<UserProfile>> = runCatching {
        // Firestore lacks case-insensitive contains; directory is intentionally bounded and filtered locally.
        val normalized = query.trim().lowercase(Locale.ROOT)
        if (normalized.length < 2) return AppResult.Success(emptyList())
        firestore.collection(AppConstants.USERS).limit(250).get().await().documents
            .mapNotNull { it.toUser() }
            .filter { it.uid != currentUid && (
                it.displayName.lowercase(Locale.ROOT).contains(normalized) ||
                    it.email.lowercase(Locale.ROOT).contains(normalized) ||
                    it.phone.contains(normalized)
                ) }
            .take(50)
    }.fold({ AppResult.Success(it) }, { AppResult.Error(it.message ?: "Search failed", cause = it) })

    suspend fun updateProfile(
        uid: String,
        changes: Map<String, Any?>,
        cooldownFieldsChanged: Boolean
    ): AppResult<Unit> = runCatching {
        val ref = firestore.collection(AppConstants.USERS).document(uid)
        if (cooldownFieldsChanged) {
            firestore.runTransaction { tx ->
                val snapshot = tx.get(ref)
                val last = snapshot.getTimestamp("lastProfileEdit")?.toDate()?.time ?: 0L
                val remaining = AppConstants.PROFILE_EDIT_COOLDOWN_MS - (System.currentTimeMillis() - last)
                if (last > 0 && remaining > 0) {
                    val days = kotlin.math.ceil(remaining / 86_400_000.0).toInt()
                    throw IllegalStateException("You can edit these profile details again in $days day${if (days == 1) "" else "s"}.")
                }
                tx.update(ref, changes + ("lastProfileEdit" to FieldValue.serverTimestamp()))
            }.await()
        } else ref.update(changes).await()
        Unit
    }.fold({ AppResult.Success(Unit) }, { AppResult.Error(it.message ?: "Profile update failed", cause = it) })

    suspend fun updatePrivacy(uid: String, email: Boolean, phone: Boolean, day: Boolean, month: Boolean, year: Boolean): AppResult<Unit> =
        runCatching {
            firestore.collection(AppConstants.USERS).document(uid).update(mapOf(
                "emailPublic" to email, "phonePublic" to phone,
                "dobPublicDay" to day, "dobPublicMonth" to month, "dobPublicYear" to year
            )).await(); Unit
        }.fold({ AppResult.Success(Unit) }, { AppResult.Error(it.message ?: "Privacy update failed", cause = it) })

    suspend fun saveFcmToken(uid: String, token: String) {
        firestore.collection(AppConstants.USERS).document(uid).set(mapOf(
            "fcmToken" to token,
            "fcmTokens" to FieldValue.arrayUnion(token),
            "fcmTokenUpdatedAt" to FieldValue.serverTimestamp()
        ), SetOptions.merge()).await()
    }

    suspend fun removeFcmToken(uid: String, token: String?) {
        val changes = mutableMapOf<String, Any>("fcmToken" to "")
        if (!token.isNullOrBlank()) changes["fcmTokens"] = FieldValue.arrayRemove(token)
        firestore.collection(AppConstants.USERS).document(uid).update(changes).await()
    }
}
