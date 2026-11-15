package com.tabletnotes.app.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.tabletnotes.app.domain.model.ChatContact

@Entity(tableName = "chat_contacts")
data class ChatContactEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val handleOrEmail: String,
    val avatarInitials: String,
    val publicKeyBase64: String,
    val lastMessagePreview: String,
    val lastMessageTimeMillis: Long,
    val unreadCount: Int,
    val isOnline: Boolean,
    val isGroup: Boolean = false
) {
    fun toDomainModel(): ChatContact = ChatContact(
        id = id,
        displayName = displayName,
        handleOrEmail = handleOrEmail,
        avatarInitials = avatarInitials,
        publicKeyBase64 = publicKeyBase64,
        lastMessagePreview = lastMessagePreview,
        lastMessageTimeMillis = lastMessageTimeMillis,
        unreadCount = unreadCount,
        isOnline = isOnline,
        isGroup = isGroup
    )
}
