package com.tabletnotes.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Uygulamanın ana başlangıç sınıfı.
 * Hilt bağımlılık enjeksiyonu (Dependency Injection) ağacını başlatır.
 */
@HiltAndroidApp
class TabletNotesApp : Application()
