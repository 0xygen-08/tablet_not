package com.tabletnotes.app

import android.app.Application
import com.tabletnotes.app.core.firebase.FirebaseProvider
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Uygulamanın ana başlangıç sınıfı.
 * Hilt bağımlılık enjeksiyonunu ve varsa Firebase Spark servislerini (çevrimdışı önbellek ile) başlatır.
 */
@HiltAndroidApp
class TabletNotesApp : Application() {

    @Inject
    lateinit var firebaseProvider: FirebaseProvider

    override fun onCreate() {
        super.onCreate()
        // google-services.json mevcutsa Firestore kalıcı önbelleğini uygulama başlangıcında hazırlar
        firebaseProvider.firestoreOrNull
    }
}
