package com.xploitmusic.app.data.provider.local

import com.xploitmusic.app.domain.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

internal data class AudioCandidate(
    val url: String,
    val format: MediaFormat?,
    val bitrate: Int
)

internal fun selectBestAudio(candidates: List<AudioCandidate>): String? {
    return candidates
        .asSequence()
        .filter { it.url.startsWith("https://") }
        .maxWithOrNull(
            compareBy<AudioCandidate> { candidate ->
                when (candidate.format) {
                    MediaFormat.M4A -> 2
                    MediaFormat.WEBMA, MediaFormat.WEBMA_OPUS,
                    MediaFormat.OPUS -> 1
                    else -> 0
                }
            }.thenBy { it.bitrate }
        )
        ?.url
}

/** Direct, on-device access to YouTube metadata and temporary audio URLs. */
@Singleton
class NewPipeExtractorClient @Inject constructor(
    okHttpClient: OkHttpClient
) {
    companion object {
        private const val CACHE_TTL_MS = 5 * 60 * 1000L
        private const val YOUTUBE_WATCH_URL = "https://www.youtube.com/watch?v="
        private val initLock = Any()
    }

    private data class CachedInfo(val info: StreamInfo, val loadedAtMs: Long)

    private val service = ServiceList.YouTube
    private val streamInfoCache = ConcurrentHashMap<String, CachedInfo>()
    private val extractionMutex = Mutex()

    init {
        synchronized(initLock) {
            if (NewPipe.getDownloader() == null) {
                NewPipe.init(
                    NewPipeOkHttpDownloader(okHttpClient),
                    Localization("es", "PE"),
                    ContentCountry("PE")
                )
            }
        }
    }

    suspend fun search(query: String): List<Song> = withContext(Dispatchers.IO) {
        val searchInfo = SearchInfo.getInfo(
            service,
            service.searchQHFactory.fromQuery(query)
        )
        searchInfo.relatedItems
            .filterIsInstance<StreamInfoItem>()
            .mapNotNull(::toSong)
            .distinctBy(Song::id)
    }

    suspend fun getTrack(videoId: String): Song {
        return toSong(loadStreamInfo(videoId))
    }

    suspend fun getStream(videoId: String): String {
        val streams = loadStreamInfo(videoId).audioStreams
        val candidates = streams.mapNotNull { it.toCandidate() }
        return selectBestAudio(candidates)
            ?: error("NewPipeExtractor returned no direct HTTPS audio stream for $videoId")
    }

    suspend fun getRelated(videoId: String): List<Song> {
        return loadStreamInfo(videoId).relatedItems
            .filterIsInstance<StreamInfoItem>()
            .mapNotNull(::toSong)
            .distinctBy(Song::id)
    }

    private suspend fun loadStreamInfo(videoId: String): StreamInfo {
        val now = System.currentTimeMillis()
        streamInfoCache[videoId]
            ?.takeIf { now - it.loadedAtMs < CACHE_TTL_MS }
            ?.let { return it.info }

        return extractionMutex.withLock {
            val refreshedAt = System.currentTimeMillis()
            streamInfoCache[videoId]
                ?.takeIf { refreshedAt - it.loadedAtMs < CACHE_TTL_MS }
                ?.let { return@withLock it.info }

            withContext(Dispatchers.IO) {
                StreamInfo.getInfo(service, "$YOUTUBE_WATCH_URL$videoId")
            }.also { info ->
                streamInfoCache[videoId] = CachedInfo(info, System.currentTimeMillis())
            }
        }
    }

    private fun toSong(item: StreamInfoItem): Song? {
        val id = runCatching { service.streamLHFactory.getId(item.url) }.getOrNull()
            ?.takeIf(String::isNotBlank)
            ?: return null
        return Song(
            id = id,
            title = item.name,
            artist = item.uploaderName?.takeIf(String::isNotBlank) ?: "YouTube",
            thumbnailUrl = item.thumbnails.maxByOrNull { it.height }?.url.orEmpty(),
            durationSeconds = item.duration.coerceAtLeast(0L)
        )
    }

    private fun toSong(info: StreamInfo): Song = Song(
        id = info.id,
        title = info.name,
        artist = info.uploaderName.takeIf(String::isNotBlank) ?: "YouTube",
        thumbnailUrl = info.thumbnails.maxByOrNull { it.height }?.url.orEmpty(),
        durationSeconds = info.duration.coerceAtLeast(0L)
    )

    private fun AudioStream.toCandidate(): AudioCandidate? {
        if (!isUrl) return null
        return AudioCandidate(
            url = content,
            format = format,
            bitrate = maxOf(averageBitrate, bitrate, 0)
        )
    }
}
