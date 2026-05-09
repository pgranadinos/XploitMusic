package com.auramusic.app.utils

import android.util.Log

object AppLogger {
    private const val TAG_SEARCH = "AuraMetrics_Search"
    private const val TAG_PLAYBACK = "AuraMetrics_Playback"
    private const val TAG_DOWNLOAD = "AuraMetrics_Download"

    private const val SEARCH_THRESHOLD_MS = 1500L
    private const val PLAYBACK_THRESHOLD_MS = 2500L

    fun logSearch(query: String, success: Boolean, timeMs: Long, error: String? = null, resultCount: Int = 0) {
        val payload = buildString {
            append("Query: [$query] | ")
            append("Success: $success | ")
            append("Time: ${timeMs}ms | ")
            append("Results: $resultCount")
            if (error != null) append(" | Error: $error")
        }

        if (!success || error != null) {
            Log.e(TAG_SEARCH, "[FAIL] $payload")
        } else if (timeMs > SEARCH_THRESHOLD_MS) {
            Log.w(TAG_SEARCH, "[SLOW] $payload")
        } else {
            Log.i(TAG_SEARCH, "[OK] $payload")
        }
    }

    fun logPlaybackState(songId: String, state: String, bufferingTimeMs: Long? = null) {
        val payload = buildString {
            append("SongID: [$songId] | ")
            append("State: $state")
            if (bufferingTimeMs != null) append(" | BufferingTime: ${bufferingTimeMs}ms")
        }

        if (state == "ERROR") {
            Log.e(TAG_PLAYBACK, "[FAIL] $payload")
        } else if (bufferingTimeMs != null && bufferingTimeMs > PLAYBACK_THRESHOLD_MS) {
            Log.w(TAG_PLAYBACK, "[SLOW_BUFFER] $payload")
        } else {
            Log.d(TAG_PLAYBACK, "[STATE_CHANGE] $payload")
        }
    }

    fun logOfflineAction(trackId: String, action: String, status: String, bytes: Long? = null) {
        val payload = buildString {
            append("Task: $action | ")
            append("Track: [$trackId] | ")
            append("Status: $status")
            if (bytes != null) append(" | Bytes: $bytes")
        }

        if (status == "FAILED" || status == "ERROR") {
            Log.e(TAG_DOWNLOAD, "[CRITICAL] $payload")
        } else {
            Log.i(TAG_DOWNLOAD, "[INFO] $payload")
        }
    }
}
