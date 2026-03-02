package com.auramusic.app.data.remote

import com.auramusic.app.data.dto.InvidiousSearchResponse
import com.auramusic.app.data.dto.InvidiousVideoDetails
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface InvidiousApi {
    
    @GET("api/v1/search")
    suspend fun search(
        @Query("q") query: String,
        @Query("type") type: String = "video",
        @Query("sort") sort: String = "relevance"
    ): List<InvidiousSearchResponse>

    @GET("api/v1/videos/{videoId}")
    suspend fun getVideoDetails(
        @Path("videoId") videoId: String
    ): InvidiousVideoDetails
}
