package com.auramusic.app.core.di

import android.content.ContentResolver
import android.content.Context
import androidx.room.Room
import com.auramusic.app.data.local.AuraDatabase
import com.auramusic.app.data.local.dao.PlaylistDao
import com.auramusic.app.data.local.dao.SongDao
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
        ).fallbackToDestructiveMigration().build()
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
    @Singleton
    fun provideContentResolver(@ApplicationContext context: Context): ContentResolver {
        return context.contentResolver
    }
}
