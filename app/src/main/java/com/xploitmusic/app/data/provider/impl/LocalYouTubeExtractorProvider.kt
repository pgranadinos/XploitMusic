package com.xploitmusic.app.data.provider.impl

import com.xploitmusic.app.data.provider.local.NewPipeExtractorClient
import com.xploitmusic.app.data.provider.util.executeWithRetry
import com.xploitmusic.app.domain.model.Song
import com.xploitmusic.app.domain.provider.MultimediaProvider
import com.xploitmusic.app.domain.provider.ProviderError
import javax.inject.Inject

class LocalYouTubeExtractorProvider @Inject constructor(
    private val extractor: NewPipeExtractorClient
) : MultimediaProvider {
    override val providerId: String = "LOCAL_YOUTUBE"

    override suspend fun search(query: String): Result<List<Song>> =
        executeWithRetry(providerId, timeoutMs = 30_000L, maxRetries = 0) {
            extractor.search(query)
        }

    override suspend fun getTrack(videoId: String): Result<Song> =
        executeWithRetry(providerId, timeoutMs = 30_000L, maxRetries = 0) {
            extractor.getTrack(videoId)
        }

    override suspend fun getStream(videoId: String): Result<String> =
        executeWithRetry(providerId, timeoutMs = 8_000L, maxRetries = 0) {
            extractor.getStream(videoId)
        }

    fun invalidateStream(videoId: String) {
        extractor.invalidateStream(videoId)
    }

    override suspend fun getRelated(videoId: String): Result<List<Song>> =
        executeWithRetry(providerId, timeoutMs = 45_000L, maxRetries = 0) {
            extractor.getRelated(videoId)
        }

    override suspend fun getTrending(region: String): Result<List<Song>> =
        Result.failure(ProviderError.NotFound(providerId))
}
