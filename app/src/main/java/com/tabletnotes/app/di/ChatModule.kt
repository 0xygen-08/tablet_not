package com.tabletnotes.app.di

import android.content.Context
import com.tabletnotes.app.core.crypto.CryptoService
import com.tabletnotes.app.core.crypto.TinkHybridNoteCryptoService
import com.tabletnotes.app.core.network.AndroidConnectivityObserver
import com.tabletnotes.app.core.network.ConnectivityObserver
import com.tabletnotes.app.feature.chat.auth.AuthService
import com.tabletnotes.app.feature.chat.auth.HybridAuthService
import com.tabletnotes.app.feature.chat.relay.HybridFirebaseRelayStorage
import com.tabletnotes.app.feature.chat.relay.RelayStorage
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ChatBindingsModule {

    @Binds
    @Singleton
    abstract fun bindCryptoService(impl: TinkHybridNoteCryptoService): CryptoService

    @Binds
    @Singleton
    abstract fun bindConnectivityObserver(impl: AndroidConnectivityObserver): ConnectivityObserver

    @Binds
    @Singleton
    abstract fun bindAuthService(impl: HybridAuthService): AuthService
}

@Module
@InstallIn(SingletonComponent::class)
object RelayStorageModule {

    @Provides
    @Singleton
    fun provideRelayStorage(
        @ApplicationContext context: Context,
        connectivityObserver: ConnectivityObserver
    ): RelayStorage {
        val relayDir = File(context.filesDir, "relay_waiting_room")
        return HybridFirebaseRelayStorage(
            localRelayDir = relayDir,
            isNetworkAvailableProvider = { connectivityObserver.isOnline.value }
        )
    }
}

