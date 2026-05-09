package com.auramusic.app.data.repository

import android.util.Log
import com.auramusic.app.core.util.Resource
import com.auramusic.app.data.remote.AuraBffApi
import com.auramusic.app.domain.model.SearchResultItem
import com.auramusic.app.domain.model.SearchResults
import com.auramusic.app.domain.model.Song
import com.auramusic.app.domain.provider.ProviderGateway
import com.auramusic.app.domain.repository.StreamRepository
import com.auramusic.app.core.analytics.AuraAnalyticsLogger
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import javax.inject.Inject
import com.auramusic.app.data.remote.AuraHomeFeedResponse
import com.auramusic.app.data.remote.AuraPlayEventRequest
import java.util.Locale
import com.auramusic.app.core.util.RegionUtils

class StreamRepositoryImpl @Inject constructor(
    private val api: AuraBffApi,
    private val gateway: ProviderGateway,
    private val analyticsLogger: AuraAnalyticsLogger
) : StreamRepository {

    companion object {
        private const val TAG = "XploitDebug"
    }

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
            Log.d(TAG, "StreamRepository: Getting HomeFeed via BFF")
            val response = api.getHomeFeed()
            Resource.Success(response)
        } catch (e: Exception) {
            // Si el BFF falla, construir el feed desde Piped directamente
            Log.w(TAG, "StreamRepository: BFF HomeFeed falló (${e.localizedMessage}), usando fallback Piped")
            try {
                val initialRegion = RegionUtils.normalizeRegion(Locale.getDefault().country)
                var trendingResult = gateway.getTrending(initialRegion)
                var activeRegion = initialRegion

                // Cascading Fallback
                if (trendingResult.isFailure) {
                    val fallbacks = RegionUtils.getFallbackRegions(initialRegion)
                    for (fallbackRegion in fallbacks) {
                        Log.w(TAG, "StreamRepository: getTrending failed for $activeRegion, trying fallback: $fallbackRegion")
                        trendingResult = gateway.getTrending(fallbackRegion)
                        if (trendingResult.isSuccess) {
                            activeRegion = fallbackRegion
                            break
                        }
                        activeRegion = fallbackRegion
                    }
                }

                if (trendingResult.isFailure) {
                    throw Exception("Todas las regiones de contingencia fallaron para Trending.")
                }
                
                val rockResult = gateway.search("rock hits")
                val popResult = gateway.search("pop hits")

                val toAuraTrack: (Song) -> com.auramusic.app.data.remote.AuraTrack = { s ->
                    com.auramusic.app.data.remote.AuraTrack(
                        id = s.id,
                        title = s.title,
                        artist = s.artist,
                        durationSeconds = s.durationSeconds.toInt(),
                        thumbnailUrl = s.thumbnailUrl
                    )
                }

                val trending = trendingResult.getOrNull()?.take(15)?.map(toAuraTrack) ?: emptyList()
                val rock = rockResult.getOrNull()?.take(10)?.map(toAuraTrack) ?: emptyList()
                val pop = popResult.getOrNull()?.take(10)?.map(toAuraTrack) ?: emptyList()

                val fallbackFeed = AuraHomeFeedResponse(
                    carousels = listOf(
                        com.auramusic.app.data.remote.AuraCarousel("${RegionUtils.getRegionFlag(activeRegion)} Tendencias", "trending", trending),
                        com.auramusic.app.data.remote.AuraCarousel("🎸 Rock", "genre", rock),
                        com.auramusic.app.data.remote.AuraCarousel("🎤 Pop", "genre", pop)
                    )
                )
                Log.d(TAG, "StreamRepository: HomeFeed fallback exitoso con Piped")
                Resource.Success(fallbackFeed)
            } catch (fallbackEx: Exception) {
                Log.e(TAG, "StreamRepository: HomeFeed fallback también falló", fallbackEx)
                Resource.Error("No se pudo cargar el inicio")
            }
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

    override suspend fun getTrending(): Resource<List<Song>> {
        val startTime = System.currentTimeMillis()
        val initialRegion = RegionUtils.normalizeRegion(Locale.getDefault().country)
        var result = gateway.getTrending(initialRegion)
        
        if (result.isFailure) {
            val fallbacks = RegionUtils.getFallbackRegions(initialRegion)
            for (fallbackRegion in fallbacks) {
                com.auramusic.app.utils.AppLogger.logSearch("trending_$initialRegion", false, System.currentTimeMillis() - startTime, "Fallback to $fallbackRegion")
                result = gateway.getTrending(fallbackRegion)
                if (result.isSuccess) break
            }
        }
        
        val timeMs = System.currentTimeMillis() - startTime
        return result.fold(
            onSuccess = { 
                com.auramusic.app.utils.AppLogger.logSearch("trending_final", true, timeMs, resultCount = it.size)
                Resource.Success(it) 
            },
            onFailure = { 
                com.auramusic.app.utils.AppLogger.logSearch("trending_final", false, timeMs, error = it.message)
                Resource.Error(it.message ?: "Error cargando tendencias. Intenta de nuevo más tarde.") 
            }
        )
    }

    override suspend fun getDiscovery(): Resource<List<Song>> {
       return try {
            val response = api.getHomeFeed()
            val discoveryCarousel = response.carousels.find { it.type == "discovery" }
                // Si no hay 'discovery', usamos la data de Trending
                ?: response.carousels.find { it.type == "trending" }
                
            val songs = discoveryCarousel?.items?.map { it.toSong() } ?: emptyList()
            Resource.Success(songs)
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Error cargando descubrimiento")
        }
    }

    override suspend fun getGenreSongs(genre: String): Resource<List<Song>> {
        return try {
            // Realizamos una búsqueda dinámica para no repetir las canciones
            searchSongs("$genre hits")
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

    // Se han eliminado old tryNewPipeExtraction y tryPipedApi
    // Ya que la lógica de extracción ahora le pertenece exclusivamente al servidor BFF

    override suspend fun recordTelemetryPlay(songId: String) {
        try {
            api.recordPlayEvent(AuraPlayEventRequest(trackId = songId, source = "android_app"))
            Log.d(TAG, "Llamada a telemetría exitosa para $songId")
        } catch (e: Exception) {
            Log.e(TAG, "Telemetría falló: ${e.message}")
            // Se ignora silenciosamente para no interrumpir al usuario
        }
    }
}
