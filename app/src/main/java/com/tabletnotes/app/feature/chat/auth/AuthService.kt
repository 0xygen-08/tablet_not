package com.tabletnotes.app.feature.chat.auth

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.tabletnotes.app.core.crypto.CryptoService
import com.tabletnotes.app.core.crypto.IdentityKeyBundle
import com.tabletnotes.app.core.firebase.FirebaseProvider
import com.tabletnotes.app.domain.model.AccountAuthState
import com.tabletnotes.app.domain.model.AvatarPreset
import com.tabletnotes.app.domain.model.PublicUserProfile
import com.tabletnotes.app.domain.model.UserProfile
import com.tabletnotes.app.domain.model.UsernameValidator
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

interface AuthService {
    val currentUser: StateFlow<UserProfile>
    val authState: StateFlow<AccountAuthState>
    val isFirebaseReady: Boolean
    fun getLocalIdentityKeyBundle(): IdentityKeyBundle

    suspend fun signUpWithEmail(
        email: String,
        password: String,
        displayName: String,
        rawUsername: String,
        avatarPreset: String
    ): Result<PublicUserProfile>

    suspend fun signInWithEmail(email: String, password: String): Result<Unit>
    suspend fun signInWithGoogleIdToken(idToken: String): Result<Unit>
    suspend fun completeProfileSetup(
        displayName: String,
        rawUsername: String,
        avatarPreset: String
    ): Result<PublicUserProfile>

    suspend fun updateProfile(
        newDisplayName: String,
        newRawUsername: String,
        newAvatarPreset: String
    ): Result<PublicUserProfile>

    suspend fun sendPasswordReset(email: String): Result<Unit>
    suspend fun signOut()
    suspend fun deleteAccountWithPassword(password: String): Result<Unit>

    // Geriye dönük uyumluluk yardımcıları
    suspend fun signInOrUpdateProfile(displayName: String, emailOrPhone: String): UserProfile
    suspend fun toggleGoogleSignInVisual(connect: Boolean, email: String? = null): UserProfile
}

@Singleton
class HybridAuthService @Inject constructor(
    @ApplicationContext context: Context,
    private val cryptoService: CryptoService,
    private val firebaseProvider: FirebaseProvider
) : AuthService {

    private val prefs = context.getSharedPreferences("tabletnotes_auth_prefs", Context.MODE_PRIVATE)
    private var keyBundle: IdentityKeyBundle = loadOrCreateKeyBundle()

    private val _currentUser = MutableStateFlow(loadStoredUserProfile())
    override val currentUser: StateFlow<UserProfile> = _currentUser.asStateFlow()

    private val _authState = MutableStateFlow<AccountAuthState>(AccountAuthState.Loading)
    override val authState: StateFlow<AccountAuthState> = _authState.asStateFlow()

    override val isFirebaseReady: Boolean
        get() = firebaseProvider.isConfigured

    private var profileListener: ListenerRegistration? = null

    private val authStateListener = FirebaseAuth.AuthStateListener { auth ->
        onFirebaseUserChanged(auth.currentUser)
    }

    init {
        val auth = firebaseProvider.authOrNull
        if (auth != null) {
            onFirebaseUserChanged(auth.currentUser)
            auth.addAuthStateListener(authStateListener)
        } else {
            _authState.value = AccountAuthState.SignedOut
        }
    }

    private fun onFirebaseUserChanged(user: FirebaseUser?) {
        detachProfileListener()
        if (user == null) {
            _authState.value = AccountAuthState.SignedOut
            return
        }

        val firestore = firebaseProvider.firestoreOrNull
        if (firestore == null) {
            _authState.value = AccountAuthState.SignedOut
            return
        }

        // Yalnızca oturum açıkken kullanıcının kendi profil belgesini dinle (çıkışta sökülür)
        profileListener = firestore.collection("users")
            .document(user.uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val username = snapshot.getString("username").orEmpty()
                    val displayName = snapshot.getString("displayName").orEmpty()
                    val avatarPreset = snapshot.getString("avatarPreset") ?: AvatarPreset.COBALT_INITIALS.id
                    if (username.isNotBlank() && displayName.isNotBlank()) {
                        val profile = PublicUserProfile(
                            uid = user.uid,
                            username = username,
                            displayName = displayName,
                            avatarPreset = avatarPreset,
                            createdAtMillis = snapshot.getTimestamp("createdAt")?.toDate()?.time ?: 0L,
                            updatedAtMillis = snapshot.getTimestamp("updatedAt")?.toDate()?.time ?: 0L
                        )
                        _authState.value = AccountAuthState.SignedIn(
                            profile = profile,
                            authEmail = user.email
                        )
                        syncLegacyLocalProfile(profile, user.email)
                        return@addSnapshotListener
                    }
                }
                // İlk kez Google ile giriş yapan veya profili tamamlanmamış kullanıcı
                _authState.value = AccountAuthState.NeedsProfileSetup(
                    uid = user.uid,
                    suggestedDisplayName = user.displayName?.takeIf { it.isNotBlank() } ?: "",
                    authEmail = user.email
                )
            }
    }

    private fun detachProfileListener() {
        profileListener?.remove()
        profileListener = null
    }

    override fun getLocalIdentityKeyBundle(): IdentityKeyBundle = keyBundle

    override suspend fun signUpWithEmail(
        email: String,
        password: String,
        displayName: String,
        rawUsername: String,
        avatarPreset: String
    ): Result<PublicUserProfile> = runCatching {
        val auth = firebaseProvider.authOrNull ?: throw FirebaseNotConfiguredException()
        val firestore = firebaseProvider.firestoreOrNull ?: throw FirebaseNotConfiguredException()

        val cleanDisplayName = displayName.trim()
        if (cleanDisplayName.isEmpty() || cleanDisplayName.length > 50) {
            throw InvalidDisplayNameException()
        }
        val normalizedUsername = UsernameValidator.normalize(rawUsername)
        if (!UsernameValidator.isValid(normalizedUsername)) {
            throw InvalidUsernameFormatException()
        }

        // Kullanıcı adını önceden kontrol et (ardından batch + Rules kesin garanti sağlar)
        val existingUsername = firestore.collection("usernames").document(normalizedUsername).get().await()
        if (existingUsername.exists()) {
            throw UsernameAlreadyTakenException()
        }

        val authResult = auth.createUserWithEmailAndPassword(email.trim(), password).await()
        val firebaseUser = authResult.user ?: throw NotSignedInException()

        try {
            claimUsernameAndWriteProfile(
                uid = firebaseUser.uid,
                authEmail = firebaseUser.email ?: email.trim(),
                displayName = cleanDisplayName,
                username = normalizedUsername,
                avatarPreset = avatarPreset
            )
        } catch (e: Exception) {
            // Kullanıcı adı son milisaniyede başkası tarafından kapıldıysa kullanıcı profili kurulum ekranında başka ad seçebilir
            throw e
        }
    }

    override suspend fun signInWithEmail(email: String, password: String): Result<Unit> = runCatching {
        val auth = firebaseProvider.authOrNull ?: throw FirebaseNotConfiguredException()
        auth.signInWithEmailAndPassword(email.trim(), password).await()
        Unit
    }

    override suspend fun signInWithGoogleIdToken(idToken: String): Result<Unit> = runCatching {
        val auth = firebaseProvider.authOrNull ?: throw FirebaseNotConfiguredException()
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential).await()
        Unit
    }

    override suspend fun completeProfileSetup(
        displayName: String,
        rawUsername: String,
        avatarPreset: String
    ): Result<PublicUserProfile> = runCatching {
        val auth = firebaseProvider.authOrNull ?: throw FirebaseNotConfiguredException()
        val user = auth.currentUser ?: throw NotSignedInException()

        val cleanDisplayName = displayName.trim()
        if (cleanDisplayName.isEmpty() || cleanDisplayName.length > 50) {
            throw InvalidDisplayNameException()
        }
        val normalizedUsername = UsernameValidator.normalize(rawUsername)
        if (!UsernameValidator.isValid(normalizedUsername)) {
            throw InvalidUsernameFormatException()
        }

        claimUsernameAndWriteProfile(
            uid = user.uid,
            authEmail = user.email,
            displayName = cleanDisplayName,
            username = normalizedUsername,
            avatarPreset = avatarPreset
        )
    }

    override suspend fun updateProfile(
        newDisplayName: String,
        newRawUsername: String,
        newAvatarPreset: String
    ): Result<PublicUserProfile> = runCatching {
        val auth = firebaseProvider.authOrNull ?: throw FirebaseNotConfiguredException()
        val firestore = firebaseProvider.firestoreOrNull ?: throw FirebaseNotConfiguredException()
        val user = auth.currentUser ?: throw NotSignedInException()

        val cleanDisplayName = newDisplayName.trim()
        if (cleanDisplayName.isEmpty() || cleanDisplayName.length > 50) {
            throw InvalidDisplayNameException()
        }
        val normalizedNewUsername = UsernameValidator.normalize(newRawUsername)
        if (!UsernameValidator.isValid(normalizedNewUsername)) {
            throw InvalidUsernameFormatException()
        }

        val currentSignedIn = _authState.value as? AccountAuthState.SignedIn
        val oldUsername = currentSignedIn?.profile?.username

        val userRef = firestore.collection("users").document(user.uid)
        val newUsernameRef = firestore.collection("usernames").document(normalizedNewUsername)

        if (oldUsername != normalizedNewUsername) {
            val existing = newUsernameRef.get().await()
            if (existing.exists() && existing.getString("uid") != user.uid) {
                throw UsernameAlreadyTakenException()
            }
        }

        val batch = firestore.batch()
        if (oldUsername != null && oldUsername != normalizedNewUsername) {
            val oldUsernameRef = firestore.collection("usernames").document(oldUsername)
            batch.delete(oldUsernameRef)
        }
        if (oldUsername != normalizedNewUsername) {
            batch.set(
                newUsernameRef,
                mapOf(
                    "uid" to user.uid,
                    "username" to normalizedNewUsername,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
        }
        // Gizlilik: Herkese açık belgede asla e-posta veya telefon saklanmaz!
        batch.set(
            userRef,
            mapOf(
                "uid" to user.uid,
                "username" to normalizedNewUsername,
                "displayName" to cleanDisplayName,
                "avatarPreset" to AvatarPreset.fromId(newAvatarPreset).id,
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            SetOptions.merge()
        )
        batch.commit().await()

        val updated = PublicUserProfile(
            uid = user.uid,
            username = normalizedNewUsername,
            displayName = cleanDisplayName,
            avatarPreset = AvatarPreset.fromId(newAvatarPreset).id,
            createdAtMillis = currentSignedIn?.profile?.createdAtMillis ?: System.currentTimeMillis(),
            updatedAtMillis = System.currentTimeMillis()
        )
        _authState.value = AccountAuthState.SignedIn(updated, user.email)
        syncLegacyLocalProfile(updated, user.email)
        updated
    }

    override suspend fun sendPasswordReset(email: String): Result<Unit> = runCatching {
        val auth = firebaseProvider.authOrNull ?: throw FirebaseNotConfiguredException()
        auth.sendPasswordResetEmail(email.trim()).await()
        Unit
    }

    override suspend fun signOut() {
        detachProfileListener()
        firebaseProvider.authOrNull?.signOut()
        _authState.value = AccountAuthState.SignedOut
    }

    override suspend fun deleteAccountWithPassword(password: String): Result<Unit> = runCatching {
        val auth = firebaseProvider.authOrNull ?: throw FirebaseNotConfiguredException()
        val firestore = firebaseProvider.firestoreOrNull ?: throw FirebaseNotConfiguredException()
        val user = auth.currentUser ?: throw NotSignedInException()
        val uid = user.uid
        val signedInState = _authState.value as? AccountAuthState.SignedIn
        val currentUsername = signedInState?.profile?.username

        val email = user.email
        if (!email.isNullOrBlank() && password.isNotBlank()) {
            val credential = com.google.firebase.auth.EmailAuthProvider.getCredential(email, password)
            user.reauthenticate(credential).await()
        }

        detachProfileListener()

        // 1. Kendi engelleme kayıtlarını sil (en iyi çaba)
        runCatching {
            val blocksSnap = firestore.collection("blocks")
                .whereEqualTo("blockerUid", uid)
                .limit(100)
                .get()
                .await()
            if (!blocksSnap.isEmpty) {
                val b = firestore.batch()
                blocksSnap.documents.forEach { b.delete(it.reference) }
                b.commit().await()
            }
        }

        // 2. Kendi arkadaşlık kayıtlarını sil (en iyi çaba)
        runCatching {
            val friendsSnap = firestore.collection("friendships")
                .whereArrayContains("users", uid)
                .limit(100)
                .get()
                .await()
            if (!friendsSnap.isEmpty) {
                val b = firestore.batch()
                friendsSnap.documents.forEach { b.delete(it.reference) }
                b.commit().await()
            }
        }

        // 3. Grup üyeliklerini güncelle veya kurucusu olduğu grubu sil
        runCatching {
            val groupsSnap = firestore.collection("groups")
                .whereArrayContains("memberIds", uid)
                .limit(50)
                .get()
                .await()
            for (doc in groupsSnap.documents) {
                val ownerId = doc.getString("ownerId")
                if (ownerId == uid) {
                    doc.reference.delete().await()
                } else {
                    val memberIds = (doc.get("memberIds") as? List<*>)?.mapNotNull { it as? String }?.filter { it != uid } ?: emptyList()
                    val roles = (doc.get("roles") as? Map<*, *>)?.entries
                        ?.mapNotNull { (k, v) -> (k as? String)?.let { ks -> (v as? String)?.let { vs -> ks to vs } } }
                        ?.toMap()
                        ?.toMutableMap()
                        ?.apply { remove(uid) }
                        ?: emptyMap()
                    doc.reference.update(
                        mapOf(
                            "memberIds" to memberIds,
                            "roles" to roles,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
        }

        // 4. Kullanıcı adı indeksini ve profili sil
        runCatching {
            val batch = firestore.batch()
            if (!currentUsername.isNullOrBlank()) {
                batch.delete(firestore.collection("usernames").document(currentUsername))
            }
            batch.delete(firestore.collection("users").document(uid))
            batch.commit().await()
        }

        // 5. Firebase Auth kullanıcısını sil (diğer kişilerin sohbetlerindeki eski mesajlar sunucu kodu olmadan temizlenemez; Faz 3 için dokümante edilmiştir)
        user.delete().await()
        _authState.value = AccountAuthState.SignedOut
        Unit
    }

    private suspend fun claimUsernameAndWriteProfile(
        uid: String,
        authEmail: String?,
        displayName: String,
        username: String,
        avatarPreset: String
    ): PublicUserProfile {
        val firestore = firebaseProvider.firestoreOrNull ?: throw FirebaseNotConfiguredException()
        val usernameRef = firestore.collection("usernames").document(username)
        val userRef = firestore.collection("users").document(uid)

        val existing = usernameRef.get().await()
        if (existing.exists() && existing.getString("uid") != uid) {
            throw UsernameAlreadyTakenException()
        }

        val resolvedAvatar = AvatarPreset.fromId(avatarPreset).id
        val batch = firestore.batch()
        batch.set(
            usernameRef,
            mapOf(
                "uid" to uid,
                "username" to username,
                "createdAt" to FieldValue.serverTimestamp()
            )
        )
        // Gizlilik: users/{uid} içinde kesinlikle email/phone yok!
        batch.set(
            userRef,
            mapOf(
                "uid" to uid,
                "username" to username,
                "displayName" to displayName,
                "avatarPreset" to resolvedAvatar,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        batch.commit().await()

        val profile = PublicUserProfile(
            uid = uid,
            username = username,
            displayName = displayName,
            avatarPreset = resolvedAvatar,
            createdAtMillis = System.currentTimeMillis(),
            updatedAtMillis = System.currentTimeMillis()
        )
        _authState.value = AccountAuthState.SignedIn(profile = profile, authEmail = authEmail)
        syncLegacyLocalProfile(profile, authEmail)
        return profile
    }

    private fun syncLegacyLocalProfile(profile: PublicUserProfile, authEmail: String?) {
        val updated = _currentUser.value.copy(
            uid = profile.uid,
            displayName = profile.displayName,
            emailOrPhone = authEmail ?: "@${profile.username}"
        )
        prefs.edit()
            .putString(KEY_DISPLAY_NAME, updated.displayName)
            .putString(KEY_EMAIL_PHONE, updated.emailOrPhone)
            .apply()
        _currentUser.value = updated
    }

    override suspend fun signInOrUpdateProfile(displayName: String, emailOrPhone: String): UserProfile {
        val cleanName = displayName.trim().ifBlank { "Tablet Kullanıcısı" }
        val cleanHandle = emailOrPhone.trim().ifBlank { "ogrenci@tablet.edu.tr" }
        val updated = _currentUser.value.copy(
            displayName = cleanName,
            emailOrPhone = cleanHandle
        )
        prefs.edit()
            .putString(KEY_DISPLAY_NAME, updated.displayName)
            .putString(KEY_EMAIL_PHONE, updated.emailOrPhone)
            .apply()
        _currentUser.value = updated
        return updated
    }

    override suspend fun toggleGoogleSignInVisual(connect: Boolean, email: String?): UserProfile {
        val current = _currentUser.value
        val resolvedEmail = if (connect) {
            email?.trim()?.takeIf { it.isNotBlank() } ?: current.googleEmail ?: "ogrenci@gmail.com"
        } else {
            null
        }
        val updated = current.copy(
            isGoogleConnected = connect,
            googleEmail = resolvedEmail,
            emailOrPhone = if (connect && resolvedEmail != null) resolvedEmail else current.emailOrPhone
        )
        prefs.edit()
            .putBoolean(KEY_GOOGLE_CONNECTED, updated.isGoogleConnected)
            .putString(KEY_GOOGLE_EMAIL, updated.googleEmail)
            .putString(KEY_EMAIL_PHONE, updated.emailOrPhone)
            .apply()
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
        val isGoogle = prefs.getBoolean(KEY_GOOGLE_CONNECTED, false)
        val googleMail = prefs.getString(KEY_GOOGLE_EMAIL, null)

        return UserProfile(
            uid = uid,
            displayName = name,
            emailOrPhone = emailPhone,
            publicKeyBase64 = keyBundle.publicKeyBase64,
            keyFingerprint = keyBundle.fingerprintHex,
            isGoogleConnected = isGoogle,
            googleEmail = googleMail
        )
    }

    companion object {
        private const val KEY_UID = "auth_uid"
        private const val KEY_DISPLAY_NAME = "auth_display_name"
        private const val KEY_EMAIL_PHONE = "auth_email_phone"
        private const val KEY_GOOGLE_CONNECTED = "auth_google_connected"
        private const val KEY_GOOGLE_EMAIL = "auth_google_email"
        private const val KEY_PUBLIC_B64 = "e2ee_public_key_b64"
        private const val KEY_PRIVATE_B64 = "e2ee_private_key_b64"
        private const val KEY_FINGERPRINT = "e2ee_key_fingerprint"
    }
}
