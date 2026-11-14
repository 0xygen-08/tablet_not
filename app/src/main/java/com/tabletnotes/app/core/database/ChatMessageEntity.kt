package com.tabletnotes.app.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tabletnotes.app.domain.model.ChatMessage
import com.tabletnotes.app.domain.model.ChatMessageType
import com.tabletnotes.app.domain.model.NoteAttachmentMeta
import com.tabletnotes.app.domain.model.RelayTransferState

@Entity(
    tableName = "chat_messages",
    indices = [Index(value = ["contactId", "timestampMillis"])]
)
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val contactId: String,
    val senderId: String,
    val recipientId: String,
    val isOutgoing: Boolean,
    val type: String,
    val textContent: String,
    val timestampMillis: Long,
    val relayState: String,
    val noteId: String?,
    val noteTitle: String?,
    val notePageCount: Int?,
    val noteStrokeCount: Int?,
    val noteFileSizeBytes: Long?,
    val noteThumbnailPath: String?,
    val relayObjectId: String?,
    val encryptedKeyEnvelopeBase64: String?,
    val sha256Hex: String?,
    val expiresAtMillis: Long?,
    val uploadProgressPercent: Int = 100
) {
    fun toDomainModel(): ChatMessage {
        val msgType = runCatching { ChatMessageType.valueOf(type) }.getOrDefault(ChatMessageType.TEXT)
        val transferState = runCatching { RelayTransferState.valueOf(relayState) }.getOrDefault(RelayTransferState.NONE)
        val attachment = if (msgType == ChatMessageType.NOTE_ATTACHMENT && noteId != null) {
            NoteAttachmentMeta(
                noteId = noteId,
                noteTitle = noteTitle ?: "",
                pageCount = notePageCount ?: 1,
                strokeCount = noteStrokeCount ?: 0,
                fileSizeBytes = noteFileSizeBytes ?: 0L,
                thumbnailPath = noteThumbnailPath,
                relayObjectId = relayObjectId,
                encryptedKeyEnvelopeBase64 = encryptedKeyEnvelopeBase64,
                sha256Hex = sha256Hex,
                expiresAtMillis = expiresAtMillis,
                uploadProgressPercent = uploadProgressPercent
            )
        } else {
            null
        }
        return ChatMessage(
            id = id,
            contactId = contactId,
            senderId = senderId,
            recipientId = recipientId,
            isOutgoing = isOutgoing,
            type = msgType,
            textContent = textContent,
            timestampMillis = timestampMillis,
            relayState = transferState,
            noteAttachment = attachment
        )
    }

    companion object {
        fun fromDomainModel(msg: ChatMessage): ChatMessageEntity {
            val att = msg.noteAttachment
            return ChatMessageEntity(
                id = msg.id,
                contactId = msg.contactId,
                senderId = msg.senderId,
                recipientId = msg.recipientId,
                isOutgoing = msg.isOutgoing,
                type = msg.type.name,
                textContent = msg.textContent,
                timestampMillis = msg.timestampMillis,
                relayState = msg.relayState.name,
                noteId = att?.noteId,
                noteTitle = att?.noteTitle,
                notePageCount = att?.pageCount,
                noteStrokeCount = att?.strokeCount,
                noteFileSizeBytes = att?.fileSizeBytes,
                noteThumbnailPath = att?.thumbnailPath,
                relayObjectId = att?.relayObjectId,
                encryptedKeyEnvelopeBase64 = att?.encryptedKeyEnvelopeBase64,
                sha256Hex = att?.sha256Hex,
                expiresAtMillis = att?.expiresAtMillis,
                uploadProgressPercent = att?.uploadProgressPercent ?: 100
            )
        }
    }
}
