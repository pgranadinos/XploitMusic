package com.auramusic.app.presentation.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auramusic.app.data.local.entity.DownloadedTrackEntity
import com.auramusic.app.data.repository.OfflineManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val offlineManager: OfflineManager
) : ViewModel() {

    companion object {
        // Default cap: 500 MB. User can change via settings slider.
        private const val DEFAULT_MAX_BYTES = 500L * 1024 * 1024
    }

    /** All download records (any status) */
    val allDownloads: StateFlow<List<DownloadedTrackEntity>> =
        offlineManager.getAllDownloads()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Only COMPLETED tracks for the offline library tab */
    val completedDownloads: StateFlow<List<DownloadedTrackEntity>> =
        offlineManager.getCompletedDownloads()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** QUEUED + DOWNLOADING + PAUSED tracks for the active tab */
    val activeDownloads: StateFlow<List<DownloadedTrackEntity>> =
        offlineManager.getActiveDownloads()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** FAILED tracks — shown separately so user can retry */
    val failedDownloads: StateFlow<List<DownloadedTrackEntity>> =
        offlineManager.getAllDownloads()
            .map { tracks -> tracks.filter { it.status == "FAILED" } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Total bytes used by completed downloads */
    val storageUsedBytes: StateFlow<Long> =
        offlineManager.getStorageUsed()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    /** User-configured storage limit in bytes */
    private val _maxStorageBytes = MutableStateFlow(DEFAULT_MAX_BYTES)
    val maxStorageBytes: StateFlow<Long> = _maxStorageBytes.asStateFlow()

    /** WiFi-only toggle preference */
    private val _wifiOnly = MutableStateFlow(true)
    val wifiOnly: StateFlow<Boolean> = _wifiOnly.asStateFlow()

    // ── Actions ──

    fun retryFailed(songId: String) {
        offlineManager.retryFailed(songId)
    }

    fun cancelDownload(songId: String) {
        offlineManager.cancelDownload(songId)
    }

    fun deleteDownload(songId: String) {
        viewModelScope.launch {
            offlineManager.deleteDownload(songId)
        }
    }

    fun clearCacheNow() {
        viewModelScope.launch {
            offlineManager.evictIfNeeded(_maxStorageBytes.value)
        }
    }

    fun setMaxStorageMb(megabytes: Int) {
        _maxStorageBytes.value = megabytes.toLong() * 1024 * 1024
    }

    fun setWifiOnly(wifiOnly: Boolean) {
        _wifiOnly.value = wifiOnly
    }

    /** Format bytes as human-readable string (KB / MB) */
    fun formatBytes(bytes: Long): String = when {
        bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
        bytes >= 1_024     -> "${bytes / 1_024} KB"
        else               -> "$bytes B"
    }
}
