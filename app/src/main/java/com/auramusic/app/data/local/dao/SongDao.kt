package com.auramusic.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.auramusic.app.data.local.entity.SongEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {

    // --- Favorites ---

    @Query("SELECT * FROM songs WHERE isFavorite = 1 ORDER BY likedAt DESC")
    fun getFavorites(): Flow<List<SongEntity>>

    @Query("SELECT isFavorite FROM songs WHERE id = :songId")
    fun isFavorite(songId: String): Flow<Boolean?>

    @Query("SELECT COUNT(*) FROM songs WHERE isFavorite = 1")
    fun getFavoriteCount(): Flow<Int>

    @Query("SELECT DISTINCT artist FROM songs WHERE isFavorite = 1 ORDER BY likedAt DESC")
    fun getDistinctFavoriteArtists(): Flow<List<String>>

    // --- Recents & Analytics ---

    @Query("SELECT * FROM songs ORDER BY lastPlayedAt DESC LIMIT 20")
    fun getRecents(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE playCount > 0 ORDER BY playCount DESC LIMIT 20")
    fun getMostPlayed(): Flow<List<SongEntity>>

    // --- Mutations ---

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSong(song: SongEntity)

    @Update
    suspend fun updateSong(song: SongEntity)

    @Transaction
    suspend fun upsertNetworkSong(song: SongEntity) {
        val existing = getSongById(song.id)
        if (existing != null) {
            val updated = existing.copy(
                title = song.title,
                artist = song.artist,
                thumbnailUrl = song.thumbnailUrl,
                durationSeconds = song.durationSeconds,
                streamUrl = song.streamUrl ?: existing.streamUrl,
                genre = song.genre ?: existing.genre
            )
            updateSong(updated)
        } else {
            insertSong(song)
        }
    }

    @Query("DELETE FROM songs WHERE id = :songId")
    suspend fun deleteSong(songId: String)

    @Query("UPDATE songs SET isFavorite = :isFavorite, likedAt = :likedAt WHERE id = :songId")
    suspend fun setFavorite(songId: String, isFavorite: Boolean, likedAt: Long?)

    @Query("UPDATE songs SET playCount = playCount + 1, lastPlayedAt = :timestamp WHERE id = :songId")
    suspend fun recordPlay(songId: String, timestamp: Long)

    @Query("SELECT * FROM songs WHERE id = :songId")
    suspend fun getSongById(songId: String): SongEntity?
}
