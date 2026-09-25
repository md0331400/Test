package com.amisayem.kothabolbo.data.repository

import com.amisayem.kothabolbo.core.AppConstants
import com.amisayem.kothabolbo.domain.model.Presence
import com.amisayem.kothabolbo.domain.model.TypingState
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class PresenceRepository(private val database: FirebaseDatabase) {
    private var ownStatus: DatabaseReference? = null
    private var connectedRef: DatabaseReference? = null
    private var connectedListener: ValueEventListener? = null
    private var connectedUid: String? = null

    fun connect(uid: String) {
        if (connectedUid == uid && connectedListener != null) return
        disconnect()
        val status = database.getReference(AppConstants.STATUS).child(uid)
        ownStatus = status
        connectedUid = uid
        val connected = database.getReference(".info/connected")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.getValue(Boolean::class.java) != true) return
                status.onDisconnect().setValue(mapOf("online" to false, "lastSeen" to ServerValue.TIMESTAMP))
                status.setValue(mapOf("online" to true, "lastSeen" to ServerValue.TIMESTAMP))
            }
            override fun onCancelled(error: DatabaseError) = Unit
        }
        connectedRef = connected
        connectedListener = listener
        connected.addValueEventListener(listener)
    }

    fun setForeground(online: Boolean) {
        ownStatus?.setValue(mapOf(
            "online" to online,
            "lastSeen" to ServerValue.TIMESTAMP
        ))
    }

    fun disconnect() {
        ownStatus?.setValue(mapOf("online" to false, "lastSeen" to ServerValue.TIMESTAMP))
        connectedListener?.let { listener -> connectedRef?.removeEventListener(listener) }
        ownStatus = null
        connectedRef = null
        connectedListener = null
        connectedUid = null
    }

    fun onlineUsers(): Flow<Set<String>> = callbackFlow {
        val ref = database.getReference(AppConstants.STATUS)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot.children.mapNotNull { node ->
                    node.key?.takeIf {
                        node.child("online").getValue(Boolean::class.java) == true ||
                            node.child("state").getValue(String::class.java) == "online"
                    }
                }.toSet())
            }
            override fun onCancelled(error: DatabaseError) { close(error.toException()) }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    fun presence(uid: String): Flow<Presence> = callbackFlow {
        val ref = database.getReference(AppConstants.STATUS).child(uid)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val online = snapshot.child("online").getValue(Boolean::class.java)
                    ?: (snapshot.child("state").getValue(String::class.java) == "online")
                trySend(Presence(
                    state = if (online) "online" else "offline",
                    lastChanged = snapshot.child("lastSeen").getValue(Long::class.java)
                        ?: snapshot.child("lastChanged").getValue(Long::class.java) ?: 0L
                ))
            }
            override fun onCancelled(error: DatabaseError) { close(error.toException()) }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    fun setTyping(me: String, other: String, typing: Boolean) {
        database.getReference(AppConstants.TYPING).child("${me}_${other}").setValue(mapOf(
            "typing" to typing,
            "timestamp" to ServerValue.TIMESTAMP,
            "updatedAtLocal" to System.currentTimeMillis()
        ))
    }

    fun typing(other: String, me: String): Flow<TypingState> = callbackFlow {
        val ref = database.getReference(AppConstants.TYPING).child("${other}_${me}")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(TypingState(
                    typing = snapshot.child("typing").getValue(Boolean::class.java) ?: false,
                    updatedAt = snapshot.child("timestamp").getValue(Long::class.java)
                        ?: snapshot.child("updatedAtLocal").getValue(Long::class.java)
                        ?: snapshot.child("updatedAt").getValue(Long::class.java) ?: 0L
                ))
            }
            override fun onCancelled(error: DatabaseError) { close(error.toException()) }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }
}
