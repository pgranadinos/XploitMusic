package com.auramusic.app.data.provider.impl

import com.auramusic.app.data.provider.util.executeWithRetry
import com.auramusic.app.data.remote.InvidiousApi
import com.auramusic.app.domain.model.Song
import com.auramusic.app.domain.provider.MultimediaProvider
import com.auramusic.app.domain.provider.ProviderError
import javax.inject.Inject

class InvidiousProvider @Inject constructor(
    private val api: InvidiousApi
) : MultimediaProvider {

    override val providerId: String = "INVIDIOUS"

    override suspend fun search(query: String): Result<List<Song>> {
        return executeWithRetry(providerId) {
            val response = api.search(query, type = "video")
            response.map {
                Song(
                    id = it.videoId,
                    title = it.title,
                    artist = it.author,
                    thumbnailUrl = it.videoThumbnails?.firstOrNull()?.url ?: "",
                    durationSeconds = it.lengthSeconds
                )
            }
        }
    }

    override suspend fun getTrack(videoId: String): Result<Song> {
        return executeWithRetry(providerId) {
            val response = api.getVideoDetails(videoId)
            Song(
                id = response.videoId,
                title = response.title,
                artist = response.author.ifBlank { "Unknown" },  // FIX: usar author real
                thumbnailUrl = response.videoThumbnails?.firstOrNull()?.url ?: "",  // FIX: usar thumbnail real
                durationSeconds = response.lengthSeconds
            )
        }
    }

    override suspend fun getStream(videoId: String): Result<String> {
        return executeWithRetry(providerId, maxRetries = 2) {
            val response = api.getVideoDetails(videoId)
            val audioFormat = response.adaptiveFormats.find { it.type.startsWith("audio") }
            audioFormat?.url ?: throw ProviderError.NotFound(providerId)
        }
    }

    override suspend fun getRelated(videoId: String): Result<List<Song>> {
        return executeWithRetry(providerId) {
            val details = api.getVideoDetails(videoId)
            val response = api.search("${details.title} mix", type = "video")
            response.map {
                Song(
                    id = it.videoId,
                    title = it.title,
                    artist = it.author,
                    thumbnailUrl = it.videoThumbnails?.firstOrNull()?.url ?: "",
                    durationSeconds = it.lengthSeconds
                )
            }
        }
    }

    override suspend fun getTrending(region: String): Result<List<Song>> {
        return Result.failure(ProviderError.NotFound(providerId))
    }
}
