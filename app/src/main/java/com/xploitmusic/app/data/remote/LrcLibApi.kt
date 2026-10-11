package com.xploitmusic.app.data.remote

import com.xploitmusic.app.data.dto.LrcLibResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface LrcLibApi {
    @GET("api/get")
    suspend fun getLyrics(
        @Query("track_name") trackName: String,
        @Query("artist_name") artistName: String,
        @Query("duration") duration: Double?
    ): LrcLibResponse
    
    @GET("api/search")
    suspend fun searchLyrics(
        @Query("q") query: String
    ): List<LrcLibResponse>
}
