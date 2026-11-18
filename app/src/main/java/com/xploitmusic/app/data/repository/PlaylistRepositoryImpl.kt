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
import kotlinx.coroutines.flow.combine
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
        playlistDao.getAllPlaylistsWithSongs().combine(playlistDao.getAllCrossRefsOrdered()) { playlists, crossRefs ->
            val positionMap = crossRefs.groupBy { it.playlistId }
            playlists.map { pws ->
                val refs = positionMap[pws.playlist.playlistId]
                if (refs.isNullOrEmpty()) {
                    pws
                } else {
                    val orderMap = refs.associate { it.songId to it.position }
                    val sortedSongs = pws.songs.sortedBy { orderMap[it.id] ?: Int.MAX_VALUE }
                    pws.copy(songs = sortedSongs)
                }
            }
        }

    override fun getPlaylistWithSongs(playlistId: Long): Flow<PlaylistWithSongs?> =
        playlistDao.getPlaylistById(playlistId).combine(playlistDao.getPlaylistSongsOrdered(playlistId)) { playlist, songs ->
            if (playlist == null) null else PlaylistWithSongs(playlist = playlist, songs = songs)
        }

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
