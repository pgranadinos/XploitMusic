package com.auramusic.app.data.provider.impl

import com.auramusic.app.data.provider.util.executeWithRetry
import com.auramusic.app.data.remote.PipedApi
import com.auramusic.app.domain.model.Song
import com.auramusic.app.domain.provider.MultimediaProvider
import com.auramusic.app.domain.provider.ProviderError
import javax.inject.Inject

class PipedProvider @Inject constructor(
    private val api: PipedApi
) : MultimediaProvider {

    override val providerId: String = "PIPED"

    override suspend fun search(query: String): Result<List<Song>> {
        return executeWithRetry(providerId) {
            val response = api.search(query)
            response.items.filter { it.type == "stream" }.map {
                Song(
                    id = it.url.removePrefix("/watch?v="),
                    title = it.title,
                    artist = it.uploaderName,
                    thumbnailUrl = it.thumbnail,
                    durationSeconds = it.duration
                )
            }
        }
    }

    override suspend fun getTrack(videoId: String): Result<Song> {
        return executeWithRetry(providerId) {
            val response = api.getStream(videoId)
            Song(
                id = videoId,
                title = response.title ?: "Unknown",
                artist = response.uploader ?: "Unknown",
                thumbnailUrl = response.thumbnailUrl ?: "",
                durationSeconds = response.duration ?: 0L
            )
        }
    }

    override suspend fun getStream(videoId: String): Result<String> {
        return executeWithRetry(providerId, maxRetries = 2) {
            val response = api.getStream(videoId)
            val stream = response.audioStreams.maxByOrNull { it.bitrate }
            stream?.url ?: throw ProviderError.NotFound(providerId)
        }
    }

    override suspend fun getRelated(videoId: String): Result<List<Song>> {
        return executeWithRetry(providerId) {
            val response = api.getStream(videoId)
            response.relatedStreams?.map {
                Song(
                    id = it.url.removePrefix("/watch?v="),
                    title = it.title,
                    artist = it.uploaderName ?: "Unknown",
                    thumbnailUrl = it.thumbnail ?: "",
                    durationSeconds = it.duration
                )
            } ?: emptyList()
        }
    }

    override suspend fun getTrending(region: String): Result<List<Song>> {
        return executeWithRetry(providerId) {
            val response = api.getTrending(region)
            response.map {
                Song(
                    id = it.url.removePrefix("/watch?v="),
                    title = it.title,
                    artist = it.uploaderName ?: "Unknown",
                    thumbnailUrl = it.thumbnail ?: "",
                    durationSeconds = it.duration
                )
            }
        }
    }
}
