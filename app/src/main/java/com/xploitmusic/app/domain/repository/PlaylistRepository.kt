package com.xploitmusic.app.domain.repository

import com.xploitmusic.app.data.local.entity.PlaylistEntity
import com.xploitmusic.app.data.local.entity.PlaylistWithSongs
import com.xploitmusic.app.domain.model.Song
import kotlinx.coroutines.flow.Flow

interface PlaylistRepository {
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>
    fun getAllPlaylistsWithSongs(): Flow<List<PlaylistWithSongs>>
    fun getPlaylistWithSongs(playlistId: Long): Flow<PlaylistWithSongs?>
    suspend fun createPlaylist(name: String, source: String? = null, sourceId: String? = null): Long
    suspend fun deletePlaylist(playlistId: Long)
    suspend fun renamePlaylist(playlistId: Long, name: String): Boolean
    suspend fun addSongToPlaylist(playlistId: Long, song: Song)
    suspend fun removeSongFromPlaylist(playlistId: Long, songId: String)
    suspend fun moveSongInPlaylist(playlistId: Long, fromPosition: Int, toPosition: Int)
}
