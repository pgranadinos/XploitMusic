package com.xploitmusic.app.data.repository

import com.xploitmusic.app.data.local.dao.PlaylistDao
import com.xploitmusic.app.data.local.dao.SongDao
import com.xploitmusic.app.data.local.entity.PlaylistEntity
import com.xploitmusic.app.data.local.entity.PlaylistSongCrossRef
import com.xploitmusic.app.data.local.entity.PlaylistWithSongs
import com.xploitmusic.app.data.local.entity.SongEntity
import com.xploitmusic.app.domain.model.Song
import com.xploitmusic.app.domain.repository.PlaylistRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistRepositoryImpl @Inject constructor(
    private val playlistDao: PlaylistDao,
    private val songDao: SongDao
) : PlaylistRepository {

    override fun getAllPlaylists(): Flow<List<PlaylistEntity>> =
        playlistDao.getAllPlaylists()

    override fun getAllPlaylistsWithSongs(): Flow<List<PlaylistWithSongs>> =
        playlistDao.getAllPlaylistsWithSongs()

    override fun getPlaylistWithSongs(playlistId: Long): Flow<PlaylistWithSongs?> =
        playlistDao.getPlaylistWithSongs(playlistId)

    override suspend fun createPlaylist(name: String, source: String?, sourceId: String?): Long {
        return playlistDao.createPlaylist(
            PlaylistEntity(
                name = name,
                createdAt = System.currentTimeMillis(),
                source = source,
                sourceId = sourceId
            )
        )
    }

    override suspend fun deletePlaylist(playlistId: Long) {
        playlistDao.deletePlaylist(playlistId)
    }

    override suspend fun renamePlaylist(playlistId: Long, name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return false
        return playlistDao.renamePlaylist(playlistId, trimmed) == 1
    }

    override suspend fun addSongToPlaylist(playlistId: Long, song: Song) {
        songDao.upsertNetworkSong(
            SongEntity(
                id = song.id,
                title = song.title,
                artist = song.artist,
                durationSeconds = song.durationSeconds,
                thumbnailUrl = song.thumbnailUrl
            )
        )
        playlistDao.addSongToPlaylist(PlaylistSongCrossRef(playlistId = playlistId, songId = song.id))
    }

    override suspend fun removeSongFromPlaylist(playlistId: Long, songId: String) {
        playlistDao.removeSongFromPlaylist(playlistId, songId)
    }

    override suspend fun moveSongInPlaylist(playlistId: Long, fromPosition: Int, toPosition: Int) {
        playlistDao.moveSong(playlistId, fromPosition, toPosition)
    }
}
