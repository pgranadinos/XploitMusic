package com.auramusic.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.auramusic.app.data.local.entity.SearchHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchHistoryDao {

    /** Observe the most recent 20 searches, ordered by most recent first */
    @Query("SELECT * FROM recent_searches ORDER BY searchedAt DESC LIMIT 20")
    fun getRecentSearches(): Flow<List<SearchHistoryEntity>>

    /** Find history entries matching a partial query (for suggestion ranking) */
    @Query("SELECT * FROM recent_searches WHERE `query` LIKE '%' || :partial || '%' ORDER BY searchedAt DESC LIMIT 5")
    suspend fun getMatchingHistory(partial: String): List<SearchHistoryEntity>

    /** Insert or update a search entry (upsert by query key) */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: SearchHistoryEntity)

    /** Delete a specific search from history */
    @Query("DELETE FROM recent_searches WHERE `query` = :query")
    suspend fun delete(query: String)

    /** Clear all search history */
    @Query("DELETE FROM recent_searches")
    suspend fun clearAll()

    /** Cleanup: delete entries older than cutoff timestamp */
    @Query("DELETE FROM recent_searches WHERE searchedAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)
}
