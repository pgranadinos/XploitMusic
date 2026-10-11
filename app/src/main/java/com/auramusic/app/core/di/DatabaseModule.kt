package com.auramusic.app.core.di

import android.content.ContentResolver
import android.content.Context
import androidx.room.Room
import com.auramusic.app.data.local.AuraDatabase
import com.auramusic.app.data.local.dao.DownloadDao
import com.auramusic.app.data.local.dao.PlaylistDao
import com.auramusic.app.data.local.dao.SearchHistoryDao
import com.auramusic.app.data.local.dao.SongDao
import com.auramusic.app.data.local.dao.PlayHistoryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AuraDatabase {
        return Room.databaseBuilder(
            context,
            AuraDatabase::class.java,
            "aura_music.db"
        ).addMigrations(
            AuraDatabase.MIGRATION_6_7,
            AuraDatabase.MIGRATION_7_8,
            AuraDatabase.MIGRATION_8_9,
            AuraDatabase.MIGRATION_9_10
        )
        .fallbackToDestructiveMigration()
        .build()
    }

    @Provides
    fun provideSongDao(database: AuraDatabase): SongDao {
        return database.songDao()
    }

    @Provides
    fun providePlaylistDao(database: AuraDatabase): PlaylistDao {
        return database.playlistDao()
    }

    @Provides
    fun provideSearchHistoryDao(database: AuraDatabase): SearchHistoryDao {
        return database.searchHistoryDao()
    }

    @Provides
    fun provideDownloadDao(database: AuraDatabase): DownloadDao {
        return database.downloadDao()
    }

    @Provides
    fun provideContentResolver(@ApplicationContext context: Context): ContentResolver {
        return context.contentResolver
    }

    @Provides
    fun providePlayHistoryDao(database: AuraDatabase): PlayHistoryDao {
        return database.playHistoryDao()
    }

    @Provides
    fun provideSpotifyTokenDao(database: AuraDatabase): com.auramusic.app.data.local.dao.SpotifyTokenDao {
        return database.spotifyTokenDao()
    }
}
