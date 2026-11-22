package com.tabletnotes.app.core.firebase

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firebase Spark (Ücretsiz Plan) servislerini (`FirebaseAuth` ve Çevrimdışı Önbellekli `FirebaseFirestore`)
 * güvenli ve tek noktadan sağlayan yardımcı sınıf.
 *
 * `app/google-services.json` henüz eklenmemişse uygulama çökmeden Faz 1 çevrimdışı modunda
 * çalışmaya devam eder (`isConfigured = false`).
 */
@Singleton
class FirebaseProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val app: FirebaseApp? by lazy {
        runCatching {
            if (FirebaseApp.getApps(context).isNotEmpty()) {
                FirebaseApp.getInstance()
            } else {
                FirebaseApp.initializeApp(context)
            }
        }.getOrNull()
    }

    val isConfigured: Boolean
        get() = app != null

    val projectId: String?
        get() = app?.options?.projectId

    val authOrNull: FirebaseAuth? by lazy {
        val firebaseApp = app ?: return@lazy null
        runCatching { FirebaseAuth.getInstance(firebaseApp) }.getOrNull()
    }

    val firestoreOrNull: FirebaseFirestore? by lazy {
        val firebaseApp = app ?: return@lazy null
        runCatching {
            FirebaseFirestore.getInstance(firebaseApp).apply {
                // Spark ücretsiz plan kotasını korumak ve çevrimdışı destek için kalıcı yerel önbellek
                val settings = FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(
                        PersistentCacheSettings.newBuilder()
                            .setSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                            .build()
                    )
                    .build()
                firestoreSettings = settings
            }
        }.getOrNull()
    }
}

