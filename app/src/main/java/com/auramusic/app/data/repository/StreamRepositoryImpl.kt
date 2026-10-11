package com.auramusic.app.data.repository

import android.util.Log
import com.auramusic.app.core.util.Resource
import com.auramusic.app.domain.model.SearchResultItem
import com.auramusic.app.domain.model.SearchResults
import com.auramusic.app.domain.model.Song
import com.auramusic.app.domain.model.LatamCharts
import com.auramusic.app.domain.model.LatamCountry
import com.auramusic.app.data.provider.youtubemusic.YouTubeMusicClient
import com.auramusic.app.data.provider.util.executeWithRetry
import com.auramusic.app.domain.provider.ProviderGateway
import com.auramusic.app.domain.repository.StreamRepository
import com.auramusic.app.core.analytics.AuraAnalyticsLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject
import com.auramusic.app.data.remote.AuraHomeFeedResponse
import java.util.Calendar
import com.auramusic.app.core.util.RegionUtils

class StreamRepositoryImpl @Inject constructor(
    private val gateway: ProviderGateway,
    private val analyticsLogger: AuraAnalyticsLogger,
    private val youtubeMusicClient: YouTubeMusicClient,
) : StreamRepository {

    companion object {
        private const val TAG = "XploitDebug"
        private const val REGIONAL_MUSIC_CACHE_TTL_MS = 10 * 60 * 1000L
        private const val MIN_REGIONAL_SONGS_BEFORE_FALLBACK = 6
        private const val HOME_REGION = "PE"
    }

    private data class RegionalMusicCache(
        val region: String,
        val songs: List<Song>,
        val loadedAtMs: Long
    )

    private val regionalMusicMutex = Mutex()
    @Volatile private var regionalMusicCache: RegionalMusicCache? = null
    private val latamMutex = Mutex()
    private var latamCache: Pair<Long, LatamCharts>? = null

    override suspend fun searchSongs(query: String): Resource<List<Song>> {
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "StreamRepository: Searching songs via ProviderGateway: $query")
        val result = gateway.search(query)
        return result.fold(
            onSuccess = { 
                analyticsLogger.logSearch(query, true, System.currentTimeMillis() - startTime, it.size)
                Resource.Success(it) 
            },
            onFailure = { 
                analyticsLogger.logSearch(query, false, System.currentTimeMillis() - startTime, 0, true, it.message)
                Resource.Error(it.message ?: "Search failed") 
            }
        )
    }

    override suspend fun searchMixed(query: String): Resource<SearchResults> {
        val startTime = System.currentTimeMillis()
        return try {
            Log.d(TAG, "StreamRepository: Mixed search via Gateway con fallback: $query")
            // Usar el gateway con fallback automático PIPED→INVIDIOUS→BFF
            val result = gateway.search(query)
            result.fold(
                onSuccess = { songs ->
                    Log.d(TAG, "StreamRepository: Mixed total — ${songs.size} songs")
                    analyticsLogger.logSearch(query, true, System.currentTimeMillis() - startTime, songs.size)
                    Resource.Success(SearchResults(songs, emptyList(), emptyList(), emptySet()))
                },
                onFailure = { error ->
                    Log.w(TAG, "StreamRepository: Gateway search falló, ${error.message}")
                    analyticsLogger.logSearch(query, false, System.currentTimeMillis() - startTime, 0, true, error.message)
                    Resource.Error("El servidor de búsqueda no responde. Verifica tu conexión e intenta de nuevo.")
                }
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            analyticsLogger.logSearch(query, false, System.currentTimeMillis() - startTime, 0, true, e.localizedMessage)
            Log.e(TAG, "StreamRepository: Mixed search error: ${e.localizedMessage}", e)
            Resource.Error("Error de búsqueda: ${e.localizedMessage}")
        }
    }

    override suspend fun getStreamInfo(songId: String): Resource<String> {
        val startTime = System.currentTimeMillis()
        val result = gateway.getStream(songId)
        val timeMs = System.currentTimeMillis() - startTime
        return result.fold(
            onSuccess = { 
                com.auramusic.app.utils.AppLogger.logPlaybackState(songId, "GET_STREAM_SUCCESS", timeMs)
                Resource.Success(it) 
            },
            onFailure = { 
                com.auramusic.app.utils.AppLogger.logPlaybackState(songId, "ERROR", timeMs)
                Resource.Error(it.message ?: "No se pudo obtener el audio del servidor.") 
            }
        )
    }
    
    override suspend fun getHomeFeed(): Resource<AuraHomeFeedResponse> {
        return try {
            Log.d(TAG, "StreamRepository: Building HomeFeed locally for $HOME_REGION")
            val year = Calendar.getInstance().get(Calendar.YEAR)
            val (regionalResult, genreResults) = coroutineScope {
                val regional = async { getTrending(HOME_REGION) }
                val rock = async { searchHomeSection("rock hits Perú $year official video") }
                val pop = async { searchHomeSection("pop latino Perú $year official video") }
                regional.await() to listOf(rock.await(), pop.await())
            }

            val regional = if (regionalResult is Resource.Success) {
                regionalResult.data.orEmpty().take(15)
            } else {
                emptyList()
            }
            val rock = selectRegionalMusic(listOf(genreResults[0]), limit = 10)
            val pop = selectRegionalMusic(listOf(genreResults[1]), limit = 10)

            val toAuraTrack: (Song) -> com.auramusic.app.data.remote.AuraTrack = { song ->
                com.auramusic.app.data.remote.AuraTrack(
                    id = song.id,
                    title = song.title,
                    artist = song.artist,
                    durationSeconds = song.durationSeconds.toInt(),
                    thumbnailUrl = song.thumbnailUrl
                )
            }
            val carousels = listOf(
                com.auramusic.app.data.remote.AuraCarousel(
                    "${RegionUtils.getRegionFlag(HOME_REGION)} Lo más escuchado",
                    "trending",
                    regional.map(toAuraTrack)
                ),
                com.auramusic.app.data.remote.AuraCarousel(
                    "🎸 Rock",
                    "genre",
                    rock.map(toAuraTrack)
                ),
                com.auramusic.app.data.remote.AuraCarousel(
                    "🎤 Pop latino",
                    "genre",
                    pop.map(toAuraTrack)
                )
            ).filter { it.items.isNotEmpty() }

            if (carousels.isEmpty()) {
                Resource.Error("No se pudo cargar el inicio")
            } else {
                Log.d(TAG, "StreamRepository: Local HomeFeed ready — ${carousels.size} sections")
                Resource.Success(AuraHomeFeedResponse(carousels = carousels))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "StreamRepository: Local HomeFeed failed", e)
            Resource.Error("No se pudo cargar el inicio")
        }
    }

    private suspend fun searchHomeSection(query: String): List<Song> {
        return try {
            gateway.search(query).getOrElse { error ->
                Log.w(TAG, "StreamRepository: Home section '$query' failed: ${error.message}")
                emptyList()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "StreamRepository: Home section '$query' failed: ${e.message}")
            emptyList()
        }
    }

    override suspend fun getRelatedSongs(songId: String): Resource<List<Song>> {
        Log.d(TAG, "StreamRepository: Getting related songs via ProviderGateway for: $songId")
        val result = gateway.getRelated(songId)
        return result.fold(
            onSuccess = { Resource.Success(it) },
            onFailure = { Resource.Error(it.message ?: "No se encontraron canciones relacionadas") }
        )
    }

    // =========================================================================
    // MÉTODOS MANTENIDOS PROVISIONALMENTE (OBSOLETOS GRACIAS A GetHomeFeed)
    // Para no romper la UI actual de una sola vez
    // =========================================================================

    override suspend fun getTrending(region: String?): Resource<List<Song>> {
        val activeRegion = RegionUtils.normalizeRegion(region ?: "PE")
        val now = System.currentTimeMillis()
        regionalMusicCache
            ?.takeIf { it.region == activeRegion && now - it.loadedAtMs < REGIONAL_MUSIC_CACHE_TTL_MS }
            ?.let {
                Log.d(TAG, "StreamRepository: Regional music cache hit for $activeRegion")
                return Resource.Success(it.songs)
            }

        return regionalMusicMutex.withLock {
            val refreshedAt = System.currentTimeMillis()
            regionalMusicCache
                ?.takeIf {
                    it.region == activeRegion &&
                        refreshedAt - it.loadedAtMs < REGIONAL_MUSIC_CACHE_TTL_MS
                }
                ?.let { return@withLock Resource.Success(it.songs) }

            Log.d(TAG, "StreamRepository: Loading regional music for $activeRegion")
            val year = Calendar.getInstance().get(Calendar.YEAR)
            val queries = regionalMusicQueries(activeRegion, year)
            val searchBatches = coroutineScope {
                queries.map { query ->
                    async {
                        try {
                            val result = gateway.search(query)
                            val songs = result.getOrElse { error ->
                                Log.w(TAG, "StreamRepository: Regional search '$query' failed: ${error.message}")
                                emptyList()
                            }
                            Log.d(TAG, "StreamRepository: Regional search '$query': ${songs.size} songs")
                            songs
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.w(TAG, "StreamRepository: Regional search '$query' failed: ${e.message}")
                            emptyList()
                        }
                    }
                }.awaitAll()
            }

            var selected = selectRegionalMusic(searchBatches)

            // Generic YouTube trends can contain news or live events, so they are only
            // considered when the music-focused searches cannot fill the first grid.
            if (selected.size < MIN_REGIONAL_SONGS_BEFORE_FALLBACK) {
                val fallbackSongs = try {
                    var result = gateway.getTrending(activeRegion)
                    if (result.isFailure && activeRegion != "US") {
                        result = gateway.getTrending("US")
                    }
                    result.getOrDefault(emptyList())
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "StreamRepository: Generic trending fallback failed: ${e.message}")
                    emptyList()
                }
                selected = selectRegionalMusic(searchBatches + listOf(fallbackSongs))
            }

            Log.d(TAG, "StreamRepository: Regional music final — ${selected.size} tracks for $activeRegion")
            if (selected.isEmpty()) {
                Resource.Error("No se encontraron tendencias musicales para $activeRegion")
            } else {
                regionalMusicCache = RegionalMusicCache(
                    region = activeRegion,
                    songs = selected,
                    loadedAtMs = System.currentTimeMillis()
                )
                Resource.Success(selected)
            }
        }
    }

    override suspend fun getDiscovery(): Resource<List<Song>> {
        return when (val homeFeed = getHomeFeed()) {
            is Resource.Success -> {
                val feed = homeFeed.data ?: return Resource.Error("No se pudo cargar descubrimiento")
                val carousel = feed.carousels.find { it.type == "discovery" }
                    ?: feed.carousels.find { it.type == "trending" }
                val songs = carousel?.items?.map { it.toSong() }.orEmpty()
                if (songs.isEmpty()) Resource.Error("No se encontraron canciones para descubrimiento")
                else Resource.Success(songs)
            }
            is Resource.Error -> Resource.Error(homeFeed.message ?: "Error cargando descubrimiento")
            is Resource.Loading -> Resource.Error("No se pudo cargar descubrimiento")
        }
    }

    override suspend fun getLatamCharts(forceRefresh: Boolean): Resource<LatamCharts> = latamMutex.withLock {
        latamCache?.takeIf { (loadedAt, selection) ->
            val ttl = if (selection.unavailableCountries.isEmpty()) 30 * 60_000L else 2 * 60_000L
            !forceRefresh && System.currentTimeMillis() - loadedAt < ttl
        }?.let { return@withLock Resource.Success(it.second) }

        val permits = Semaphore(3)
        val results = coroutineScope {
            LatamCountry.values().map { country ->
                async {
                    permits.withPermit {
                        country to executeWithRetry("YOUTUBE_MUSIC", timeoutMs = 20_000L, maxRetries = 0) {
                            youtubeMusicClient.getCountryChart(country)
                        }
                    }
                }
            }.awaitAll()
        }
        val charts = results.mapNotNull { it.second.getOrNull() }.filter { it.entries.isNotEmpty() }
        if (charts.isEmpty()) return@withLock Resource.Error("No se pudieron cargar los rankings de Latinoamérica. Intenta de nuevo.")
        val selection = LatamCharts(
            charts = charts,
            entries = mergeLatamCharts(charts),
            unavailableCountries = LatamCountry.values().filter { country -> charts.none { it.country == country } },
        )
        latamCache = System.currentTimeMillis() to selection
        Resource.Success(selection)
    }

    override suspend fun getGenreSongs(genre: String): Resource<List<Song>> {
        return try {
            // Realizamos una búsqueda dinámica para no repetir las canciones
            searchSongs("$genre hits")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Error cargando género")
        }
    }

    private fun com.auramusic.app.data.remote.AuraTrack.toSong() = Song(
        id = id,
        title = title,
        artist = artist,
        thumbnailUrl = thumbnailUrl,
        durationSeconds = durationSeconds.toLong()
    )

    // La extracción y los fallbacks pertenecen al ProviderGateway.

}
