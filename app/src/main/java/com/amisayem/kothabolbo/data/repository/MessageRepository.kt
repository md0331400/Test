package com.amisayem.kothabolbo.data.repository

import com.amisayem.kothabolbo.core.AppConstants
import com.amisayem.kothabolbo.core.AppResult
import com.amisayem.kothabolbo.core.TimeUtils
import com.amisayem.kothabolbo.data.local.DraftEntity
import com.amisayem.kothabolbo.data.local.KothaDao
import com.amisayem.kothabolbo.data.local.MediaDeleteEntity
import com.amisayem.kothabolbo.data.remote.NotificationPayload
import com.amisayem.kothabolbo.data.remote.VercelApi
import com.amisayem.kothabolbo.data.remote.toContact
import com.amisayem.kothabolbo.data.remote.toMessage
import com.amisayem.kothabolbo.domain.model.ChatContact
import com.amisayem.kothabolbo.domain.model.ChatMessage
import com.amisayem.kothabolbo.domain.model.ReplyReference
import com.amisayem.kothabolbo.domain.model.UserProfile
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class MessageRepository(
    private val firestore: FirebaseFirestore,
    private val authRepository: AuthRepository,
    private val dao: KothaDao,
    private val api: VercelApi
) {
    private fun messageQueryFlow(query: Query): Flow<List<ChatMessage>> = callbackFlow {
        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) close(error)
            else trySend(snapshot?.documents?.mapNotNull { it.toMessage() }.orEmpty())
        }
        awaitClose { registration.remove() }
    }

    fun liveMessages(uid: String): Flow<List<ChatMessage>> {
        val sent = messageQueryFlow(firestore.collection(AppConstants.MESSAGES).whereEqualTo("senderId", uid))
        val received = messageQueryFlow(firestore.collection(AppConstants.MESSAGES).whereEqualTo("receiverId", uid))
        return combine(sent, received) { a, b ->
            (a + b).distinctBy { it.id }
                .filter { TimeUtils.isLive(it.sortTime) && it.visibleTo(uid) }
                .sortedBy { it.sortTime }
        }.distinctUntilChanged()
    }

    fun conversation(uid: String, otherUid: String, includeArchive: Boolean = true): Flow<List<ChatMessage>> {
        val live = liveMessages(uid)
        val archive = dao.observeRestored(uid)
        return combine(live, archive) { cloud, restored ->
            val archived = if (includeArchive) restored.map { it.toDomain() } else emptyList()
            (cloud + archived)
                .filter { (it.senderId == uid && it.receiverId == otherUid) || (it.senderId == otherUid && it.receiverId == uid) }
                .filter { it.visibleTo(uid) }
                .distinctBy { it.id }
                .sortedBy { it.sortTime }
        }
    }

    fun contacts(uid: String): Flow<List<ChatContact>> = callbackFlow {
        val registration = firestore.collection(AppConstants.CONTACTS).document(uid).collection("contacts")
            .addSnapshotListener { snapshot, error ->
                if (error != null) close(error)
                else trySend(snapshot?.documents?.mapNotNull { it.toContact() }.orEmpty())
            }
        awaitClose { registration.remove() }
    }

    suspend fun send(
        sender: UserProfile,
        receiver: UserProfile,
        text: String = "",
        type: String = "text",
        imageUrl: String = "",
        imageFileId: String = "",
        voiceUrl: String = "",
        voiceFileId: String = "",
        voiceDuration: Long = 0,
        replyTo: ReplyReference? = null
    ): AppResult<String> = runCatching {
        require(sender.uid != receiver.uid) { "You cannot message yourself." }
        require(type != "text" || text.trim().isNotBlank()) { "Message cannot be empty." }
        val ref = firestore.collection(AppConstants.MESSAGES).document()
        val now = System.currentTimeMillis()
        val data = mutableMapOf<String, Any?>(
            "senderId" to sender.uid,
            "receiverId" to receiver.uid,
            "text" to text.trim(),
            "type" to type,
            "imageUrl" to imageUrl,
            "imageFileId" to imageFileId,
            "voiceUrl" to voiceUrl,
            "voiceFileId" to voiceFileId,
            "voiceDuration" to voiceDuration,
            "timestamp" to FieldValue.serverTimestamp(),
            "clientCreatedAt" to now,
            "delivered" to false,
            "read" to false,
            "seen" to false,
            "edited" to false,
            "deletedFor" to emptyList<String>(),
            "deletedBySender" to false,
            "deletedByReceiver" to false
        )
        replyTo?.let { data["replyTo"] = mapOf("id" to it.id, "text" to it.text, "senderName" to it.senderName) }
        ref.set(data).await()
        // The message write is the source of truth. Contact/cache/media bookkeeping is
        // idempotent best-effort so a later side-effect failure can never report “send failed”
        // after the document exists (which would make notification-action retry duplicate it).
        runCatching { addContact(sender.uid, receiver) }
        runCatching { addContact(receiver.uid, sender) }
        runCatching {
            if (imageFileId.isNotBlank()) dao.queueMedia(MediaDeleteEntity(imageFileId, sender.uid, now + AppConstants.AUTO_DELETE_MS, "chat_image"))
            if (voiceFileId.isNotBlank()) dao.queueMedia(MediaDeleteEntity(voiceFileId, sender.uid, now + AppConstants.AUTO_DELETE_MS, "voice"))
        }

        // Push failure must never roll back (or retry) an already committed message.
        runCatching {
            val token = authRepository.idToken() ?: return@runCatching
            val preview = when (type) {
                "image" -> "📷 Photo"
                "voice" -> "🎤 Voice message"
                else -> text.trim()
            }
            api.sendNotification(token, NotificationPayload(
                receiverId = receiver.uid,
                title = sender.displayName,
                body = preview,
                icon = sender.avatar,
                image = if (type == "image") imageUrl else "",
                url = "/?chat=${sender.uid}",
                messageType = type,
                messageId = ref.id
            ))
        }
        ref.id
    }.fold({ AppResult.Success(it) }, { AppResult.Error(it.message ?: "Message could not be sent", cause = it) })

    private suspend fun addContact(ownerUid: String, contact: UserProfile) {
        firestore.collection(AppConstants.CONTACTS).document(ownerUid).collection("contacts")
            .document(contact.uid).set(mapOf(
                "uid" to contact.uid,
                "displayName" to contact.displayName,
                "photoURL" to contact.avatar,
                "addedAt" to FieldValue.serverTimestamp(),
                "removed" to false,
                "removedAt" to null
            )).await()
    }

    suspend fun markDelivered(uid: String, messages: List<ChatMessage>) = batchUpdate(
        messages.filter { it.receiverId == uid && !it.delivered },
        mapOf("delivered" to true)
    )

    suspend fun markSeen(uid: String, otherUid: String, messages: List<ChatMessage>) = batchUpdate(
        messages.filter { it.receiverId == uid && it.senderId == otherUid && (!it.read || !it.seen) },
        mapOf("delivered" to true, "read" to true, "seen" to true)
    )

    private suspend fun batchUpdate(messages: List<ChatMessage>, changes: Map<String, Any>) {
        messages.chunked(450).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { batch.update(firestore.collection(AppConstants.MESSAGES).document(it.id), changes) }
            if (chunk.isNotEmpty()) batch.commit().await()
        }
    }

    suspend fun edit(uid: String, message: ChatMessage, newText: String): AppResult<Unit> = runCatching {
        require(message.senderId == uid) { "Only your own message can be edited." }
        require(TimeUtils.isLive(message.sortTime)) { "This message has expired." }
        require(message.type == "text") { "Only text messages can be edited." }
        require(newText.trim().isNotBlank()) { "Message cannot be empty." }
        firestore.collection(AppConstants.MESSAGES).document(message.id)
            .update(mapOf("text" to newText.trim(), "edited" to true)).await(); Unit
    }.fold({ AppResult.Success(Unit) }, { AppResult.Error(it.message ?: "Edit failed", cause = it) })

    suspend fun deleteForMe(uid: String, message: ChatMessage): AppResult<Unit> = runCatching {
        val ref = firestore.collection(AppConstants.MESSAGES).document(message.id)
        if (!ref.get().await().exists()) {
            // Drive restore rows are a local archive and must never be recreated in Firestore.
            dao.deleteRestored(uid, message.id)
            return@runCatching Unit
        }
        val field = if (message.senderId == uid) "deletedBySender" else "deletedByReceiver"
        // Both the role-specific flag and deletedFor are set. Every live/archive/cache filter honors both,
        // closing the old app's resurrection path after listener refresh/restart.
        ref.update(mapOf(
            field to true,
            "deletedFor" to FieldValue.arrayUnion(uid)
        )).await(); Unit
    }.fold({ AppResult.Success(Unit) }, { AppResult.Error(it.message ?: "Delete failed", cause = it) })

    suspend fun deleteForEveryone(uid: String, message: ChatMessage): AppResult<Unit> = runCatching {
        require(message.senderId == uid) { "Only your own message can be deleted for everyone." }
        val ref = firestore.collection(AppConstants.MESSAGES).document(message.id)
        if (ref.get().await().exists()) {
            ref.delete().await()
            queueMessageMedia(uid, message, System.currentTimeMillis())
        } else {
            // An archive has no server counterpart; remove only this device-local copy.
            dao.deleteRestored(uid, message.id)
        }
        Unit
    }.fold({ AppResult.Success(Unit) }, { AppResult.Error(it.message ?: "Delete failed", cause = it) })

    suspend fun clearConversation(uid: String, otherUid: String): AppResult<Unit> = runCatching {
        val snapshot = currentConversationDocuments(uid, otherUid)
        snapshot.chunked(400).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { message ->
                val ref = firestore.collection(AppConstants.MESSAGES).document(message.id)
                if (otherUid in message.deletedFor) batch.delete(ref)
                else batch.update(ref, "deletedFor", FieldValue.arrayUnion(uid))
            }
            if (chunk.isNotEmpty()) batch.commit().await()
        }
        dao.clearRestoredConversation(uid, otherUid)
        removeFromList(uid, otherUid)
        Unit
    }.fold({ AppResult.Success(Unit) }, { AppResult.Error(it.message ?: "Conversation could not be cleared", cause = it) })

    suspend fun removeFromList(uid: String, otherUid: String): AppResult<Unit> = runCatching {
        firestore.collection(AppConstants.CONTACTS).document(uid).collection("contacts").document(otherUid)
            .set(mapOf("uid" to otherUid, "removed" to true, "removedAt" to FieldValue.serverTimestamp()), com.google.firebase.firestore.SetOptions.merge()).await()
        dao.removeChatCache(uid, otherUid); Unit
    }.fold({ AppResult.Success(Unit) }, { AppResult.Error(it.message ?: "Chat could not be removed", cause = it) })

    suspend fun reopen(uid: String, other: UserProfile) {
        addContact(uid, other)
        // Preserve documented legacy behavior: reopening a removed/cleared chat removes this uid
        // from deletedFor on still-retained documents, but cannot resurrect Delete-for-Me because
        // its role-specific flag remains true and all filters check it.
        currentConversationDocuments(uid, other.uid).chunked(400).forEach { chunk ->
            val batch = firestore.batch()
            chunk.filter { uid in it.deletedFor }.forEach {
                batch.update(firestore.collection(AppConstants.MESSAGES).document(it.id), "deletedFor", FieldValue.arrayRemove(uid))
            }
            if (chunk.any { uid in it.deletedFor }) batch.commit().await()
        }
    }

    private suspend fun currentConversationDocuments(uid: String, otherUid: String): List<ChatMessage> {
        val sent = firestore.collection(AppConstants.MESSAGES).whereEqualTo("senderId", uid).get().await().documents.mapNotNull { it.toMessage() }
        val received = firestore.collection(AppConstants.MESSAGES).whereEqualTo("receiverId", uid).get().await().documents.mapNotNull { it.toMessage() }
        return (sent + received).distinctBy { it.id }.filter {
            (it.senderId == uid && it.receiverId == otherUid) || (it.senderId == otherUid && it.receiverId == uid)
        }
    }

    suspend fun messagesForBackup(uid: String): List<ChatMessage> {
        val sent = firestore.collection(AppConstants.MESSAGES).whereEqualTo("senderId", uid).get().await()
            .documents.mapNotNull { it.toMessage() }
        val received = firestore.collection(AppConstants.MESSAGES).whereEqualTo("receiverId", uid).get().await()
            .documents.mapNotNull { it.toMessage() }
        val archive = dao.restoredOnce(uid).map { it.toDomain() }
        return (sent + received + archive).distinctBy { it.id }.filter { it.visibleTo(uid) }.sortedBy { it.sortTime }
    }

    suspend fun cleanupExpired(uid: String): Int {
        val old = runCatching {
            val sent = firestore.collection(AppConstants.MESSAGES).whereEqualTo("senderId", uid).get().await().documents.mapNotNull { it.toMessage() }
            val received = firestore.collection(AppConstants.MESSAGES).whereEqualTo("receiverId", uid).get().await().documents.mapNotNull { it.toMessage() }
            (sent + received).distinctBy { it.id }.filter { !TimeUtils.isLive(it.sortTime) }
        }.getOrDefault(emptyList())
        old.chunked(400).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { batch.delete(firestore.collection(AppConstants.MESSAGES).document(it.id)) }
            if (chunk.isNotEmpty()) runCatching { batch.commit().await() }
            chunk.forEach { queueMessageMedia(uid, it, System.currentTimeMillis()) }
        }
        return old.size
    }

    private suspend fun queueMessageMedia(uid: String, m: ChatMessage, expiresAt: Long) {
        if (m.imageFileId.isNotBlank()) dao.queueMedia(MediaDeleteEntity(m.imageFileId, uid, expiresAt, "chat_image"))
        if (m.voiceFileId.isNotBlank()) dao.queueMedia(MediaDeleteEntity(m.voiceFileId, uid, expiresAt, "voice"))
    }

    fun draft(uid: String, otherUid: String): Flow<String> = dao.observeDraft(uid, otherUid).map { row -> row?.text.orEmpty() }
    suspend fun saveDraft(uid: String, otherUid: String, text: String) {
        if (text.isBlank()) dao.clearDraft(uid, otherUid)
        else dao.saveDraft(DraftEntity(uid, otherUid, text, System.currentTimeMillis()))
    }
    suspend fun clearDraft(uid: String, otherUid: String) = dao.clearDraft(uid, otherUid)
}
