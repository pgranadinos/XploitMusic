package com.auramusic.app.domain.usecase

import android.util.Log
import com.auramusic.app.data.remote.SpotifyPlaylistDto
import com.auramusic.app.data.remote.SpotifyTrackDto
import com.auramusic.app.data.repository.SpotifyMatchingEngine
import com.auramusic.app.data.repository.SpotifyMatchingEngine.MatchType
import com.auramusic.app.data.repository.SpotifyPlaylistPersister
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
    data class LowConfidence(
        val spotifyTrack: SpotifyTrackDto,
        val candidate: Song,
        val score: Float
    ) : TrackImportStatus()
    data class Failed(val spotifyTrack: SpotifyTrackDto, val error: String) : TrackImportStatus()
}

data class ImportProgress(
    val playlist: SpotifyPlaylistDto,
    val totalTracks: Int,
    val processedTracks: Int,
    val latestStatus: TrackImportStatus?,
    val isComplete: Boolean = false,
    /** ID de Room de la playlist creada, disponible solo cuando isComplete=true */
    val createdPlaylistId: Long? = null,
    val matchedTracks: Int = 0,
    val skippedTracks: Int = 0,
    val failedTracks: Int = 0,
    val errorMessage: String? = null
)

// ─── Use Case ─────────────────────────────────────────────────────────────────

class ImportPlaylistUseCase @Inject constructor(
    private val spotifyRepository: SpotifyRepository,
    private val gateway: ProviderGateway,
    private val matchingEngine: SpotifyMatchingEngine,
    private val persister: SpotifyPlaylistPersister
) {
    companion object {
        private const val TAG = "ImportPlaylistUseCase"
        private const val MAX_CANDIDATES = 8
    }

    fun execute(playlist: SpotifyPlaylistDto): Flow<ImportProgress> = flow {
        val tracksResult = spotifyRepository.getAllPlaylistTracks(playlist.id)
        val tracks = tracksResult.getOrElse {
            Log.e(TAG, "Error fetching tracks: ${it.message}")
            emit(
                ImportProgress(
                    playlist = playlist,
                    totalTracks = 0,
                    processedTracks = 0,
                    latestStatus = null,
                    errorMessage = "No se pudieron obtener las canciones: ${it.message ?: "error de Spotify"}"
                )
            )
            return@flow
        }

        val total = tracks.size
        val matchedSongs = mutableListOf<Song>()
        var processed = 0
        var matched = 0
        var skipped = 0
        var failed = 0

        for (track in tracks) {
            val status: TrackImportStatus = try {
                val candidates = gateway.search(buildSearchQuery(track))
                    .getOrThrow()
                    .take(MAX_CANDIDATES)

                val result = matchingEngine.findBestMatch(track, candidates)

                when {
                    result.candidate == null || result.matchType == MatchType.NO_MATCH -> {
                        skipped++
                        TrackImportStatus.NoMatch(track)
                    }
                    result.matchType == MatchType.LOW_CONFIDENCE -> {
                        skipped++
                        TrackImportStatus.LowConfidence(track, result.candidate, result.score)
                    }
                    else -> {
                        matchedSongs.add(result.candidate)
                        matched++
                        TrackImportStatus.Matched(track, result.candidate, result.matchType, result.score)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Match failed for '${track.name}': ${e.message}")
                failed++
                TrackImportStatus.Failed(track, e.message ?: "Error desconocido")
            }

            processed++
            emit(
                ImportProgress(
                    playlist = playlist,
                    totalTracks = total,
                    processedTracks = processed,
                    latestStatus = status,
                    matchedTracks = matched,
                    skippedTracks = skipped,
                    failedTracks = failed
                )
            )
        }

        if (matchedSongs.isEmpty() && failed > 0) {
            emit(
                ImportProgress(
                    playlist = playlist,
                    totalTracks = total,
                    processedTracks = processed,
                    latestStatus = null,
                    matchedTracks = matched,
                    skippedTracks = skipped,
                    failedTracks = failed,
                    errorMessage = "No se pudo completar la búsqueda de canciones. Revisa la conexión e inténtalo nuevamente."
                )
            )
            return@flow
        }

        val createdId = try {
            persister.persist(playlist, matchedSongs)
        } catch (e: Exception) {
            Log.e(TAG, "Error persisting playlist: ${e.message}", e)
            emit(
                ImportProgress(
                    playlist = playlist,
                    totalTracks = total,
                    processedTracks = processed,
                    latestStatus = null,
                    matchedTracks = matched,
                    skippedTracks = skipped,
                    failedTracks = failed,
                    errorMessage = "No se pudo guardar la playlist: ${e.message ?: "error local"}"
                )
            )
            return@flow
        }
        emit(
            ImportProgress(
                playlist = playlist,
                totalTracks = total,
                processedTracks = processed,
                latestStatus = null,
                isComplete = true,
                createdPlaylistId = createdId,
                matchedTracks = matched,
                skippedTracks = skipped,
                failedTracks = failed
            )
        )
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

}
