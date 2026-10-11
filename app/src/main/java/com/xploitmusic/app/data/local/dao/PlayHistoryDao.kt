package com.xploitmusic.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.xploitmusic.app.data.local.entity.PlayHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlayHistoryDao {
    @Query("SELECT * FROM play_history ORDER BY playedAtTimestamp DESC LIMIT :limit")
    fun getRecentPlayHistory(limit: Int = 10): Flow<List<PlayHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(history: PlayHistoryEntity)

    @Query("DELETE FROM play_history WHERE contextId = :contextId")
    suspend fun deleteHistory(contextId: String)
}
