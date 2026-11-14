package com.tabletnotes.app.feature.chat.auth

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.tabletnotes.app.core.crypto.CryptoService
import com.tabletnotes.app.core.crypto.IdentityKeyBundle
import com.tabletnotes.app.domain.model.UserProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface AuthService {
    val currentUser: StateFlow<UserProfile>
    fun getLocalIdentityKeyBundle(): IdentityKeyBundle
    suspend fun signInOrUpdateProfile(displayName: String, emailOrPhone: String): UserProfile
}

@Singleton
class HybridAuthService @Inject constructor(
    @ApplicationContext context: Context,
    private val cryptoService: CryptoService
) : AuthService {

    private val prefs = context.getSharedPreferences("tabletnotes_auth_prefs", Context.MODE_PRIVATE)

    private var keyBundle: IdentityKeyBundle = loadOrCreateKeyBundle()

    private val _currentUser = MutableStateFlow(loadStoredUserProfile())
    override val currentUser: StateFlow<UserProfile> = _currentUser.asStateFlow()

    override fun getLocalIdentityKeyBundle(): IdentityKeyBundle = keyBundle

    override suspend fun signInOrUpdateProfile(displayName: String, emailOrPhone: String): UserProfile {
        val cleanName = displayName.trim().ifBlank { "Tablet Kullanıcısı" }
        val cleanHandle = emailOrPhone.trim().ifBlank { "ogrenci@tablet.edu.tr" }
        val current = _currentUser.value

        val updated = current.copy(
            displayName = cleanName,
            emailOrPhone = cleanHandle
        )

        prefs.edit()
            .putString(KEY_DISPLAY_NAME, updated.displayName)
            .putString(KEY_EMAIL_PHONE, updated.emailOrPhone)
            .apply()

        // Firebase yapılandırılmışsa kullanıcı profilini ve E2EE Açık Anahtarını Firestore'a yayınla
        runCatching {
            FirebaseApp.getInstance()
            val firebaseUid = FirebaseAuth.getInstance().currentUser?.uid ?: updated.uid
            FirebaseFirestore.getInstance()
                .collection("users")
                .document(firebaseUid)
                .set(
                    mapOf(
                        "uid" to firebaseUid,
                        "displayName" to updated.displayName,
                        "emailOrPhone" to updated.emailOrPhone,
                        "publicKeyBase64" to updated.publicKeyBase64,
                        "keyFingerprint" to updated.keyFingerprint
                    )
                )
        }

        _currentUser.value = updated
        return updated
    }

    private fun loadOrCreateKeyBundle(): IdentityKeyBundle {
        val pub = prefs.getString(KEY_PUBLIC_B64, null)
        val priv = prefs.getString(KEY_PRIVATE_B64, null)
        val fp = prefs.getString(KEY_FINGERPRINT, null)
        if (!pub.isNullOrBlank() && !priv.isNullOrBlank() && !fp.isNullOrBlank()) {
            return IdentityKeyBundle(
                publicKeyBase64 = pub,
                privateKeyBase64 = priv,
                fingerprintHex = fp
            )
        }
        val generated = cryptoService.generateIdentityKeyBundle()
        prefs.edit()
            .putString(KEY_PUBLIC_B64, generated.publicKeyBase64)
            .putString(KEY_PRIVATE_B64, generated.privateKeyBase64)
            .putString(KEY_FINGERPRINT, generated.fingerprintHex)
            .apply()
        return generated
    }

    private fun loadStoredUserProfile(): UserProfile {
        val uid = prefs.getString(KEY_UID, null) ?: "user_${UUID.randomUUID().toString().take(8)}".also {
            prefs.edit().putString(KEY_UID, it).apply()
        }
        val name = prefs.getString(KEY_DISPLAY_NAME, "Sen (Pixel Tablet)") ?: "Sen (Pixel Tablet)"
        val emailPhone = prefs.getString(KEY_EMAIL_PHONE, "ogrenci@tablet.edu.tr") ?: "ogrenci@tablet.edu.tr"

        return UserProfile(
            uid = uid,
            displayName = name,
            emailOrPhone = emailPhone,
            publicKeyBase64 = keyBundle.publicKeyBase64,
            keyFingerprint = keyBundle.fingerprintHex
        )
    }

    companion object {
        private const val KEY_UID = "auth_uid"
        private const val KEY_DISPLAY_NAME = "auth_display_name"
        private const val KEY_EMAIL_PHONE = "auth_email_phone"
        private const val KEY_PUBLIC_B64 = "e2ee_public_key_b64"
        private const val KEY_PRIVATE_B64 = "e2ee_private_key_b64"
        private const val KEY_FINGERPRINT = "e2ee_key_fingerprint"
    }
}
