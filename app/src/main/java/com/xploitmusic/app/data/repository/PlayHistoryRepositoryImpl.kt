package com.xploitmusic.app.data.repository

import com.xploitmusic.app.data.local.dao.PlayHistoryDao
import com.xploitmusic.app.data.local.entity.PlayHistoryEntity
import com.xploitmusic.app.domain.repository.PlayHistoryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayHistoryRepositoryImpl @Inject constructor(
    private val playHistoryDao: PlayHistoryDao
) : PlayHistoryRepository {

    override fun getRecentPlayHistory(limit: Int): Flow<List<PlayHistoryEntity>> =
        playHistoryDao.getRecentPlayHistory(limit)
}
