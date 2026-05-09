package com.auramusic.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.auramusic.app.data.local.entity.DownloadedTrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    /** All downloads ordered by most recently downloaded */
    @Query("SELECT * FROM downloaded_tracks ORDER BY downloadedAt DESC")
    fun getAll(): Flow<List<DownloadedTrackEntity>>

    /** Only completed downloads for offline library */
    @Query("SELECT * FROM downloaded_tracks WHERE status = 'COMPLETED' ORDER BY lastAccessedAt DESC")
    fun getCompleted(): Flow<List<DownloadedTrackEntity>>

    /** Active downloads (queued, downloading, paused) */
    @Query("SELECT * FROM downloaded_tracks WHERE status IN ('QUEUED', 'DOWNLOADING', 'PAUSED')")
    fun getActive(): Flow<List<DownloadedTrackEntity>>

    /** Get a download by song ID */
    @Query("SELECT * FROM downloaded_tracks WHERE songId = :songId")
    suspend fun getBySongId(songId: String): DownloadedTrackEntity?

    /** Get completed download for offline playback */
    @Query("SELECT * FROM downloaded_tracks WHERE songId = :songId AND status = 'COMPLETED'")
    suspend fun getCompletedBySongId(songId: String): DownloadedTrackEntity?

    /** Upsert a download record raw */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRaw(entity: DownloadedTrackEntity)

    /** 
     * Atomic enqueue function. 
     * Validates that the track is not already COMPLETED or QUEUED/DOWNLOADING 
     * before upserting, to cleanly prevent race conditions on concurrent playlist additions.
     */
    @Transaction
    suspend fun enqueueDownload(entity: DownloadedTrackEntity): Boolean {
        val existing = getBySongId(entity.songId)
        if (existing != null) {
            if (existing.status == "COMPLETED" || existing.status == "QUEUED" || existing.status == "DOWNLOADING") {
                return false // Already active or done
            }
        }
        insertRaw(entity.copy(id = existing?.id ?: 0))
        return true
    }

    /** Update progress and status during download */
    @Query("UPDATE downloaded_tracks SET status = :status, progress = :progress WHERE songId = :songId")
    suspend fun updateProgress(songId: String, status: String, progress: Int)

    /** Touch last accessed timestamp (for LRU eviction) */
    @Query("UPDATE downloaded_tracks SET lastAccessedAt = :timestamp WHERE songId = :songId")
    suspend fun touchAccess(songId: String, timestamp: Long = System.currentTimeMillis())

    /** Delete a download record */
    @Query("DELETE FROM downloaded_tracks WHERE songId = :songId")
    suspend fun delete(songId: String)

    /** Total storage used by completed downloads */
    @Query("SELECT SUM(fileSizeBytes) FROM downloaded_tracks WHERE status = 'COMPLETED'")
    suspend fun getTotalStorageUsed(): Long?

    /** Count of completed downloads */
    @Query("SELECT COUNT(*) FROM downloaded_tracks WHERE status = 'COMPLETED'")
    fun getDownloadCount(): Flow<Int>

    /** Least recently used tracks for eviction (oldest accessed first) */
    @Query("SELECT * FROM downloaded_tracks WHERE status = 'COMPLETED' ORDER BY lastAccessedAt ASC LIMIT :count")
    suspend fun getLeastRecentlyUsed(count: Int): List<DownloadedTrackEntity>
}
