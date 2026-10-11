package com.auramusic.app.data.provider.impl

import com.auramusic.app.data.provider.util.executeWithRetry
import com.auramusic.app.data.remote.AuraBffApi
import com.auramusic.app.domain.model.Song
import com.auramusic.app.domain.provider.MultimediaProvider
import com.auramusic.app.domain.provider.ProviderError
import javax.inject.Inject

class BffProvider @Inject constructor(
    private val api: AuraBffApi
) : MultimediaProvider {

    override val providerId: String = "AURA_BFF"

    override suspend fun search(query: String): Result<List<Song>> {
        // Timeout reducido: el BFF es un backup opcional. Si no responde pronto, usamos Piped.
        return executeWithRetry(providerId, timeoutMs = 3000L) {
            val response = api.search(query = query, type = "tracks")
            response.results.map {
                Song(
                    id = it.id,
                    title = it.title,
                    artist = it.artist ?: "Unknown",
                    thumbnailUrl = it.thumbnailUrl,
                    durationSeconds = it.durationSeconds?.toLong() ?: 0L
                )
            }
        }
    }

    override suspend fun getTrack(videoId: String): Result<Song> {
        return executeWithRetry(providerId) {
            // Buscamos directamente el track para extraer metadata
            val response = api.search(query = videoId, type = "tracks")
            val item = response.results.find { it.id == videoId } ?: throw ProviderError.NotFound(providerId)
            Song(
                id = item.id,
                title = item.title,
                artist = item.artist ?: "Unknown",
                thumbnailUrl = item.thumbnailUrl,
                durationSeconds = item.durationSeconds?.toLong() ?: 0L
            )
        }
    }

    override suspend fun getStream(videoId: String): Result<String> {
        // Local yt-dlp extraction is slower than a public API on a cold request.
        return executeWithRetry(providerId, timeoutMs = 30_000L, maxRetries = 0) {
            val response = api.getStream(videoId)
            // FIX: validar que streamUrl no sea null/vacío (BFF puede omitirlo en error)
            val url = response.streamUrl
            when {
                url.isNullOrBlank() ->
                    throw ProviderError.NotFound(providerId)
                // FIX: rechazar URLs cleartext que burlarían el NSC en dispositivos con API < 28
                url.startsWith("http://") ->
                    throw ProviderError.FatalError(
                        providerId,
                        "Stream URL insegura (cleartext HTTP) devuelta por el BFF para $videoId"
                    )
                else -> url
            }
        }
    }

    override suspend fun getRelated(videoId: String): Result<List<Song>> {
        return executeWithRetry(providerId) {
            val response = api.getRelatedTracks(trackId = videoId)
            response.items.map {
                Song(
                    id = it.id,
                    title = it.title,
                    artist = it.artist,
                    thumbnailUrl = it.thumbnailUrl,
                    durationSeconds = it.durationSeconds.toLong()
                )
            }
        }
    }

    override suspend fun getTrending(region: String): Result<List<Song>> {
        return executeWithRetry(providerId, timeoutMs = 3000L) {
            val response = api.getHomeFeed()
            val trendingCarousel = response.carousels.find { it.type == "trending" }
                ?: throw ProviderError.NotFound(providerId)
            val songs = trendingCarousel.items.map {
                Song(
                    id = it.id,
                    title = it.title,
                    artist = it.artist,
                    thumbnailUrl = it.thumbnailUrl,
                    durationSeconds = it.durationSeconds.toLong()
                )
            }
            if (songs.isEmpty()) throw ProviderError.NotFound(providerId)
            songs
        }
    }
}
