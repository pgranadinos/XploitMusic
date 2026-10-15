package com.xploitmusic.app.data.repository

import com.xploitmusic.app.data.local.dao.SearchHistoryDao
import com.xploitmusic.app.data.local.entity.SearchHistoryEntity
import com.xploitmusic.app.domain.repository.SearchHistoryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchHistoryRepositoryImpl @Inject constructor(
    private val searchHistoryDao: SearchHistoryDao
) : SearchHistoryRepository {

    override fun getRecentSearches(): Flow<List<SearchHistoryEntity>> =
        searchHistoryDao.getRecentSearches()

    override suspend fun getMatchingHistory(query: String): List<SearchHistoryEntity> =
        searchHistoryDao.getMatchingHistory(query)

    override suspend fun saveSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        searchHistoryDao.upsert(
            SearchHistoryEntity(
                query = trimmed.lowercase(),
                displayQuery = trimmed,
                searchedAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun upsert(entity: SearchHistoryEntity) {
        searchHistoryDao.upsert(entity)
    }

    override suspend fun delete(query: String) {
        searchHistoryDao.delete(query)
    }

    override suspend fun clearAll() {
        searchHistoryDao.clearAll()
    }

    override suspend fun deleteOlderThan(timestamp: Long) {
        searchHistoryDao.deleteOlderThan(timestamp)
    }
}
