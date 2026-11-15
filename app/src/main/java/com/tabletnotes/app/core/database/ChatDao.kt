package com.tabletnotes.app.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_contacts ORDER BY lastMessageTimeMillis DESC")
    fun observeContacts(): Flow<List<ChatContactEntity>>

    @Query("SELECT * FROM chat_contacts WHERE id = :contactId LIMIT 1")
    suspend fun getContactById(contactId: String): ChatContactEntity?

    @Query("SELECT COUNT(*) FROM chat_contacts")
    suspend fun getContactCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertContact(contact: ChatContactEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertContacts(contacts: List<ChatContactEntity>)

    @Query("SELECT * FROM chat_messages WHERE contactId = :contactId ORDER BY timestampMillis ASC")
    fun observeMessagesForContact(contactId: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE id = :messageId LIMIT 1")
    suspend fun getMessageById(messageId: String): ChatMessageEntity?

    @Query("SELECT * FROM chat_messages WHERE relayState IN ('QUEUED_OFFLINE', 'UPLOAD_INTERRUPTED') ORDER BY timestampMillis ASC")
    suspend fun getPendingOutboxMessages(): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessage(message: ChatMessageEntity)

    @Query("UPDATE chat_messages SET relayState = :newState, uploadProgressPercent = :progress WHERE id = :messageId")
    suspend fun updateMessageRelayState(messageId: String, newState: String, progress: Int = 100)

    @Query("UPDATE chat_contacts SET lastMessagePreview = :preview, lastMessageTimeMillis = :timestampMillis, unreadCount = 0 WHERE id = :contactId")
    suspend fun updateContactLastMessage(contactId: String, preview: String, timestampMillis: Long)

    @Query("UPDATE chat_contacts SET unreadCount = 0 WHERE id = :contactId")
    suspend fun clearUnreadCount(contactId: String)
}

