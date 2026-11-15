package com.tabletnotes.app.feature.chat

import com.tabletnotes.app.core.crypto.TinkHybridNoteCryptoService
import com.tabletnotes.app.domain.model.DrawingToolType
import com.tabletnotes.app.domain.model.NoteDocument
import com.tabletnotes.app.domain.model.NotePage
import com.tabletnotes.app.domain.model.NoteStroke
import com.tabletnotes.app.domain.model.StrokePoint
import com.tabletnotes.app.feature.chat.relay.HybridFirebaseRelayStorage
import com.tabletnotes.app.feature.format.binary.NoteBinaryCodec
import java.io.IOException
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineAndResumableRelayTest {

    private val crypto = TinkHybridNoteCryptoService()

    @Test
    fun `interrupted upload at 50 percent resumes from 50 percent and completes when online`() = runTest {
        val tempRelayDir = Files.createTempDirectory("relay_resume_test").toFile()
        try {
            var networkOnline = true
            val relay = HybridFirebaseRelayStorage(
                localRelayDir = tempRelayDir,
                isNetworkAvailableProvider = { networkOnline }
            )

            val recordedStepsAttempt1 = mutableListOf<Int>()
            var interruptedAt = 0

            // 1. Deneme: %50'ye ulaştığında internet kesilsin
            try {
                relay.uploadEncryptedNote(
                    senderId = "user_a",
                    recipientId = "user_b",
                    cipherBytes = ByteArray(4096) { it.toByte() },
                    encryptedKeyEnvelopeBase64 = "env_test",
                    existingRelayObjectId = "relay_resumable_01",
                    resumeFromPercent = 0,
                    onProgress = { pct ->
                        recordedStepsAttempt1.add(pct)
                        interruptedAt = pct
                        if (pct == 50) {
                            networkOnline = false
                        }
                    }
                )
            } catch (_: IOException) {
                // Beklenen kesinti
            }

            assertEquals(50, interruptedAt)
            assertFalse("Yarım kalan yükleme henüz Relay'de hazır olmamalı", relay.existsInRelay("relay_resumable_01"))

            // 2. Deneme: İnternet geri geldiğinde %50'den devam etsin
            networkOnline = true
            val recordedStepsAttempt2 = mutableListOf<Int>()
            val receipt = relay.uploadEncryptedNote(
                senderId = "user_a",
                recipientId = "user_b",
                cipherBytes = ByteArray(4096) { it.toByte() },
                encryptedKeyEnvelopeBase64 = "env_test",
                existingRelayObjectId = "relay_resumable_01",
                resumeFromPercent = interruptedAt,
                onProgress = { pct ->
                    recordedStepsAttempt2.add(pct)
                }
            )

            assertEquals(50, recordedStepsAttempt2.first())
            assertEquals(100, recordedStepsAttempt2.last())
            assertTrue(relay.existsInRelay(receipt.relayObjectId))
        } finally {
            tempRelayDir.deleteRecursively()
        }
    }

    @Test
    fun `10000 strokes end to end encode encrypt upload download decrypt decode stress test`() = runTest {
        val tempRelayDir = Files.createTempDirectory("relay_10k_stress").toFile()
        try {
            val relay = HybridFirebaseRelayStorage(localRelayDir = tempRelayDir)
            val recipientKeys = crypto.generateIdentityKeyBundle()

            val strokes = (0 until 10_000).map { idx ->
                val bx = (idx % 100) * 8f + 20f
                val by = (idx / 100) * 10f + 20f
                NoteStroke(
                    id = "s_$idx",
                    pageIndex = 0,
                    tool = DrawingToolType.PEN,
                    colorArgb = 0xFF1F1F1F.toInt(),
                    thickness = 2.5f,
                    points = listOf(
                        StrokePoint(x = bx, y = by, pressure = 0.5f, timestampMillis = 1000L + idx),
                        StrokePoint(x = bx + 5f, y = by + 4f, pressure = 0.7f, timestampMillis = 1001L + idx)
                    )
                )
            }

            val largeDoc = NoteDocument(
                id = "doc_10k_e2ee",
                title = "10.000 Çizgi Stres Testi Notu",
                pages = listOf(NotePage(pageIndex = 0, widthPt = 960f, heightPt = 1280f)),
                strokes = strokes,
                textBlocks = emptyList(),
                createdAtMillis = 1000L,
                updatedAtMillis = 2000L
            )

            // 1. Vektör binary kodlama (.tnote)
            val encodedBytes = NoteBinaryCodec.encode(largeDoc, compress = true)
            assertTrue("10.000 çizgili .tnote dosyası 10 MB sınırının çok altında olmalı", encodedBytes.size < 1_000_000)

            // 2. Uçtan uca şifreleme (E2EE)
            val encrypted = crypto.encryptNoteForRecipient(encodedBytes, recipientKeys.publicKeyBase64)

            // 3. Relay bekleme odasına yükleme
            val receipt = relay.uploadEncryptedNote(
                senderId = "sender_1",
                recipientId = "recipient_1",
                cipherBytes = encrypted.cipherBytes,
                encryptedKeyEnvelopeBase64 = encrypted.encryptedKeyEnvelopeBase64
            )

            // 4. Alıcı tarafından indirme ve şifre çözme
            val bundle = relay.downloadEncryptedNote(receipt.relayObjectId, "recipient_1")
            val decryptedBytes = crypto.decryptNoteForRecipient(
                cipherBytes = bundle.cipherBytes,
                encryptedKeyEnvelopeBase64 = bundle.encryptedKeyEnvelopeBase64,
                recipientPrivateKeyBase64 = recipientKeys.privateKeyBase64
            )

            // 5. İndirme onayı sonrası Relay kopyasını silme
            assertTrue(relay.confirmDownloadAndDeleteRelayCopy(receipt.relayObjectId, "recipient_1"))
            assertFalse(relay.existsInRelay(receipt.relayObjectId))

            // 6. Vektör veriyi geri çözme ve 10.000 çizgiyi doğrulama
            val restoredDoc = NoteBinaryCodec.decode(decryptedBytes)
            assertEquals(10_000, restoredDoc.strokes.size)
            assertEquals(largeDoc.title, restoredDoc.title)
        } finally {
            tempRelayDir.deleteRecursively()
        }
    }
}

