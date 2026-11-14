package com.tabletnotes.app.di

import com.tabletnotes.app.feature.nearby.service.GoogleNearbyConnectionsManager
import com.tabletnotes.app.feature.nearby.service.NearbyTransferManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NearbyModule {

    @Binds
    @Singleton
    abstract fun bindNearbyTransferManager(
        impl: GoogleNearbyConnectionsManager
    ): NearbyTransferManager
}
