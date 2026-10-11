package com.xploitmusic.app.core.di

import com.xploitmusic.app.data.repository.FavoritesRepositoryImpl
import com.xploitmusic.app.data.repository.LocalAudioRepositoryImpl
import com.xploitmusic.app.data.repository.PlayerRepositoryImpl
import com.xploitmusic.app.data.repository.QueueManagerImpl
import com.xploitmusic.app.data.repository.StreamRepositoryImpl
import com.xploitmusic.app.domain.repository.FavoritesRepository
import com.xploitmusic.app.domain.repository.LocalAudioRepository
import com.xploitmusic.app.domain.repository.PlayerRepository
import com.xploitmusic.app.domain.repository.QueueManager
import com.xploitmusic.app.domain.repository.StreamRepository
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
