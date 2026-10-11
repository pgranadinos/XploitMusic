package com.auramusic.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.auramusic.app.data.local.dao.DownloadDao
import com.auramusic.app.data.local.dao.PlaylistDao
import com.auramusic.app.data.local.dao.SearchHistoryDao
import com.auramusic.app.data.local.dao.SongDao
import com.auramusic.app.data.local.entity.DownloadedTrackEntity
import com.auramusic.app.data.local.entity.PlaylistEntity
import com.auramusic.app.data.local.entity.PlaylistSongCrossRef
import com.auramusic.app.data.local.entity.SearchHistoryEntity
import com.auramusic.app.data.local.entity.SongEntity
import com.auramusic.app.data.local.entity.PlayHistoryEntity
import com.auramusic.app.data.local.dao.PlayHistoryDao
import com.auramusic.app.data.local.dao.SpotifyTokenDao
import com.auramusic.app.data.local.entity.SpotifyTokenEntity

@Database(
    entities = [
        SongEntity::class,
        PlaylistEntity::class,
        PlaylistSongCrossRef::class,
        SearchHistoryEntity::class,
        DownloadedTrackEntity::class,
        PlayHistoryEntity::class,
        SpotifyTokenEntity::class
    ],
    version = 10,
    exportSchema = true
)
abstract class AuraDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun downloadDao(): DownloadDao
    abstract fun playHistoryDao(): PlayHistoryDao
    abstract fun spotifyTokenDao(): SpotifyTokenDao

    companion object {
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE songs ADD COLUMN playCount INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE playlist_song_cross_ref ADD COLUMN position INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `spotify_token` (" +
                            "`id` INTEGER NOT NULL, " +
                            "`access_token` TEXT NOT NULL, " +
                            "`refresh_token` TEXT NOT NULL, " +
                            "`expires_at` INTEGER NOT NULL, " +
                            "`spotify_user_id` TEXT NOT NULL, " +
                            "`display_name` TEXT NOT NULL, " +
                            "`avatar_url` TEXT NOT NULL, " +
                            "PRIMARY KEY(`id`))"
                )
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_songs_isFavorite` ON `songs` (`isFavorite`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_songs_lastPlayedAt` ON `songs` (`lastPlayedAt`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_song_cross_ref_songId` ON `playlist_song_cross_ref` (`songId`)")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE playlists ADD COLUMN source TEXT DEFAULT NULL")
                database.execSQL("ALTER TABLE playlists ADD COLUMN sourceId TEXT DEFAULT NULL")
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_playlists_source_sourceId` " +
                        "ON `playlists` (`source`, `sourceId`)"
                )
            }
        }
    }
}
