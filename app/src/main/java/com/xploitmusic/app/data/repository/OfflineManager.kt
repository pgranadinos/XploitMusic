package com.xploitmusic.app.data.repository

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.xploitmusic.app.data.local.dao.DownloadDao
import com.xploitmusic.app.data.local.entity.DownloadedTrackEntity
import com.xploitmusic.app.data.service.DownloadWorker
import com.xploitmusic.app.data.service.StorageCleanupWorker
import com.xploitmusic.app.domain.model.DownloadQuality
import com.xploitmusic.app.domain.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates downloads, storage management, and offline availability checks.
 * Single point of entry for all download operations.
 */
@Singleton
class OfflineManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadDao: DownloadDao
) {
    companion object {
        private const val TAG = "XploitDebug"
        const val DEFAULT_MAX_STORAGE_BYTES = 500L * 1024 * 1024 // 500 MB
    }

    // Hilt injects XploitApp fields before Application.onCreate. Resolving WorkManager here
    // would ask XploitApp for its configuration before workerFactory has been injected.
    private val workManager by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        WorkManager.getInstance(context)
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ── Download Operations ──

    /**
     * Queue a single track for download.
     * Uses ExistingWorkPolicy.KEEP to avoid duplicate downloads.
     */
    fun downloadTrack(
        song: Song,
        quality: DownloadQuality = DownloadQuality.NORMAL,
        wifiOnly: Boolean = true,
        fromPlaylist: String? = null
    ) {
        val entity = DownloadedTrackEntity(
            songId = song.id,
            title = song.title,
            artist = song.artist,
            thumbnailUrl = song.thumbnailUrl,
            durationSeconds = song.durationSeconds.toLong(),
            status = "QUEUED",
            quality = quality.name,
            addedFromPlaylist = fromPlaylist
        )

        scope.launch {
            val wasEnqueued = downloadDao.enqueueDownload(entity)
            if (wasEnqueued) {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(
                        if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
                    )
                    .setRequiresStorageNotLow(true)
                    .build()

                val request = OneTimeWorkRequestBuilder<DownloadWorker>()
                    .setInputData(workDataOf(
                        "songId" to song.id,
                        "quality" to quality.name
                    ))
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .addTag("download_${song.id}")
                    .build()

                workManager.enqueueUniqueWork(
                    "download_${song.id}",
                    ExistingWorkPolicy.KEEP,
                    request
                )

                Log.d(TAG, "OfflineManager: Queued download for '${song.title}' (${quality.name})")
            } else {
                Log.d(TAG, "OfflineManager: Skipped enqueuing '${song.title}', already downloading or completed.")
            }
        }
    }

    /** Download all tracks in a playlist */
    fun downloadPlaylist(
        playlistName: String,
        songs: List<Song>,
        quality: DownloadQuality = DownloadQuality.NORMAL,
        wifiOnly: Boolean = true
    ) {
        songs.forEach { song ->
            downloadTrack(song, quality, wifiOnly, fromPlaylist = playlistName)
        }
        Log.d(TAG, "OfflineManager: Queued ${songs.size} tracks from playlist '$playlistName'")
    }

    /** Cancel a pending/active download */
    fun cancelDownload(songId: String) {
        workManager.cancelUniqueWork("download_$songId")
        scope.launch {
            val entity = downloadDao.getBySongId(songId)
            if (entity != null && entity.status != "COMPLETED") {
                entity.filePath?.let { File(it).delete() }
                downloadDao.delete(songId)
            }
        }
        Log.d(TAG, "OfflineManager: Cancelled download for $songId")
    }

    /** Delete a completed download (file + DB record) */
    suspend fun deleteDownload(songId: String) {
        val entity = downloadDao.getBySongId(songId) ?: return
        entity.filePath?.let { path -> File(path).delete() }
        downloadDao.delete(songId)
        Log.d(TAG, "OfflineManager: Deleted download for '${entity.title}'")
    }

    /** Check if a song is available offline */
    suspend fun isAvailableOffline(songId: String): Boolean {
        val entity = downloadDao.getCompletedBySongId(songId) ?: return false
        return entity.filePath?.let { File(it).exists() } == true
    }

    /** Returns the physical absolute path if the song is downloaded and intact, or null otherwise */
    suspend fun getOfflineFilePath(songId: String): String? {
        val entity = downloadDao.getCompletedBySongId(songId) ?: return null
        val path = entity.filePath ?: return null
        return if (File(path).exists()) path else null
    }

    /** Bumps the Last Accessed timestamp so the LRU eviction defers this track */
    suspend fun touchAccess(songId: String) {
        val timestamp = System.currentTimeMillis()
        downloadDao.touchAccess(songId, timestamp)
        Log.d(TAG, "OfflineManager: Touched LRU cache for $songId")
    }

    /** Run LRU eviction to stay under storage limit */
    suspend fun evictIfNeeded(maxBytes: Long = DEFAULT_MAX_STORAGE_BYTES) {
        var used = downloadDao.getTotalStorageUsed() ?: 0L
        while (used > maxBytes) {
            val lru = downloadDao.getLeastRecentlyUsed(1).firstOrNull() ?: break
            lru.filePath?.let { File(it).delete() }
            downloadDao.delete(lru.songId)
            used -= lru.fileSizeBytes
            Log.d(TAG, "OfflineManager: Evicted '${lru.title}' (${lru.fileSizeBytes / 1024}KB)")
        }
    }

    // ── Observable State ──

    /** Re-enqueue a previously FAILED download */
    fun retryFailed(songId: String) {
        scope.launch {
            val entity = downloadDao.getBySongId(songId) ?: return@launch
            if (entity.status != "FAILED") return@launch

            // Delete any stale file remnants
            entity.filePath?.let { File(it).delete() }
            downloadDao.updateProgress(songId, "QUEUED", 0)

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresStorageNotLow(true)
                .build()

            val request = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(workDataOf(
                    DownloadWorker.KEY_SONG_ID to songId,
                    DownloadWorker.KEY_QUALITY to entity.quality
                ))
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .addTag("download_$songId")
                .build()

            workManager.enqueueUniqueWork(
                "download_$songId",
                ExistingWorkPolicy.REPLACE, // Force re-run even if previous is done
                request
            )
            Log.d(TAG, "OfflineManager: Retrying FAILED download for '${entity.title}'")
        }
    }

    /**
     * Verify physical file integrity for all COMPLETED downloads.
     * If a file is missing or zero-bytes, marks the record as FAILED so user can retry.
     */
    suspend fun verifyIntegrity() {
        downloadDao.getLeastRecentlyUsed(Int.MAX_VALUE).forEach { entity ->
            if (entity.status != "COMPLETED") return@forEach
            val file = entity.filePath?.let { File(it) }
            val isCorrupt = file == null || !file.exists() || file.length() == 0L
            if (isCorrupt) {
                file?.delete()
                downloadDao.insertRaw(entity.copy(
                    status = "FAILED",
                    errorMessage = "Archivo no encontrado o corrupto — vuelve a descargar",
                    filePath = null,
                    fileSizeBytes = 0,
                    checksum = null
                ))
                Log.w(TAG, "OfflineManager: Integrity failure for '${entity.title}'")
            }
        }
    }

    /**
     * Schedule a daily periodic cleanup job using WorkManager.
     * Keeps storage usage under [maxBytes] by evicting least-recently-used tracks.
     * Safe to call multiple times — uses KEEP policy.
     */
    fun schedulePeriodicCleanup(maxBytes: Long = DEFAULT_MAX_STORAGE_BYTES) {
        val request = PeriodicWorkRequestBuilder<StorageCleanupWorker>(24, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder().setRequiresBatteryNotLow(true).build()
            )
            .build()

        workManager.enqueueUniquePeriodicWork(
            "offline_storage_cleanup",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
        Log.d(TAG, "OfflineManager: Scheduled periodic storage cleanup (max ${maxBytes / 1024 / 1024}MB)")
    }

    // ── Observable State ──

    fun getStorageUsed(): Flow<Long> = downloadDao.getAll().map { tracks ->
        tracks.filter { it.status == "COMPLETED" }.sumOf { it.fileSizeBytes }
    }

    fun getDownloadCount(): Flow<Int> = downloadDao.getDownloadCount()
    fun getAllDownloads(): Flow<List<DownloadedTrackEntity>> = downloadDao.getAll()
    fun getCompletedDownloads(): Flow<List<DownloadedTrackEntity>> = downloadDao.getCompleted()
    fun getActiveDownloads(): Flow<List<DownloadedTrackEntity>> = downloadDao.getActive()
}
