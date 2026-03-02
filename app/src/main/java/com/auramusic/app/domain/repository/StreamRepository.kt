package com.auramusic.app.domain.repository

import com.auramusic.app.core.util.Resource
import com.auramusic.app.domain.model.SearchResults
import com.auramusic.app.domain.model.Song

interface StreamRepository {
    suspend fun searchSongs(query: String): Resource<List<Song>>
    suspend fun searchMixed(query: String): Resource<SearchResults>
    suspend fun getStreamInfo(songId: String): Resource<String> // Returns audio URL
    suspend fun getRelatedSongs(songId: String): Resource<List<Song>>
    suspend fun getTrending(): Resource<List<Song>>
}
