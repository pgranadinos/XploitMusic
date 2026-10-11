package com.auramusic.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.auramusic.app.data.local.entity.PlaylistEntity
import com.auramusic.app.data.local.entity.PlaylistSongCrossRef
import com.auramusic.app.data.local.entity.PlaylistWithSongs
import com.auramusic.app.data.local.entity.SongEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {

    // --- Playlist CRUD ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun createPlaylist(playlist: PlaylistEntity): Long

    @Query("DELETE FROM playlists WHERE playlistId = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Query("UPDATE playlists SET name = :name WHERE playlistId = :playlistId")
    suspend fun renamePlaylist(playlistId: Long, name: String): Int

    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT playlistId FROM playlists WHERE source = :source AND sourceId = :sourceId LIMIT 1")
    suspend fun findPlaylistIdBySource(source: String, sourceId: String): Long?

    @Query("UPDATE playlists SET name = :name, coverUrl = :coverUrl WHERE playlistId = :playlistId")
    suspend fun updatePlaylistMetadata(playlistId: Long, name: String, coverUrl: String?)

    // --- Song ↔ Playlist ---

    @Query("SELECT MAX(position) FROM playlist_song_cross_ref WHERE playlistId = :playlistId")
    suspend fun getMaxPosition(playlistId: Long): Int?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlaylistSongCrossRef(crossRef: PlaylistSongCrossRef)

    @Query("DELETE FROM playlist_song_cross_ref WHERE playlistId = :playlistId")
    suspend fun clearPlaylistSongs(playlistId: Long)

    @Transaction
    suspend fun addSongToPlaylist(crossRef: PlaylistSongCrossRef) {
        val currentMax = getMaxPosition(crossRef.playlistId) ?: -1
        val finalCrossRef = crossRef.copy(position = currentMax + 1)
        insertPlaylistSongCrossRef(finalCrossRef)
    }

    @Query("SELECT * FROM playlist_song_cross_ref WHERE playlistId = :playlistId ORDER BY position ASC")
    suspend fun getPlaylistCrossRefs(playlistId: Long): List<PlaylistSongCrossRef>

    @Update
    suspend fun updatePlaylistCrossRefs(crossRefs: List<PlaylistSongCrossRef>)

    @Transaction
    suspend fun moveSong(playlistId: Long, fromPosition: Int, toPosition: Int) {
        val currentList = getPlaylistCrossRefs(playlistId).toMutableList()
        if (fromPosition !in currentList.indices || toPosition !in currentList.indices) return
        
        val item = currentList.removeAt(fromPosition)
        currentList.add(toPosition, item)
        
        val updatedList = currentList.mapIndexed { index, ref ->
            ref.copy(position = index)
        }
        updatePlaylistCrossRefs(updatedList)
    }

    @Query("DELETE FROM playlist_song_cross_ref WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removeSongFromPlaylist(playlistId: Long, songId: String)

    // --- Relations ---

    // C6: Corrección Estricta: `@Relation` de Kotlin elimina el ordenamiento custom impuesto
    // por la columna "position". Esta query estricta hace un join resolviendo matemáticamente
    // la estructura de UI O(1) en la capa SQLite.
    @Query("""
        SELECT songs.* FROM songs 
        INNER JOIN playlist_song_cross_ref ref ON songs.id = ref.songId 
        WHERE ref.playlistId = :playlistId 
        ORDER BY ref.position ASC
    """)
    fun getPlaylistSongsOrdered(playlistId: Long): Flow<List<SongEntity>>

    @Transaction
    @Query("SELECT * FROM playlists WHERE playlistId = :playlistId")
    fun getPlaylistWithSongs(playlistId: Long): Flow<PlaylistWithSongs?>

    @Transaction
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun getAllPlaylistsWithSongs(): Flow<List<PlaylistWithSongs>>
}
