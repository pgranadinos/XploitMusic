package com.auramusic.app.data.repository

import android.util.Log
import com.auramusic.app.core.util.Resource
import com.auramusic.app.data.remote.PipedApi
import com.auramusic.app.domain.model.SearchResultItem
import com.auramusic.app.domain.model.SearchResults
import com.auramusic.app.domain.model.Song
import com.auramusic.app.domain.repository.StreamRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo
import javax.inject.Inject

class StreamRepositoryImpl @Inject constructor(
    private val api: PipedApi
) : StreamRepository {

    companion object {
        private const val TAG = "XploitDebug"
    }

    override suspend fun searchSongs(query: String): Resource<List<Song>> {
        return try {
            Log.d(TAG, "StreamRepository: Searching songs: $query")
            val response = api.search(query, filter = "music_songs")

            val songs = response.items
                .filter { it.type == "stream" }
                .map { item ->
                    val videoId = item.url.substringAfter("watch?v=")
                    Song(
                        id = videoId,
                        title = item.title,
                        artist = item.uploaderName,
                        thumbnailUrl = item.thumbnail,
                        durationSeconds = item.duration
                    )
                }

            Log.d(TAG, "StreamRepository: Found ${songs.size} songs")
            Resource.Success(songs)
        } catch (e: java.io.IOException) {
            Log.e(TAG, "StreamRepository: Network error: ${e.localizedMessage}", e)
            Resource.Error("Network error: ${e.localizedMessage}")
        } catch (e: Exception) {
            Log.e(TAG, "StreamRepository: Search error: ${e.localizedMessage}", e)
            Resource.Error("Search error: ${e.localizedMessage}")
        }
    }

    override suspend fun searchMixed(query: String): Resource<SearchResults> {
        return try {
            Log.d(TAG, "StreamRepository: Mixed search: $query")

            // Piped API requires specific filter values – make 3 parallel calls
            val songs = mutableListOf<Song>()
            val channels = mutableListOf<SearchResultItem.ChannelResult>()
            val playlists = mutableListOf<SearchResultItem.PlaylistResult>()

            // 1. Songs (music_songs)
            try {
                val songResponse = api.search(query, filter = "music_songs")
                for (item in songResponse.items) {
                    val videoId = item.url.substringAfter("watch?v=", "")
                    if (videoId.isNotEmpty()) {
                        songs.add(
                            Song(
                                id = videoId,
                                title = item.title,
                                artist = item.uploaderName,
                                thumbnailUrl = item.thumbnail,
                                durationSeconds = item.duration
                            )
                        )
                    }
                }
                Log.d(TAG, "StreamRepository: Mixed — ${songs.size} songs")
            } catch (e: Exception) {
                Log.w(TAG, "StreamRepository: Mixed songs failed: ${e.message}")
            }

            // 2. Channels
            try {
                val channelResponse = api.search(query, filter = "channels")
                for (item in channelResponse.items) {
                    channels.add(
                        SearchResultItem.ChannelResult(
                            name = item.title,
                            url = item.url,
                            thumbnail = item.thumbnail
                        )
                    )
                }
                Log.d(TAG, "StreamRepository: Mixed — ${channels.size} channels")
            } catch (e: Exception) {
                Log.w(TAG, "StreamRepository: Mixed channels failed: ${e.message}")
            }

            // 3. Playlists
            try {
                val playlistResponse = api.search(query, filter = "playlists")
                for (item in playlistResponse.items) {
                    playlists.add(
                        SearchResultItem.PlaylistResult(
                            name = item.title,
                            url = item.url,
                            thumbnail = item.thumbnail,
                            videoCount = if (item.videos >= 0) item.videos else 0
                        )
                    )
                }
                Log.d(TAG, "StreamRepository: Mixed — ${playlists.size} playlists")
            } catch (e: Exception) {
                Log.w(TAG, "StreamRepository: Mixed playlists failed: ${e.message}")
            }

            Log.d(TAG, "StreamRepository: Mixed total — ${songs.size} songs, ${channels.size} channels, ${playlists.size} playlists")
            Resource.Success(SearchResults(songs, channels, playlists))
        } catch (e: java.io.IOException) {
            Log.e(TAG, "StreamRepository: Mixed search network error: ${e.localizedMessage}", e)
            Resource.Error("Network error: ${e.localizedMessage}")
        } catch (e: Exception) {
            Log.e(TAG, "StreamRepository: Mixed search error: ${e.localizedMessage}", e)
            Resource.Error("Search error: ${e.localizedMessage}")
        }
    }

    override suspend fun getStreamInfo(songId: String): Resource<String> {
        Log.d(TAG, "StreamRepository: Getting stream for: $songId")

        // Strategy 1: Local extraction with NewPipeExtractor
        val localResult = tryNewPipeExtraction(songId)
        if (localResult is Resource.Success) return localResult
        Log.w(TAG, "StreamRepository: NewPipe failed, trying Piped API...")

        // Strategy 2: Piped API fallback
        val pipedResult = tryPipedApi(songId)
        if (pipedResult is Resource.Success) return pipedResult

        val localError = (localResult as? Resource.Error)?.message ?: "Unknown"
        val pipedError = (pipedResult as? Resource.Error)?.message ?: "Unknown"
        return Resource.Error("Cannot get audio.\nLocal: $localError\nPiped: $pipedError")
    }

    override suspend fun getRelatedSongs(songId: String): Resource<List<Song>> {
        return try {
            Log.d(TAG, "StreamRepository: Getting related songs for: $songId")
            val response = api.getStream(songId)
            val related = response.relatedStreams?.mapNotNull { stream ->
                val videoId = stream.url.substringAfter("watch?v=", "")
                if (videoId.isNotEmpty() && stream.duration > 0) {
                    Song(
                        id = videoId,
                        title = stream.title,
                        artist = stream.uploaderName ?: "Unknown",
                        thumbnailUrl = stream.thumbnail ?: "",
                        durationSeconds = stream.duration
                    )
                } else null
            } ?: emptyList()

            Log.d(TAG, "StreamRepository: Found ${related.size} related songs")
            Resource.Success(related)
        } catch (e: Exception) {
            Log.e(TAG, "StreamRepository: Related songs error: ${e.localizedMessage}", e)
            Resource.Error("Error getting related: ${e.localizedMessage}")
        }
    }

    override suspend fun getTrending(): Resource<List<Song>> {
        return try {
            Log.d(TAG, "StreamRepository: Getting Peru music trending...")
            val allSongs = mutableListOf<Song>()

            // 1. Peru-specific music searches
            val peruQueries = listOf(
                "Top Hits Peru 2025",
                "Música Peruana Tendencias",
                "Reggaeton Peru Mix 2025"
            )
            for (query in peruQueries) {
                try {
                    val response = api.search(query, "music_songs")
                    val songs = response.items.mapNotNull { item ->
                        val videoId = item.url.substringAfter("watch?v=", "")
                        if (videoId.isNotEmpty()) {
                            Song(
                                id = videoId,
                                title = item.title,
                                artist = item.uploaderName,
                                thumbnailUrl = item.thumbnail,
                                durationSeconds = item.duration
                            )
                        } else null
                    }
                    allSongs.addAll(songs)
                    if (allSongs.size >= 15) break
                } catch (e: Exception) {
                    Log.w(TAG, "StreamRepository: Peru search '$query' failed: ${e.message}")
                }
            }

            // 2. Piped trending endpoint with region=PE as complementary source
            try {
                val trendingItems = api.getTrending(region = "PE")
                val trendingSongs = trendingItems.mapNotNull { item ->
                    val videoId = item.url.substringAfter("watch?v=", "")
                    if (videoId.isNotEmpty() && item.duration > 0) {
                        Song(
                            id = videoId,
                            title = item.title,
                            artist = item.uploaderName ?: "Unknown",
                            thumbnailUrl = item.thumbnail ?: "",
                            durationSeconds = item.duration
                        )
                    } else null
                }
                allSongs.addAll(trendingSongs)
                Log.d(TAG, "StreamRepository: Piped PE trending: ${trendingSongs.size} items")
            } catch (e: Exception) {
                Log.w(TAG, "StreamRepository: Piped PE trending failed: ${e.message}")
            }

            val unique = allSongs.distinctBy { it.id }.take(25)
            Log.d(TAG, "StreamRepository: Peru trending total: ${unique.size} songs")
            Resource.Success(unique)
        } catch (e: Exception) {
            Log.e(TAG, "StreamRepository: Peru trending error: ${e.localizedMessage}", e)
            Resource.Error("Peru trending error: ${e.localizedMessage}")
        }
    }

    private suspend fun tryNewPipeExtraction(songId: String): Resource<String> {
        return try {
            withContext(Dispatchers.IO) {
                val youtubeUrl = "https://www.youtube.com/watch?v=$songId"
                Log.d(TAG, "NewPipe: Extracting from $youtubeUrl")
                val streamInfo = StreamInfo.getInfo(ServiceList.YouTube, youtubeUrl)
                val audioStreams = streamInfo.audioStreams

                Log.d(TAG, "NewPipe: Found ${audioStreams.size} audio streams")

                @Suppress("DEPRECATION")
                val bestStream = audioStreams
                    .filter { it.url != null }
                    .let { streams ->
                        streams.filter { it.format?.name?.contains("M4A", true) == true }
                            .maxByOrNull { it.bitrate }
                            ?: streams.maxByOrNull { it.bitrate }
                    }

                @Suppress("DEPRECATION")
                if (bestStream?.url != null) {
                    Log.d(TAG, "NewPipe: Selected - Format: ${bestStream.format}, Bitrate: ${bestStream.bitrate}")
                    Resource.Success(bestStream.url!!)
                } else {
                    Resource.Error("No audio streams found via NewPipe")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "NewPipe: Failed: ${e.javaClass.simpleName}: ${e.message}", e)
            Resource.Error("NewPipe: ${e.localizedMessage ?: e.javaClass.simpleName}")
        }
    }

    private suspend fun tryPipedApi(songId: String): Resource<String> {
        return try {
            Log.d(TAG, "Piped: Getting streams for $songId")
            val response = api.getStream(songId)
            val audioStreams = response.audioStreams

            val bestStream = audioStreams
                .filter { it.url.isNotBlank() }
                .let { streams ->
                    streams.filter { it.format.equals("M4A", ignoreCase = true) }
                        .maxByOrNull { it.bitrate }
                        ?: streams.maxByOrNull { it.bitrate }
                }

            if (bestStream != null) {
                Log.d(TAG, "Piped: Selected - Format: ${bestStream.format}, Bitrate: ${bestStream.bitrate}")
                Resource.Success(bestStream.url)
            } else {
                Resource.Error("No audio streams found via Piped API")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Piped: Failed: ${e.javaClass.simpleName}: ${e.message}", e)
            Resource.Error("Piped: ${e.localizedMessage ?: e.javaClass.simpleName}")
        }
    }
}
