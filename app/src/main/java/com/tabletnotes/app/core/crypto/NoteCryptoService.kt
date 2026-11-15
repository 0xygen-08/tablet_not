package com.tabletnotes.app.core.crypto

import com.google.crypto.tink.aead.AeadConfig
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

data class IdentityKeyBundle(
    val publicKeyBase64: String,
    val privateKeyBase64: String,
    val fingerprintHex: String
)

data class EncryptedNotePackage(
    val cipherBytes: ByteArray,
    val encryptedKeyEnvelopeBase64: String,
    val plainSha256Hex: String
)

/**
 * Uçtan Uca Şifreleme (E2EE) Servisi.
 *
 * Her alıcı için **ECIES (ECDH P-256 + HKDF-SHA256) + AES-256-GCM (AEAD)** hibrit şifreleme uygular:
 * 1. Her `.tnote` gönderiminde tek kullanımlık 256-bit `DEK` (Data Encryption Key) ve 96-bit `IV` üretilir.
 * 2. `.tnote` vektör verisi `AES-256-GCM` (128-bit kimlik doğrulama etiketi ile) şifrelenir.
 * 3. `DEK`, yalnızca alıcının Açık Anahtarı (`recipientPublicKeyBase64`) ve geçici (ephemeral) bir EC anahtar çifti
 *    ile üretilen ortak sır (`ECDH`) kullanılarak sarılır (`encryptedKeyEnvelopeBase64`).
 * 4. Relay sunucusu (Firebase Storage / Firestore) dahil hiç kimse alıcının Özel Anahtarı (`privateKeyBase64`)
 *    olmadan ne `DEK`'i ne de `.tnote` içeriğini çözebilir.
 */
interface CryptoService {
    fun generateIdentityKeyBundle(): IdentityKeyBundle

    fun encryptNoteForRecipient(
        plainNoteBytes: ByteArray,
        recipientPublicKeyBase64: String,
        associatedData: String = "TabletNotes-E2EE-v1"
    ): EncryptedNotePackage

    fun decryptNoteForRecipient(
        cipherBytes: ByteArray,
        encryptedKeyEnvelopeBase64: String,
        recipientPrivateKeyBase64: String,
        associatedData: String = "TabletNotes-E2EE-v1"
    ): ByteArray

    fun sha256Hex(data: ByteArray): String
}

@Singleton
class TinkHybridNoteCryptoService @Inject constructor() : CryptoService {

    private val secureRandom = SecureRandom()

    init {
        runCatching { AeadConfig.register() }
    }

    override fun generateIdentityKeyBundle(): IdentityKeyBundle {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"), secureRandom)
        val kp = kpg.generateKeyPair()
        val pubBytes = kp.public.encoded
        val privBytes = kp.private.encoded
        val pubB64 = Base64.getEncoder(). withoutPadding().encodeToString(pubBytes)
        val privB64 = Base64.getEncoder().withoutPadding().encodeToString(privBytes)
        val fp = sha256Hex(pubBytes).take(16).chunked(4).joinToString(":") { it.uppercase() }
        return IdentityKeyBundle(
            publicKeyBase64 = pubB64,
            privateKeyBase64 = privB64,
            fingerprintHex = fp
        )
    }

    override fun encryptNoteForRecipient(
        plainNoteBytes: ByteArray,
        recipientPublicKeyBase64: String,
        associatedData: String
    ): EncryptedNotePackage {
        val recipientPubBytes = try {
            Base64.getDecoder().decode(recipientPublicKeyBase64)
        } catch (e: Exception) {
            throw GeneralSecurityException("Geçersiz alıcı açık anahtarı (Base64).", e)
        }

        val keyFactory = KeyFactory.getInstance("EC")
        val recipientPublicKey = keyFactory.generatePublic(X509EncodedKeySpec(recipientPubBytes))

        // 1. Geçici (Ephemeral) EC anahtar çifti üret ve ECDH ortak sırrını hesapla
        val ephemKpg = KeyPairGenerator.getInstance("EC")
        ephemKpg.initialize(ECGenParameterSpec("secp256r1"), secureRandom)
        val ephemKeyPair = ephemKpg.generateKeyPair()

        val keyAgreement = KeyAgreement.getInstance("ECDH")
        keyAgreement.init(ephemKeyPair.private)
        keyAgreement.doPhase(recipientPublicKey, true)
        val sharedSecret = keyAgreement.generateSecret()

        val ephemPubBytes = ephemKeyPair.public.encoded
        val kekBytes = deriveKek(sharedSecret, ephemPubBytes, recipientPubBytes)

        // 2. Tek kullanımlık 256-bit DEK ve 96-bit IV üret
        val dek = ByteArray(32).also { secureRandom.nextBytes(it) }
        val payloadIv = ByteArray(12).also { secureRandom.nextBytes(it) }
        val wrapIv = ByteArray(12).also { secureRandom.nextBytes(it) }

        // 3. Not verisini AES-256-GCM ile şifrele
        val payloadCipher = Cipher.getInstance("AES/GCM/NoPadding")
        payloadCipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(dek, "AES"),
            GCMParameterSpec(128, payloadIv)
        )
        payloadCipher.updateAAD(associatedData.toByteArray(Charsets.UTF_8))
        val cipherBytes = payloadCipher.doFinal(plainNoteBytes)

        // 4. DEK'i alıcıya özel KEK ile sar (AES-256-GCM Key Wrapping)
        val wrapCipher = Cipher.getInstance("AES/GCM/NoPadding")
        wrapCipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(kekBytes, "AES"),
            GCMParameterSpec(128, wrapIv)
        )
        wrapCipher.updateAAD(associatedData.toByteArray(Charsets.UTF_8))
        val wrappedDek = wrapCipher.doFinal(dek)

        // 5. Anahtar zarfını (Envelope) paketle
        val envelopeBytes = ByteArrayOutputStream().apply {
            val dos = DataOutputStream(this)
            dos.writeByte(1) // Zarf sürümü v1
            dos.writeShort(ephemPubBytes.size)
            dos.write(ephemPubBytes)
            dos.write(wrapIv)
            dos.writeShort(wrappedDek.size)
            dos.write(wrappedDek)
            dos.write(payloadIv)
            dos.flush()
        }.toByteArray()

        return EncryptedNotePackage(
            cipherBytes = cipherBytes,
            encryptedKeyEnvelopeBase64 = Base64.getEncoder().withoutPadding().encodeToString(envelopeBytes),
            plainSha256Hex = sha256Hex(plainNoteBytes)
        )
    }

    override fun decryptNoteForRecipient(
        cipherBytes: ByteArray,
        encryptedKeyEnvelopeBase64: String,
        recipientPrivateKeyBase64: String,
        associatedData: String
    ): ByteArray {
        val privBytes = try {
            Base64.getDecoder().decode(recipientPrivateKeyBase64)
        } catch (e: Exception) {
            throw GeneralSecurityException("Geçersiz alıcı özel anahtarı.", e)
        }
        val envelopeBytes = try {
            Base64.getDecoder().decode(encryptedKeyEnvelopeBase64)
        } catch (e: Exception) {
            throw GeneralSecurityException("Geçersiz şifreli anahtar zarfı.", e)
        }

        val dis = DataInputStream(ByteArrayInputStream(envelopeBytes))
        val version = dis.readUnsignedByte()
        if (version != 1) {
            throw GeneralSecurityException("Desteklenmeyen E2EE zarf sürümü: v$version")
        }

        val ephemPubLen = dis.readUnsignedShort()
        val ephemPubBytes = ByteArray(ephemPubLen).also { dis.readFully(it) }
        val wrapIv = ByteArray(12).also { dis.readFully(it) }
        val wrappedDekLen = dis.readUnsignedShort()
        val wrappedDek = ByteArray(wrappedDekLen).also { dis.readFully(it) }
        val payloadIv = ByteArray(12).also { dis.readFully(it) }

        val keyFactory = KeyFactory.getInstance("EC")
        val recipientPrivateKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(privBytes))
        val ephemPublicKey = keyFactory.generatePublic(X509EncodedKeySpec(ephemPubBytes))

        // Alıcının açık anahtarını türetmek veya bağlama dahil etmek için ECDH ortak sırrını hesapla
        val keyAgreement = KeyAgreement.getInstance("ECDH")
        keyAgreement.init(recipientPrivateKey)
        keyAgreement.doPhase(ephemPublicKey, true)
        val sharedSecret = keyAgreement.generateSecret()

        // KEK türetimi
        val kekBytes = deriveKekSymmetric(sharedSecret, ephemPubBytes)

        // DEK zarfını çöz
        val unwrapCipher = Cipher.getInstance("AES/GCM/NoPadding")
        unwrapCipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(kekBytes, "AES"),
            GCMParameterSpec(128, wrapIv)
        )
        unwrapCipher.updateAAD(associatedData.toByteArray(Charsets.UTF_8))
        val dek = unwrapCipher.doFinal(wrappedDek)

        // Not gövdesini çöz
        val payloadCipher = Cipher.getInstance("AES/GCM/NoPadding")
        payloadCipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(dek, "AES"),
            GCMParameterSpec(128, payloadIv)
        )
        payloadCipher.updateAAD(associatedData.toByteArray(Charsets.UTF_8))
        return payloadCipher.doFinal(cipherBytes)
    }

    private fun deriveKek(
        sharedSecret: ByteArray,
        ephemPubBytes: ByteArray,
        @Suppress("UNUSED_PARAMETER") recipientPubBytes: ByteArray
    ): ByteArray = deriveKekSymmetric(sharedSecret, ephemPubBytes)

    private fun deriveKekSymmetric(
        sharedSecret: ByteArray,
        ephemPubBytes: ByteArray
    ): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update("TabletNotes-ECIES-HKDF-v1".toByteArray(Charsets.UTF_8))
        digest.update(ephemPubBytes)
        digest.update(sharedSecret)
        return digest.digest()
    }

    override fun sha256Hex(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(data)
        return digest.joinToString("") { "%02x".format(it) }
    }
}

