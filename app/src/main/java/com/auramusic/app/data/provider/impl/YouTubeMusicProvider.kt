package com.auramusic.app.data.provider.impl

import com.auramusic.app.data.provider.util.executeWithRetry
import com.auramusic.app.data.provider.youtubemusic.YouTubeMusicClient
import com.auramusic.app.domain.model.Song
import com.auramusic.app.domain.provider.MultimediaProvider
import com.auramusic.app.domain.provider.ProviderError
import javax.inject.Inject

class YouTubeMusicProvider @Inject constructor(
    private val client: YouTubeMusicClient,
) : MultimediaProvider {
    override val providerId: String = "YOUTUBE_MUSIC"

    override suspend fun search(query: String): Result<List<Song>> =
        executeWithRetry(providerId, timeoutMs = 15_000L, maxRetries = 1) {
            client.searchSongs(query)
        }

    override suspend fun getTrack(videoId: String): Result<Song> =
        executeWithRetry(providerId, timeoutMs = 15_000L, maxRetries = 1) {
            client.getTrack(videoId)
        }

    override suspend fun getRelated(videoId: String): Result<List<Song>> =
        executeWithRetry(providerId, timeoutMs = 15_000L, maxRetries = 1) {
            client.getRelated(videoId)
        }

    override suspend fun getTrending(region: String): Result<List<Song>> =
        executeWithRetry(providerId, timeoutMs = 20_000L, maxRetries = 1) {
            client.getTrending(region)
        }

    override suspend fun getStream(videoId: String): Result<String> =
        Result.failure(ProviderError.NotFound(providerId))
}
