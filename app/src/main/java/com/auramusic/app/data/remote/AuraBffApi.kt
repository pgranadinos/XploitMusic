package com.auramusic.app.data.remote

import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Body
import retrofit2.http.Path
import retrofit2.http.Query

interface AuraBffApi {
    @GET("home/feed")
    suspend fun getHomeFeed(): AuraHomeFeedResponse

    @GET("search")
    suspend fun search(
        @Query("q") query: String,
        @Query("type") type: String = "all"
    ): AuraSearchResponse

    @GET("stream/{trackId}")
    suspend fun getStream(
        @Path("trackId") trackId: String
    ): AuraStreamResponse

    /**
     * Returns tracks related to [trackId]. The BFF resolves this via
     * yt-dlp's "related videos" sidebar. Added in BFF v1.1.
     * If the BFF doesn't support this endpoint yet, StreamRepositoryImpl
     * will catch the 404 and fall back to artist-based search.
     */
    @GET("stream/{trackId}/related")
    suspend fun getRelatedTracks(
        @Path("trackId") trackId: String,
        @Query("limit") limit: Int = 15
    ): AuraRelatedResponse

    @POST("analytics/play")
    suspend fun recordPlayEvent(
        @Body request: AuraPlayEventRequest
    )
}

// Data classes for the new BFF Contracts
data class AuraHomeFeedResponse(
    val carousels: List<AuraCarousel>
)

data class AuraCarousel(
    val title: String,
    val type: String,
    val items: List<AuraTrack>
)

data class AuraTrack(
    val id: String,
    val title: String,
    val artist: String,
    val durationSeconds: Int,
    val thumbnailUrl: String
)

data class AuraSearchResponse(
    val query: String,
    val results: List<AuraSearchItem>
)

data class AuraSearchItem(
    val id: String,
    val title: String,
    val type: String,
    val thumbnailUrl: String,
    val artist: String? = null,
    val durationSeconds: Int? = null,
    val itemCount: Int? = null
)

data class AuraStreamResponse(
    val streamUrl: String?,   // nullable: BFF puede omitirlo si el stream no está disponible
    val format: String? = null,
    val expireAt: Long = 0L,
    val bitrate: Int? = null  // kbps, opcional en v1 del BFF
)

data class AuraPlayEventRequest(
    val trackId: String,
    val source: String = "android_app"
)

/**
 * DTO for GET /stream/{trackId}/related.
 * [items] mirrors the same shape as AuraCarousel.items so we can
 * reuse the [AuraTrack.toSong()] extension without extra mapping.
 */
data class AuraRelatedResponse(
    val trackId: String,
    val items: List<AuraTrack>
)
