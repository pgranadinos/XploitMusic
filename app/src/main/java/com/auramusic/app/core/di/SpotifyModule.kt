package com.auramusic.app.core.di

import com.auramusic.app.data.local.dao.PlaylistDao
import com.auramusic.app.data.local.dao.SongDao
import com.auramusic.app.data.remote.SpotifyApi
import com.auramusic.app.data.remote.SpotifyAuthApi
import com.auramusic.app.data.local.dao.SpotifyTokenDao
import com.auramusic.app.data.repository.SpotifyMatchingEngine
import com.auramusic.app.data.repository.SpotifyRepositoryImpl
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
        tokenDao: SpotifyTokenDao
    ): SpotifyRepository {
        return SpotifyRepositoryImpl(api, authApi, tokenDao)
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
        playlistDao: PlaylistDao,
        songDao: SongDao
    ): ImportPlaylistUseCase {
        return ImportPlaylistUseCase(spotifyRepository, gateway, matchingEngine, playlistDao, songDao)
    }
}
