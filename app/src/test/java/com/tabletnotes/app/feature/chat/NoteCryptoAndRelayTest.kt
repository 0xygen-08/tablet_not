package com.tabletnotes.app.feature.chat

import com.tabletnotes.app.core.crypto.TinkHybridNoteCryptoService
import com.tabletnotes.app.domain.model.DrawingToolType
import com.tabletnotes.app.domain.model.NoteDocument
import com.tabletnotes.app.domain.model.NotePage
import com.tabletnotes.app.domain.model.NoteStroke
import com.tabletnotes.app.domain.model.StrokePoint
import com.tabletnotes.app.feature.chat.relay.HybridFirebaseRelayStorage
import com.tabletnotes.app.feature.chat.relay.NoteFileTooLargeException
import com.tabletnotes.app.feature.chat.relay.RelayStorage
import com.tabletnotes.app.feature.format.binary.NoteBinaryCodec
import java.nio.file.Files
import java.security.GeneralSecurityException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteCryptoAndRelayTest {

    private val cryptoService = TinkHybridNoteCryptoService()

    private fun createSampleNoteBytes(): ByteArray {
        val doc = NoteDocument(
            id = "e2ee-note-01",
            title = "Gizli Fizik Sınav Çözümü",
            pages = listOf(NotePage(pageIndex = 0, widthPt = 960f, heightPt = 1280f)),
            strokes = listOf(
                NoteStroke(
                    id = "s-1",
                    pageIndex = 0,
                    tool = DrawingToolType.PEN,
                    colorArgb = 0xFF1F1F1F.toInt(),
                    thickness = 3.5f,
                    points = listOf(
                        StrokePoint(x = 50f, y = 100f, pressure = 0.6f, timestampMillis = 1000L),
                        StrokePoint(x = 150f, y = 200f, pressure = 0.8f, timestampMillis = 1016L)
                    )
                )
            ),
            textBlocks = emptyList(),
            createdAtMillis = 1000L,
            updatedAtMillis = 2000L
        )
        return NoteBinaryCodec.encode(doc, compress = true)
    }

    @Test
    fun `e2ee encrypt and decrypt for intended recipient restores exact tnote binary`() {
        val recipientKeys = cryptoService.generateIdentityKeyBundle()
        val plainBytes = createSampleNoteBytes()

        val encryptedPackage = cryptoService.encryptNoteForRecipient(
            plainNoteBytes = plainBytes,
            recipientPublicKeyBase64 = recipientKeys.publicKeyBase64
        )

        // Şifreli veri açık metinle aynı olmamalı
        assertFalse(plainBytes.contentEquals(encryptedPackage.cipherBytes))

        val decryptedBytes = cryptoService.decryptNoteForRecipient(
            cipherBytes = encryptedPackage.cipherBytes,
            encryptedKeyEnvelopeBase64 = encryptedPackage.encryptedKeyEnvelopeBase64,
            recipientPrivateKeyBase64 = recipientKeys.privateKeyBase64
        )

        assertArrayEquals(plainBytes, decryptedBytes)
        val decodedDoc = NoteBinaryCodec.decode(decryptedBytes)
        assertEquals("Gizli Fizik Sınav Çözümü", decodedDoc.title)
        assertEquals(1, decodedDoc.strokes.size)
    }

    @Test(expected = GeneralSecurityException::class)
    fun `e2ee decrypt fails when wrong recipient private key is used`() {
        val aliceKeys = cryptoService.generateIdentityKeyBundle()
        val attackerKeys = cryptoService.generateIdentityKeyBundle()
        val plainBytes = createSampleNoteBytes()

        val encryptedForAlice = cryptoService.encryptNoteForRecipient(
            plainNoteBytes = plainBytes,
            recipientPublicKeyBase64 = aliceKeys.publicKeyBase64
        )

        // Saldırgan kendi özel anahtarıyla çözmeye çalışırsa AEAD kimlik doğrulaması reddeder
        cryptoService.decryptNoteForRecipient(
            cipherBytes = encryptedForAlice.cipherBytes,
            encryptedKeyEnvelopeBase64 = encryptedForAlice.encryptedKeyEnvelopeBase64,
            recipientPrivateKeyBase64 = attackerKeys.privateKeyBase64
        )
    }

    @Test(expected = GeneralSecurityException::class)
    fun `e2ee decrypt fails when ciphertext is tampered with`() {
        val recipientKeys = cryptoService.generateIdentityKeyBundle()
        val plainBytes = createSampleNoteBytes()

        val pkg = cryptoService.encryptNoteForRecipient(
            plainNoteBytes = plainBytes,
            recipientPublicKeyBase64 = recipientKeys.publicKeyBase64
        )

        val tamperedCipher = pkg.cipherBytes.copyOf()
        tamperedCipher[tamperedCipher.size / 2] = (tamperedCipher[tamperedCipher.size / 2].toInt() xor 0xFF).toByte()

        cryptoService.decryptNoteForRecipient(
            cipherBytes = tamperedCipher,
            encryptedKeyEnvelopeBase64 = pkg.encryptedKeyEnvelopeBase64,
            recipientPrivateKeyBase64 = recipientKeys.privateKeyBase64
        )
    }

    @Test
    fun `relay waiting room stores encrypted note and deletes it immediately after confirmed download`() = runTest {
        val tempRelayDir = Files.createTempDirectory("relay_waiting_test").toFile()
        try {
            val relay: RelayStorage = HybridFirebaseRelayStorage(localRelayDir = tempRelayDir)
            val recipientKeys = cryptoService.generateIdentityKeyBundle()
            val plainBytes = createSampleNoteBytes()
            val pkg = cryptoService.encryptNoteForRecipient(plainBytes, recipientKeys.publicKeyBase64)

            val receipt = relay.uploadEncryptedNote(
                senderId = "user_sender",
                recipientId = "user_recipient",
                cipherBytes = pkg.cipherBytes,
                encryptedKeyEnvelopeBase64 = pkg.encryptedKeyEnvelopeBase64
            )

            assertTrue(relay.existsInRelay(receipt.relayObjectId))

            val downloaded = relay.downloadEncryptedNote(
                relayObjectId = receipt.relayObjectId,
                requestingUserId = "user_recipient"
            )
            val decrypted = cryptoService.decryptNoteForRecipient(
                cipherBytes = downloaded.cipherBytes,
                encryptedKeyEnvelopeBase64 = downloaded.encryptedKeyEnvelopeBase64,
                recipientPrivateKeyBase64 = recipientKeys.privateKeyBase64
            )
            assertArrayEquals(plainBytes, decrypted)

            // Başarılı indirme onayı sonrası Relay kopyası silinmeli
            val deleted = relay.confirmDownloadAndDeleteRelayCopy(
                relayObjectId = receipt.relayObjectId,
                requestingUserId = "user_recipient"
            )
            assertTrue(deleted)
            assertFalse("İndirme sonrası Relay'de kopya kalmamalı", relay.existsInRelay(receipt.relayObjectId))
        } finally {
            tempRelayDir.deleteRecursively()
        }
    }

    @Test(expected = SecurityException::class)
    fun `relay blocks unauthorized user from downloading note addressed to someone else`() = runTest {
        val tempRelayDir = Files.createTempDirectory("relay_auth_test").toFile()
        try {
            val relay: RelayStorage = HybridFirebaseRelayStorage(localRelayDir = tempRelayDir)
            val receipt = relay.uploadEncryptedNote(
                senderId = "alice",
                recipientId = "bob",
                cipherBytes = ByteArray(128),
                encryptedKeyEnvelopeBase64 = "env"
            )

            // "mallory" alıcı veya gönderici olmadığı için SecurityException fırlatmalı
            relay.downloadEncryptedNote(
                relayObjectId = receipt.relayObjectId,
                requestingUserId = "mallory"
            )
        } finally {
            tempRelayDir.deleteRecursively()
        }
    }

    @Test(expected = NoteFileTooLargeException::class)
    fun `relay rejects files larger than maximum size limit`() = runTest {
        val tempRelayDir = Files.createTempDirectory("relay_limit_test").toFile()
        try {
            val relay: RelayStorage = HybridFirebaseRelayStorage(localRelayDir = tempRelayDir)
            val oversizedPayload = ByteArray((RelayStorage.MAX_NOTE_SIZE_BYTES + 1024L).toInt())

            relay.uploadEncryptedNote(
                senderId = "alice",
                recipientId = "bob",
                cipherBytes = oversizedPayload,
                encryptedKeyEnvelopeBase64 = "env"
            )
        } finally {
            tempRelayDir.deleteRecursively()
        }
    }
}

