package com.xploitmusic.app.data.repository

import com.xploitmusic.app.core.util.Resource
import com.xploitmusic.app.data.dto.LrcLibResponse
import com.xploitmusic.app.data.remote.LrcLibApi
import com.xploitmusic.app.domain.model.Lyrics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.text.Normalizer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LyricsRepositoryImpl @Inject constructor(
    private val api: LrcLibApi
) {
    suspend fun getLyrics(trackName: String, artistName: String, duration: Double): Resource<Lyrics> {
        val cleanArtist = cleanArtist(artistName)
        val cleanTrack = cleanTrack(trackName, artistName, cleanArtist)

        return try {
            val response = withTimeout(TOTAL_LOOKUP_TIMEOUT_MS) {
                exactLookup(cleanTrack, cleanArtist, duration)
                    ?: progressiveSearch(cleanTrack, cleanArtist, duration)
            }

            if (response == null) {
                Resource.Error("No se encontraron letras para esta canción")
            } else {
                Resource.Success(response.toDomain())
            }
        } catch (_: TimeoutCancellationException) {
            Resource.Error("Tiempo de espera agotado al buscar la letra")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Resource.Error("No se pudieron cargar las letras: ${e.message}")
        }
    }

    private suspend fun exactLookup(
        trackName: String,
        artistName: String,
        duration: Double
    ): LrcLibResponse? = try {
        api.getLyrics(trackName, artistName, duration.takeIf { it > 0.0 })
            .takeIf(::hasUsableLyrics)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    private suspend fun progressiveSearch(
        trackName: String,
        artistName: String,
        duration: Double
    ): LrcLibResponse? {
        val queries = buildList {
            if (artistName.isNotBlank()) add("$trackName $artistName")
            add(trackName)
        }.map(String::trim).filter(String::isNotBlank).distinct()

        var lastFailure: Exception? = null
        var completedRequest = false
        for (query in queries) {
            val candidates = try {
                api.searchLyrics(query).also { completedRequest = true }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastFailure = e
                continue
            }

            candidates.bestMatch(trackName, artistName, duration)?.let { return it }
        }
        if (!completedRequest && lastFailure != null) throw lastFailure
        return null
    }

    private fun List<LrcLibResponse>.bestMatch(
        trackName: String,
        artistName: String,
        duration: Double
    ): LrcLibResponse? =
        asSequence()
            .filter(::hasUsableLyrics)
            .map { it to matchScore(it, trackName, artistName, duration) }
            .filter { (_, score) -> score >= MIN_MATCH_SCORE }
            .maxByOrNull { (_, score) -> score }
            ?.first

    private fun LrcLibResponse.toDomain() = Lyrics(
        id = id,
        trackName = trackName,
        artistName = artistName,
        albumName = albumName,
        duration = duration,
        instrumental = instrumental,
        plainLyrics = plainLyrics,
        syncedLyrics = syncedLyrics
    )

    private fun hasUsableLyrics(item: LrcLibResponse): Boolean =
        item.instrumental || !item.syncedLyrics.isNullOrBlank() || !item.plainLyrics.isNullOrBlank()

    private fun matchScore(
        item: LrcLibResponse,
        trackName: String,
        artistName: String,
        duration: Double
    ): Int {
        val wantedTrack = normalize(trackName)
        val candidateTrack = normalize(item.trackName)
        val wantedArtist = normalize(artistName)
        val candidateArtist = normalize(item.artistName)

        val titleScore = when {
            wantedTrack == candidateTrack -> 6
            wantedTrack in candidateTrack || candidateTrack in wantedTrack -> 3
            else -> 0
        }
        val artistScore = when {
            wantedArtist.isBlank() -> 0
            wantedArtist == candidateArtist -> 4
            wantedArtist in candidateArtist || candidateArtist in wantedArtist -> 2
            else -> 0
        }
        val durationDifference = kotlin.math.abs(item.duration - duration)
        val durationScore = when {
            duration <= 0.0 -> 0
            durationDifference <= 3.0 -> 3
            durationDifference <= 10.0 -> 1
            else -> 0
        }
        return titleScore + artistScore + durationScore
    }

    private fun cleanArtist(value: String): String = value
        .replace(Regex("(?i)\\s*[-–—]\\s*topic\\s*$"), "")
        .replace(Regex("(?i)\\s*vevo\\s*$"), "")
        .replace(Regex("(?i)^\\s*(official|oficial)\\s+"), "")
        .replace(Regex("(?i)\\s+(official|oficial)(\\s+(music|artist|channel))?\\s*$"), "")
        .trim()

    private fun cleanTrack(value: String, originalArtist: String, cleanArtist: String): String {
        val withoutArtistPrefix = listOf(originalArtist, cleanArtist)
            .filter(String::isNotBlank)
            .distinct()
            .fold(value) { title, artist ->
                title.replace(Regex("(?i)^\\s*${Regex.escape(artist)}\\s*[-–—:]\\s*"), "")
            }
        return withoutArtistPrefix
            .replace(Regex("(?i)\\([^)]*(official|video|audio|lyrics?|letra|visualizer)[^)]*\\)"), " ")
            .replace(Regex("(?i)\\[[^]]*(official|video|audio|lyrics?|letra|visualizer)[^]]*]"), " ")
            .replace(Regex("(?i)\\s+(official\\s+(music\\s+)?(video|audio)|lyrics?(\\s+video)?)\\s*$"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun normalize(value: String): String = value
        .let { Normalizer.normalize(it, Normalizer.Form.NFD) }
        .replace(Regex("\\p{M}+"), "")
        .lowercase()
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()

    private companion object {
        const val MIN_MATCH_SCORE = 7
        const val TOTAL_LOOKUP_TIMEOUT_MS = 12_000L
    }
}
