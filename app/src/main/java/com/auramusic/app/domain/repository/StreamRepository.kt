package com.auramusic.app.domain.repository

import com.auramusic.app.core.util.Resource
import com.auramusic.app.domain.model.SearchResults
import com.auramusic.app.domain.model.Song
import com.auramusic.app.domain.model.LatamCharts
import com.auramusic.app.data.remote.AuraHomeFeedResponse

interface StreamRepository {
    suspend fun searchSongs(query: String): Resource<List<Song>>
    suspend fun searchMixed(query: String): Resource<SearchResults>
    suspend fun getStreamInfo(songId: String): Resource<String> // Returns audio URL
    suspend fun getRelatedSongs(songId: String): Resource<List<Song>>
    
    // Nueva función consolidada para el Home
    suspend fun getHomeFeed(): Resource<AuraHomeFeedResponse>
    
    suspend fun getTrending(region: String? = null): Resource<List<Song>>
    suspend fun getLatamCharts(forceRefresh: Boolean = false): Resource<LatamCharts>
    suspend fun getDiscovery(): Resource<List<Song>>
    suspend fun getGenreSongs(genre: String): Resource<List<Song>>
    
}
