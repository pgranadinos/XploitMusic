package com.auramusic.app.data.service

import android.net.Uri
import android.util.Log
import com.auramusic.app.data.local.dao.DownloadDao
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves the best media URI for a song before sending it to ExoPlayer.
 *
 * Priority:
 *  1. Local downloaded file (offline playback) — if COMPLETED and file exists
 *  2. Remote stream URL — fallback for online playback
 *
 * By centralising this logic here, ExoPlayer never needs to know whether it
 * is playing a cached file or a live stream.
 */
@Singleton
class OfflineAwareMediaSourceFactory @Inject constructor(
    private val downloadDao: DownloadDao
) {
    companion object {
        private const val TAG = "XploitDebug"
    }

    /**
     * Returns a [Uri] for [songId]:
     *  - `Uri.fromFile()` if a valid local file exists
     *  - `Uri.parse(fallbackUrl)` otherwise
     *
     * Also touches [DownloadDao.touchAccess] to keep LRU accurate.
     */
    suspend fun resolveUri(songId: String, fallbackUrl: String): Uri {
        val completed = downloadDao.getCompletedBySongId(songId)
        if (completed != null) {
            val file = completed.filePath?.let { File(it) }
            if (file != null && file.exists() && file.length() > 0) {
                downloadDao.touchAccess(songId, System.currentTimeMillis())
                Log.d(TAG, "OfflineMedia: Playing from local file for $songId")
                return Uri.fromFile(file)
            } else {
                Log.w(TAG, "OfflineMedia: File missing for $songId, falling back to stream")
            }
        }
        return Uri.parse(fallbackUrl)
    }

    /** Convenience: Returns true if a valid local file exists for [songId] */
    suspend fun isAvailableOffline(songId: String): Boolean {
        val entity = downloadDao.getCompletedBySongId(songId) ?: return false
        return entity.filePath?.let { File(it).exists() && File(it).length() > 0 } == true
    }
}
