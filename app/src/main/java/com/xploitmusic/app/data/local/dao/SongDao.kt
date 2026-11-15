package com.xploitmusic.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.xploitmusic.app.data.local.entity.SongEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {

    // --- Favorites ---

    @Query("SELECT * FROM songs WHERE isFavorite = 1 ORDER BY favoritePosition ASC")
    fun getFavorites(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE isFavorite = 1 ORDER BY favoritePosition ASC")
    suspend fun getFavoritesList(): List<SongEntity>

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

    @Update
    suspend fun updateSongs(songs: List<SongEntity>)

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

    @Query("""
        UPDATE songs 
        SET isFavorite = :isFavorite, 
            likedAt = :likedAt,
            favoritePosition = CASE WHEN :isFavorite = 1 THEN (SELECT COALESCE(MIN(favoritePosition), 0) - 1 FROM songs WHERE isFavorite = 1) ELSE favoritePosition END 
        WHERE id = :songId
    """)
    suspend fun setFavorite(songId: String, isFavorite: Boolean, likedAt: Long?)

    @Transaction
    suspend fun moveFavorite(fromPosition: Int, toPosition: Int) {
        val currentList = getFavoritesList().toMutableList()
        if (fromPosition !in currentList.indices || toPosition !in currentList.indices) return

        val item = currentList.removeAt(fromPosition)
        currentList.add(toPosition, item)

        val updatedList = currentList.mapIndexed { index, song ->
            song.copy(favoritePosition = index)
        }
        updateSongs(updatedList)
    }

    @Query("UPDATE songs SET playCount = playCount + 1, lastPlayedAt = :timestamp WHERE id = :songId")
    suspend fun recordPlay(songId: String, timestamp: Long)

    @Query("SELECT * FROM songs WHERE id = :songId")
    suspend fun getSongById(songId: String): SongEntity?
}
