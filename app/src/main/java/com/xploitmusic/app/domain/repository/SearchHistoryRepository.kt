package com.xploitmusic.app.domain.repository

import com.xploitmusic.app.data.local.entity.SearchHistoryEntity
import kotlinx.coroutines.flow.Flow

interface SearchHistoryRepository {
    fun getRecentSearches(): Flow<List<SearchHistoryEntity>>
    suspend fun getMatchingHistory(query: String): List<SearchHistoryEntity>
    suspend fun saveSearch(query: String)
    suspend fun upsert(entity: SearchHistoryEntity)
    suspend fun delete(query: String)
    suspend fun clearAll()
    suspend fun deleteOlderThan(timestamp: Long)
}
