package com.xploitmusic.app.core.di

import android.content.ContentResolver
import android.content.Context
import androidx.room.Room
import com.xploitmusic.app.data.local.XploitDatabase
import com.xploitmusic.app.data.local.dao.DownloadDao
import com.xploitmusic.app.data.local.dao.PlaylistDao
import com.xploitmusic.app.data.local.dao.SearchHistoryDao
import com.xploitmusic.app.data.local.dao.SongDao
import com.xploitmusic.app.data.local.dao.PlayHistoryDao
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
    fun provideDatabase(@ApplicationContext context: Context): XploitDatabase {
        val builder = Room.databaseBuilder(
            context,
            XploitDatabase::class.java,
            "xploit_music.db"
        ).addMigrations(
            XploitDatabase.MIGRATION_6_7,
            XploitDatabase.MIGRATION_7_8,
            XploitDatabase.MIGRATION_8_9,
            XploitDatabase.MIGRATION_9_10
        )

        if (com.xploitmusic.app.BuildConfig.DEBUG) {
            builder.fallbackToDestructiveMigration()
        }

        return builder.build()
    }

    @Provides
    fun provideSongDao(database: XploitDatabase): SongDao {
        return database.songDao()
    }

    @Provides
    fun providePlaylistDao(database: XploitDatabase): PlaylistDao {
        return database.playlistDao()
    }

    @Provides
    fun provideSearchHistoryDao(database: XploitDatabase): SearchHistoryDao {
        return database.searchHistoryDao()
    }

    @Provides
    fun provideDownloadDao(database: XploitDatabase): DownloadDao {
        return database.downloadDao()
    }

    @Provides
    fun provideContentResolver(@ApplicationContext context: Context): ContentResolver {
        return context.contentResolver
    }

    @Provides
    fun providePlayHistoryDao(database: XploitDatabase): PlayHistoryDao {
        return database.playHistoryDao()
    }

    @Provides
    fun provideSpotifyTokenDao(database: XploitDatabase): com.xploitmusic.app.data.local.dao.SpotifyTokenDao {
        return database.spotifyTokenDao()
    }
}
