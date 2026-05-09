package com.auramusic.app.domain.provider

import com.auramusic.app.domain.model.Song

interface MultimediaProvider {
    val providerId: String
    
    suspend fun search(query: String): Result<List<Song>>
    suspend fun getTrack(videoId: String): Result<Song>
    suspend fun getStream(videoId: String): Result<String>
    suspend fun getRelated(videoId: String): Result<List<Song>>
    suspend fun getTrending(region: String): Result<List<Song>>
}
