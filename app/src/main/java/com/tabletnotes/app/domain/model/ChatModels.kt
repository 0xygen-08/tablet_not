package com.tabletnotes.app.domain.model

import java.util.Locale

enum class AvatarPreset(
    val id: String,
    val colorArgb: Int,
    val iconKey: String? = null
) {
    COBALT_INITIALS("cobalt_circle", 0xFF2B63D9.toInt()),
    EMERALD_INITIALS("emerald_circle", 0xFF16794B.toInt()),
    PURPLE_INITIALS("purple_circle", 0xFF6B38E8.toInt()),
    CORAL_INITIALS("coral_circle", 0xFFDC4C46.toInt()),
    AMBER_INITIALS("amber_circle", 0xFFD97706.toInt()),
    TEAL_INITIALS("teal_circle", 0xFF0E7490.toInt()),
    STAR_PRESET("star_preset", 0xFF4F46E5.toInt(), iconKey = "star"),
    BOLT_PRESET("bolt_preset", 0xFF059669.toInt(), iconKey = "bolt");

    companion object {
        fun fromId(id: String?): AvatarPreset =
            entries.firstOrNull { it.id == id } ?: COBALT_INITIALS
    }
}

object UsernameValidator {
    private val REGEX = Regex("^[a-z0-9_]{3,20}$")

    fun normalize(input: String): String =
        input.trim().removePrefix("@").lowercase(Locale.ROOT)

    fun isValid(username: String): Boolean =
        REGEX.matches(username)
}

/**
 * Herkese açık kullanıcı profili (`users/{uid}`).
 * Gizlilik kuralı: E-posta adresi veya telefon numarası kesinlikle burada tutulmaz.
 */
data class PublicUserProfile(
    val uid: String,
    val username: String,
    val displayName: String,
    val avatarPreset: String = AvatarPreset.COBALT_INITIALS.id,
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L
)

sealed interface AccountAuthState {
    data object Loading : AccountAuthState
    data object SignedOut : AccountAuthState
    data class NeedsProfileSetup(
        val uid: String,
        val suggestedDisplayName: String,
        val authEmail: String?
    ) : AccountAuthState
    data class SignedIn(
        val profile: PublicUserProfile,
        val authEmail: String?
    ) : AccountAuthState
}

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
    val keyFingerprint: String,
    val isGoogleConnected: Boolean = false,
    val googleEmail: String? = null
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
    val isOnline: Boolean = true,
    val isGroup: Boolean = false
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

