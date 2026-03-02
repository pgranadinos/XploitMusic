package com.auramusic.app.data.remote

import com.auramusic.app.data.dto.PipedSearchResponse
import com.auramusic.app.data.dto.PipedStreamResponse
import com.auramusic.app.data.dto.PipedTrendingItem
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface PipedApi {
    @GET("search")
    suspend fun search(
        @Query("q") query: String,
        @Query("filter") filter: String = "music_songs"
    ): PipedSearchResponse

    @GET("streams/{videoId}")
    suspend fun getStream(
        @Path("videoId") videoId: String
    ): PipedStreamResponse

    @GET("trending")
    suspend fun getTrending(
        @Query("region") region: String = "US"
    ): List<PipedTrendingItem>
}
