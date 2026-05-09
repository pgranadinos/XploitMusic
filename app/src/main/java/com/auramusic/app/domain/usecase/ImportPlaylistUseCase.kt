package com.auramusic.app.domain.usecase

import android.util.Log
import com.auramusic.app.data.local.dao.PlaylistDao
import com.auramusic.app.data.local.dao.SongDao
import com.auramusic.app.data.local.entity.PlaylistEntity
import com.auramusic.app.data.local.entity.PlaylistSongCrossRef
import com.auramusic.app.data.local.entity.SongEntity
import com.auramusic.app.data.remote.SpotifyPlaylistDto
import com.auramusic.app.data.remote.SpotifyTrackDto
import com.auramusic.app.data.repository.SpotifyMatchingEngine
import com.auramusic.app.data.repository.SpotifyMatchingEngine.MatchType
import com.auramusic.app.domain.model.Song
import com.auramusic.app.domain.provider.ProviderGateway
import com.auramusic.app.domain.repository.SpotifyRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

// ─── Progreso de importación ──────────────────────────────────────────────────

sealed class TrackImportStatus {
    data class Matched(
        val spotifyTrack: SpotifyTrackDto,
        val song: Song,
        val matchType: MatchType,
        val score: Float
    ) : TrackImportStatus()

    data class NoMatch(val spotifyTrack: SpotifyTrackDto) : TrackImportStatus()
    data class Failed(val spotifyTrack: SpotifyTrackDto, val error: String) : TrackImportStatus()
}

data class ImportProgress(
    val playlist: SpotifyPlaylistDto,
    val totalTracks: Int,
    val processedTracks: Int,
    val latestStatus: TrackImportStatus?,
    val isComplete: Boolean = false,
    /** ID de Room de la playlist creada, disponible solo cuando isComplete=true */
    val createdPlaylistId: Long? = null
)

// ─── Use Case ─────────────────────────────────────────────────────────────────

class ImportPlaylistUseCase @Inject constructor(
    private val spotifyRepository: SpotifyRepository,
    private val gateway: ProviderGateway,
    private val matchingEngine: SpotifyMatchingEngine,
    private val playlistDao: PlaylistDao,
    private val songDao: SongDao
) {
    companion object {
        private const val TAG = "ImportPlaylistUseCase"
        private const val MAX_CANDIDATES = 8
    }

    fun execute(playlist: SpotifyPlaylistDto): Flow<ImportProgress> = flow {
        val tracksResult = spotifyRepository.getAllPlaylistTracks(playlist.id)
        val tracks = tracksResult.getOrElse {
            Log.e(TAG, "Error fetching tracks: ${it.message}")
            emit(ImportProgress(playlist, 0, 0, null, isComplete = true))
            return@flow
        }

        val total = tracks.size
        val matchedSongs = mutableListOf<Song>()
        var processed = 0

        for (track in tracks) {
            val status: TrackImportStatus = try {
                val candidates = gateway.search(buildSearchQuery(track))
                    .getOrNull()?.take(MAX_CANDIDATES) ?: emptyList()

                val result = matchingEngine.findBestMatch(track, candidates)

                if (result.matchType != MatchType.NO_MATCH && result.candidate != null) {
                    matchedSongs.add(result.candidate)
                    TrackImportStatus.Matched(track, result.candidate, result.matchType, result.score)
                } else {
                    TrackImportStatus.NoMatch(track)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Match failed for '${track.name}': ${e.message}")
                TrackImportStatus.Failed(track, e.message ?: "Error desconocido")
            }

            processed++
            emit(ImportProgress(playlist, total, processed, status))
        }

        // Persistir en Room
        val createdId = persistToRoom(playlist, matchedSongs)
        emit(ImportProgress(playlist, total, processed, null, isComplete = true, createdPlaylistId = createdId))
        Log.d(TAG, "Import complete: ${matchedSongs.size}/$total songs matched")
    }

    private fun buildSearchQuery(track: SpotifyTrackDto): String {
        val artist = track.artists.firstOrNull()?.name ?: ""
        val cleanTitle = track.name
            .replace(Regex("""\s*\(feat\..*?\)""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*\(ft\..*?\)""", RegexOption.IGNORE_CASE), "")
            .trim()
        return "$cleanTitle $artist"
    }

    private suspend fun persistToRoom(playlist: SpotifyPlaylistDto, songs: List<Song>): Long? {
        return try {
            val playlistId = playlistDao.createPlaylist(
                PlaylistEntity(
                    name     = "\uD83C\uDFB5 ${playlist.name}",
                    coverUrl = playlist.images?.firstOrNull()?.url
                )
            )

            songs.forEachIndexed { index, song ->
                songDao.upsertNetworkSong(
                    SongEntity(
                        id              = song.id,
                        title           = song.title,
                        artist          = song.artist,
                        thumbnailUrl    = song.thumbnailUrl,
                        durationSeconds = song.durationSeconds
                    )
                )
                playlistDao.insertPlaylistSongCrossRef(
                    PlaylistSongCrossRef(
                        playlistId = playlistId,
                        songId     = song.id,
                        position   = index
                    )
                )
            }
            Log.d(TAG, "Persisted '${playlist.name}' (id=$playlistId) with ${songs.size} songs")
            playlistId
        } catch (e: Exception) {
            Log.e(TAG, "Error persisting playlist: ${e.message}", e)
            null
        }
    }
}
