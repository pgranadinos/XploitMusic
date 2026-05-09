package com.auramusic.app.core.di

import com.auramusic.app.data.repository.FavoritesRepositoryImpl
import com.auramusic.app.data.repository.LocalAudioRepositoryImpl
import com.auramusic.app.data.repository.PlayerRepositoryImpl
import com.auramusic.app.data.repository.QueueManagerImpl
import com.auramusic.app.data.repository.StreamRepositoryImpl
import com.auramusic.app.domain.repository.FavoritesRepository
import com.auramusic.app.domain.repository.LocalAudioRepository
import com.auramusic.app.domain.repository.PlayerRepository
import com.auramusic.app.domain.repository.QueueManager
import com.auramusic.app.domain.repository.StreamRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds
    @Singleton
    abstract fun bindPlayerRepository(impl: PlayerRepositoryImpl): PlayerRepository

    @Binds
    @Singleton
    abstract fun bindStreamRepository(impl: StreamRepositoryImpl): StreamRepository

    @Binds
    @Singleton
    abstract fun bindFavoritesRepository(impl: FavoritesRepositoryImpl): FavoritesRepository

    @Binds
    @Singleton
    abstract fun bindLocalAudioRepository(impl: LocalAudioRepositoryImpl): LocalAudioRepository

    @Binds
    @Singleton
    abstract fun bindQueueManager(impl: QueueManagerImpl): QueueManager
}

