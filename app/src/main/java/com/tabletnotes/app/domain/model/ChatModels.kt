package com.tabletnotes.app.domain.model

enum class ChatMessageType {
    TEXT,
    NOTE_ATTACHMENT
}

enum class RelayTransferState {
    NONE,
    QUEUED_OFFLINE,
    ENCRYPTING_AND_UPLOADING,
    UPLOAD_INTERRUPTED,
    WAITING_IN_RELAY,
    DOWNLOADING_AND_DECRYPTING,
    DOWNLOADED_AND_DELETED_FROM_RELAY,
    EXPIRED_AUTO_DELETED,
    FAILED_TOO_LARGE,
    FAILED_ERROR
}

data class UserProfile(
    val uid: String,
    val displayName: String,
    val emailOrPhone: String,
    val publicKeyBase64: String,
    val keyFingerprint: String
)

data class ChatContact(
    val id: String,
    val displayName: String,
    val handleOrEmail: String,
    val avatarInitials: String,
    val publicKeyBase64: String,
    val lastMessagePreview: String,
    val lastMessageTimeMillis: Long,
    val unreadCount: Int = 0,
    val isOnline: Boolean = true
)

data class NoteAttachmentMeta(
    val noteId: String,
    val noteTitle: String,
    val pageCount: Int,
    val strokeCount: Int,
    val fileSizeBytes: Long,
    val thumbnailPath: String?,
    val relayObjectId: String? = null,
    val encryptedKeyEnvelopeBase64: String? = null,
    val sha256Hex: String? = null,
    val expiresAtMillis: Long? = null,
    val uploadProgressPercent: Int = 100
)

data class ChatMessage(
    val id: String,
    val contactId: String,
    val senderId: String,
    val recipientId: String,
    val isOutgoing: Boolean,
    val type: ChatMessageType,
    val textContent: String,
    val timestampMillis: Long,
    val relayState: RelayTransferState = RelayTransferState.NONE,
    val noteAttachment: NoteAttachmentMeta? = null
)
