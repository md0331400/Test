package com.amisayem.kothabolbo.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface KothaDao {
    @Query("SELECT * FROM restored_messages WHERE ownerUid=:uid ORDER BY timestampMs ASC")
    fun observeRestored(uid: String): Flow<List<RestoredMessageEntity>>

    @Query("SELECT * FROM restored_messages WHERE ownerUid=:uid ORDER BY timestampMs ASC")
    suspend fun restoredOnce(uid: String): List<RestoredMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreMessages(messages: List<RestoredMessageEntity>)

    @Query("DELETE FROM restored_messages WHERE ownerUid=:uid")
    suspend fun clearRestored(uid: String)

    @Query("DELETE FROM restored_messages WHERE ownerUid=:uid AND messageId=:messageId")
    suspend fun deleteRestored(uid: String, messageId: String)

    @Query("DELETE FROM restored_messages WHERE ownerUid=:uid AND ((senderId=:uid AND receiverId=:otherUid) OR (senderId=:otherUid AND receiverId=:uid))")
    suspend fun clearRestoredConversation(uid: String, otherUid: String)

    @Upsert
    suspend fun queueMedia(item: MediaDeleteEntity)

    @Query("SELECT * FROM media_delete_queue WHERE expiresAt <= :now ORDER BY expiresAt ASC LIMIT 50")
    suspend fun expiredMedia(now: Long): List<MediaDeleteEntity>

    @Query("UPDATE media_delete_queue SET attempts=attempts+1 WHERE fileId=:fileId")
    suspend fun bumpMediaAttempt(fileId: String)

    @Query("DELETE FROM media_delete_queue WHERE fileId=:fileId")
    suspend fun removeMedia(fileId: String)

    @Query("SELECT * FROM chat_cache WHERE ownerUid=:uid ORDER BY lastTimestamp DESC")
    fun observeChatCache(uid: String): Flow<List<ChatCacheEntity>>

    @Upsert
    suspend fun cacheChats(chats: List<ChatCacheEntity>)

    @Query("DELETE FROM chat_cache WHERE ownerUid=:uid AND partnerUid=:partnerUid")
    suspend fun removeChatCache(uid: String, partnerUid: String)

    @Query("SELECT * FROM profile_cache WHERE uid=:uid LIMIT 1")
    fun observeProfileCache(uid: String): Flow<ProfileCacheEntity?>

    @Upsert
    suspend fun cacheProfile(profile: ProfileCacheEntity)

    @Query("SELECT * FROM drafts WHERE ownerUid=:uid AND partnerUid=:partnerUid LIMIT 1")
    fun observeDraft(uid: String, partnerUid: String): Flow<DraftEntity?>

    @Upsert
    suspend fun saveDraft(draft: DraftEntity)

    @Query("DELETE FROM drafts WHERE ownerUid=:uid AND partnerUid=:partnerUid")
    suspend fun clearDraft(uid: String, partnerUid: String)

    @Query("DELETE FROM chat_cache WHERE ownerUid=:uid")
    suspend fun clearChatCache(uid: String)

    @Query("DELETE FROM drafts WHERE ownerUid=:uid")
    suspend fun clearDrafts(uid: String)
}
