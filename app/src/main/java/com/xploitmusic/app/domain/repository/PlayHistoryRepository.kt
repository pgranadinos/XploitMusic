package com.xploitmusic.app.domain.repository

import com.xploitmusic.app.data.local.entity.PlayHistoryEntity
import kotlinx.coroutines.flow.Flow

interface PlayHistoryRepository {
    fun getRecentPlayHistory(limit: Int = 5): Flow<List<PlayHistoryEntity>>
}
