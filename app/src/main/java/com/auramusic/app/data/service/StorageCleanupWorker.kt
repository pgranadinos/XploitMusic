package com.auramusic.app.data.service

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.auramusic.app.data.repository.OfflineManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Periodic WorkManager job (every 24h) that:
 * 1. Verifies physical integrity of all COMPLETED downloads
 * 2. Runs LRU eviction to stay under the configured storage limit
 *
 * Scheduled via OfflineManager.schedulePeriodicCleanup().
 */
@HiltWorker
class StorageCleanupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val offlineManager: OfflineManager
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "XploitDebug"
    }

    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "StorageCleanupWorker: Starting integrity check + LRU eviction")
            offlineManager.verifyIntegrity()
            offlineManager.evictIfNeeded()
            Log.d(TAG, "StorageCleanupWorker: ✅ Cleanup complete")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "StorageCleanupWorker: Cleanup failed: ${e.message}", e)
            Result.failure()
        }
    }
}
