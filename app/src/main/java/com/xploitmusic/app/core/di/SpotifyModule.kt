package com.xploitmusic.app.core.di

import com.xploitmusic.app.data.local.SpotifyOAuthSessionStore
import com.xploitmusic.app.data.local.SpotifyConfigurationStore
import com.xploitmusic.app.data.remote.SpotifyApi
import com.xploitmusic.app.data.remote.SpotifyAuthApi
import com.xploitmusic.app.data.local.dao.SpotifyTokenDao
import com.xploitmusic.app.data.repository.SpotifyMatchingEngine
import com.xploitmusic.app.data.repository.SpotifyRepositoryImpl
import com.xploitmusic.app.data.repository.SpotifyPlaylistPersister
import com.xploitmusic.app.domain.provider.ProviderGateway
import com.xploitmusic.app.domain.repository.SpotifyRepository
import com.xploitmusic.app.domain.usecase.ImportPlaylistUseCase
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
        configurationStore: SpotifyConfigurationStore
    ): SpotifyRepository {
        return SpotifyRepositoryImpl(
            api,
            authApi,
            tokenDao,
            oauthSessionStore,
            configurationStore
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
