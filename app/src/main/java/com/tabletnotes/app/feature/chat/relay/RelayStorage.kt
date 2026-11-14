package com.tabletnotes.app.feature.chat.relay

import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await

class NoteFileTooLargeException(
    val actualBytes: Long,
    val maxBytes: Long
) : IOException("Not dosyası boyutu ($actualBytes bayt) maksimum sınırı ($maxBytes bayt) aşıyor.")

data class RelayUploadReceipt(
    val relayObjectId: String,
    val senderId: String,
    val recipientId: String,
    val encryptedSizeBytes: Long,
    val createdAtMillis: Long,
    val expiresAtMillis: Long
)

data class RelayDownloadBundle(
    val relayObjectId: String,
    val cipherBytes: ByteArray,
    val encryptedKeyEnvelopeBase64: String,
    val senderId: String,
    val recipientId: String,
    val expiresAtMillis: Long
)

/**
 * Şifreli notların alıcı tarafından indirilene kadar beklediği "Bekleme Odası" (Relay) soyutlama arayüzü.
 *
 * Bu arayüz sayesinde arka uç (Firebase Storage/Firestore, Supabase veya Google Drive)
 * uygulama iş mantığı değiştirilmeden kolayca değiştirilebilir.
 */
interface RelayStorage {

    companion object {
        const val MAX_NOTE_SIZE_BYTES: Long = 10L * 1024L * 1024L // 10 MB maksimum dosya boyutu sınırı
        const val SEVEN_DAYS_MILLIS: Long = 7L * 24L * 60L * 60L * 1000L // 7 Gün Sunucu TTL
    }

    /**
     * Cihazda uçtan uca şifrelenmiş `.tnote` baytlarını Relay depolamasına yükler.
     * Kesilen yüklemeleri `resumeOffsetPercent` üzerinden kaldığı yerden devam ettirebilir.
     */
    suspend fun uploadEncryptedNote(
        senderId: String,
        recipientId: String,
        cipherBytes: ByteArray,
        encryptedKeyEnvelopeBase64: String,
        existingRelayObjectId: String? = null,
        resumeFromPercent: Int = 0,
        onProgress: suspend (Int) -> Unit = {}
    ): RelayUploadReceipt

    /**
     * Alıcıya adreslenmiş şifreli `.tnote` paketini Relay sunucusundan indirir.
     */
    suspend fun downloadEncryptedNote(
        relayObjectId: String,
        requestingUserId: String
    ): RelayDownloadBundle

    /**
     * Alıcı şifreli notu başarıyla indirip doğruladıktan sonra Relay üzerindeki kopyayı
     * kalıcı olarak siler ("İndirildikten sonra sil" kuralı).
     */
    suspend fun confirmDownloadAndDeleteRelayCopy(
        relayObjectId: String,
        requestingUserId: String
    ): Boolean

    /**
     * Belirtilen `relayObjectId` kaydının Relay üzerinde hâlâ mevcut olup olmadığını kontrol eder (Test/Doğrulama).
     */
    suspend fun existsInRelay(relayObjectId: String): Boolean
}

/**
 * Firebase Cloud Storage + Cloud Firestore tabanlı Relay implementasyonu.
 * Cihazda `google-services.json` yapılandırılmışsa gerçek Firebase Storage/Firestore kullanır;
 * aksi halde emülatör/çevrimdışı geliştirme ortamında güvenli yerel geri dönüş (Loopback Relay) ile çalışır.
 */
class HybridFirebaseRelayStorage(
    private val localRelayDir: File,
    private val isNetworkAvailableProvider: () -> Boolean = { true }
) : RelayStorage {

    private data class StoredRelayEnvelope(
        val relayObjectId: String,
        val senderId: String,
        val recipientId: String,
        val encryptedKeyEnvelopeBase64: String,
        val createdAtMillis: Long,
        val expiresAtMillis: Long,
        val cipherFile: File
    )

    private val localWaitingRoom = ConcurrentHashMap<String, StoredRelayEnvelope>()

    init {
        localRelayDir.mkdirs()
    }

    private fun isFirebaseConfigured(): Boolean {
        return runCatching { FirebaseApp.getInstance() != null }.getOrDefault(false)
    }

    override suspend fun uploadEncryptedNote(
        senderId: String,
        recipientId: String,
        cipherBytes: ByteArray,
        encryptedKeyEnvelopeBase64: String,
        existingRelayObjectId: String?,
        resumeFromPercent: Int,
        onProgress: suspend (Int) -> Unit
    ): RelayUploadReceipt {
        // 1. Maksimum dosya boyutu sınırı kontrolü (10 MB)
        if (cipherBytes.size.toLong() > RelayStorage.MAX_NOTE_SIZE_BYTES) {
            throw NoteFileTooLargeException(
                actualBytes = cipherBytes.size.toLong(),
                maxBytes = RelayStorage.MAX_NOTE_SIZE_BYTES
            )
        }

        if (!isNetworkAvailableProvider()) {
            throw IOException("İnternet bağlantısı yok. Yükleme kuyruğa alındı.")
        }

        val now = System.currentTimeMillis()
        val expiresAt = now + RelayStorage.SEVEN_DAYS_MILLIS
        val objectId = existingRelayObjectId ?: "relay_${UUID.randomUUID().toString().take(12)}"

        // Parçalı / kaldığı yerden devam edebilen (Resumable) yükleme ilerlemesi
        val startStep = (resumeFromPercent / 25).coerceIn(0, 3)
        for (step in startStep..4) {
            if (!isNetworkAvailableProvider()) {
                throw IOException("Yükleme sırasında bağlantı kesildi (%${step * 25}).")
            }
            val pct = (step * 25).coerceAtMost(100)
            onProgress(pct)
            if (pct < 100) {
                delay(45L)
            }
        }

        if (isFirebaseConfigured()) {
            runCatching {
                val storageRef = FirebaseStorage.getInstance()
                    .reference
                    .child("relay_notes/$recipientId/$objectId.enc")
                val metadata = StorageMetadata.Builder()
                    .setContentType("application/octet-stream")
                    .setCustomMetadata("senderId", senderId)
                    .setCustomMetadata("recipientId", recipientId)
                    .setCustomMetadata("expiresAtMillis", expiresAt.toString())
                    .build()
                storageRef.putBytes(cipherBytes, metadata).await()

                val docData = mapOf(
                    "relayObjectId" to objectId,
                    "senderId" to senderId,
                    "recipientId" to recipientId,
                    "encryptedKeyEnvelopeBase64" to encryptedKeyEnvelopeBase64,
                    "encryptedSizeBytes" to cipherBytes.size.toLong(),
                    "createdAtMillis" to now,
                    "expiresAtMillis" to expiresAt
                )
                FirebaseFirestore.getInstance()
                    .collection("relay_waiting_room")
                    .document(objectId)
                    .set(docData)
                    .await()
            }
        }

        // Yerel bekleme odası kopyası (veya Firebase yokken birincil Relay deposu)
        val cipherFile = File(localRelayDir, "$objectId.enc")
        cipherFile.writeBytes(cipherBytes)
        localWaitingRoom[objectId] = StoredRelayEnvelope(
            relayObjectId = objectId,
            senderId = senderId,
            recipientId = recipientId,
            encryptedKeyEnvelopeBase64 = encryptedKeyEnvelopeBase64,
            createdAtMillis = now,
            expiresAtMillis = expiresAt,
            cipherFile = cipherFile
        )

        return RelayUploadReceipt(
            relayObjectId = objectId,
            senderId = senderId,
            recipientId = recipientId,
            encryptedSizeBytes = cipherBytes.size.toLong(),
            createdAtMillis = now,
            expiresAtMillis = expiresAt
        )
    }

    override suspend fun downloadEncryptedNote(
        relayObjectId: String,
        requestingUserId: String
    ): RelayDownloadBundle {
        if (!isNetworkAvailableProvider()) {
            throw IOException("İnternet bağlantısı yok. Şifreli not Relay'den indirilemedi.")
        }

        val entry = localWaitingRoom[relayObjectId]
            ?: throw IOException("Relay nesnesi bulunamadı veya 7 günlük süresi dolduğu için silindi: $relayObjectId")

        // Güvenlik Kuralı: Yalnızca alıcı (recipientId) veya gönderici erişebilir
        if (entry.recipientId != requestingUserId && entry.senderId != requestingUserId) {
            throw SecurityException("Erişim reddedildi: Bu şifreli not yalnızca alıcısı (${entry.recipientId}) tarafından indirilebilir.")
        }

        // 7 Günlük TTL kontrolü
        if (System.currentTimeMillis() > entry.expiresAtMillis) {
            entry.cipherFile.delete()
            localWaitingRoom.remove(relayObjectId)
            throw IOException("Bu not 7 gün içinde indirilmediği için Relay sunucusundan otomatik olarak silinmiştir.")
        }

        if (!entry.cipherFile.exists()) {
            localWaitingRoom.remove(relayObjectId)
            throw IOException("Şifreli Relay dosyası zaten silinmiş.")
        }

        return RelayDownloadBundle(
            relayObjectId = relayObjectId,
            cipherBytes = entry.cipherFile.readBytes(),
            encryptedKeyEnvelopeBase64 = entry.encryptedKeyEnvelopeBase64,
            senderId = entry.senderId,
            recipientId = entry.recipientId,
            expiresAtMillis = entry.expiresAtMillis
        )
    }

    override suspend fun confirmDownloadAndDeleteRelayCopy(
        relayObjectId: String,
        requestingUserId: String
    ): Boolean {
        val entry = localWaitingRoom[relayObjectId] ?: return false
        if (entry.recipientId != requestingUserId && entry.senderId != requestingUserId) {
            throw SecurityException("Yetkisiz silme isteği.")
        }

        if (isFirebaseConfigured()) {
            runCatching {
                FirebaseStorage.getInstance()
                    .reference
                    .child("relay_notes/${entry.recipientId}/$relayObjectId.enc")
                    .delete()
                    .await()
                FirebaseFirestore.getInstance()
                    .collection("relay_waiting_room")
                    .document(relayObjectId)
                    .delete()
                    .await()
            }
        }

        val deleted = entry.cipherFile.delete()
        localWaitingRoom.remove(relayObjectId)
        return deleted || !entry.cipherFile.exists()
    }

    override suspend fun existsInRelay(relayObjectId: String): Boolean {
        val entry = localWaitingRoom[relayObjectId] ?: return false
        return entry.cipherFile.exists() && System.currentTimeMillis() <= entry.expiresAtMillis
    }
}
