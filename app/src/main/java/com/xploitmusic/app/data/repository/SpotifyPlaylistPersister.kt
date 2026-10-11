package com.xploitmusic.app.data.repository

import androidx.room.withTransaction
import com.xploitmusic.app.data.local.XploitDatabase
import com.xploitmusic.app.data.local.dao.PlaylistDao
import com.xploitmusic.app.data.local.dao.SongDao
import com.xploitmusic.app.data.local.entity.PlaylistEntity
import com.xploitmusic.app.data.local.entity.PlaylistSongCrossRef
import com.xploitmusic.app.data.local.entity.SongEntity
import com.xploitmusic.app.data.remote.SpotifyPlaylistDto
import com.xploitmusic.app.domain.model.Song
import javax.inject.Inject

class SpotifyPlaylistPersister @Inject constructor(
    private val database: XploitDatabase,
    private val playlistDao: PlaylistDao,
    private val songDao: SongDao
) {
    suspend fun persist(playlist: SpotifyPlaylistDto, songs: List<Song>): Long =
        database.withTransaction {
            val name = "🎵 ${playlist.name}"
            val coverUrl = playlist.images?.firstOrNull()?.url
            val existingId = playlistDao.findPlaylistIdBySource(SOURCE, playlist.id)
            val playlistId = existingId ?: playlistDao.createPlaylist(
                PlaylistEntity(
                    name = name,
                    coverUrl = coverUrl,
                    source = SOURCE,
                    sourceId = playlist.id
                )
            )

            if (existingId != null) {
                playlistDao.updatePlaylistMetadata(playlistId, name, coverUrl)
                playlistDao.clearPlaylistSongs(playlistId)
            }

            songs.distinctBy { it.id }.forEachIndexed { index, song ->
                songDao.upsertNetworkSong(
                    SongEntity(
                        id = song.id,
                        title = song.title,
                        artist = song.artist,
                        thumbnailUrl = song.thumbnailUrl,
                        durationSeconds = song.durationSeconds
                    )
                )
                playlistDao.insertPlaylistSongCrossRef(
                    PlaylistSongCrossRef(
                        playlistId = playlistId,
                        songId = song.id,
                        position = index
                    )
                )
            }
            playlistId
        }

    private companion object {
        const val SOURCE = "spotify"
    }
}
