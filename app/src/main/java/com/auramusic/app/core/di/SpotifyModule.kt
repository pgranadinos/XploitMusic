package com.auramusic.app.core.di

import com.auramusic.app.data.local.SpotifyOAuthSessionStore
import com.auramusic.app.data.local.SpotifyConfigurationStore
import com.auramusic.app.core.network.SpotifyOAuthLoopbackServer
import com.auramusic.app.data.remote.SpotifyApi
import com.auramusic.app.data.remote.SpotifyAuthApi
import com.auramusic.app.data.local.dao.SpotifyTokenDao
import com.auramusic.app.data.repository.SpotifyMatchingEngine
import com.auramusic.app.data.repository.SpotifyRepositoryImpl
import com.auramusic.app.data.repository.SpotifyPlaylistPersister
import com.auramusic.app.domain.provider.ProviderGateway
import com.auramusic.app.domain.repository.SpotifyRepository
import com.auramusic.app.domain.usecase.ImportPlaylistUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SpotifyModule {

    @Provides
    @Singleton
    fun provideSpotifyRepository(
        api: SpotifyApi,
        authApi: SpotifyAuthApi,
        tokenDao: SpotifyTokenDao,
        oauthSessionStore: SpotifyOAuthSessionStore,
        configurationStore: SpotifyConfigurationStore,
        loopbackServer: SpotifyOAuthLoopbackServer
    ): SpotifyRepository {
        return SpotifyRepositoryImpl(
            api,
            authApi,
            tokenDao,
            oauthSessionStore,
            configurationStore,
            loopbackServer
        )
    }

    @Provides
    @Singleton
    fun provideSpotifyMatchingEngine(): SpotifyMatchingEngine {
        return SpotifyMatchingEngine()
    }

    @Provides
    @Singleton
    fun provideImportPlaylistUseCase(
        spotifyRepository: SpotifyRepository,
        gateway: ProviderGateway,
        matchingEngine: SpotifyMatchingEngine,
        persister: SpotifyPlaylistPersister
    ): ImportPlaylistUseCase {
        return ImportPlaylistUseCase(spotifyRepository, gateway, matchingEngine, persister)
    }
}
