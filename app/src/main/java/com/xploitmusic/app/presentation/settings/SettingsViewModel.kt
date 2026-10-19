package com.xploitmusic.app.presentation.settings

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xploitmusic.app.core.util.PlaybackLogManager
import com.xploitmusic.app.data.repository.OfflineManager
import com.xploitmusic.app.domain.repository.QueueManager
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
class SettingsViewModel @Inject constructor(
    private val queueManager: QueueManager,
    private val offlineManager: OfflineManager,
    private val playbackLogManager: PlaybackLogManager
) : ViewModel() {

    val isAutoplayEnabled: StateFlow<Boolean> = queueManager.isAutoplayEnabled

    val storageUsedFormatted: StateFlow<String> = offlineManager.getStorageUsed()
        .map { bytes -> formatStorageSize(bytes) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "0 MB")

    private val _recentAuditLogs = MutableStateFlow("")
    val recentAuditLogs: StateFlow<String> = _recentAuditLogs.asStateFlow()

    fun toggleAutoplay() {
        queueManager.toggleAutoplay()
    }

    fun clearAllDownloads() {
        viewModelScope.launch {
            offlineManager.clearAllDownloads()
        }
    }

    fun loadRecentLogs() {
        viewModelScope.launch {
            _recentAuditLogs.value = playbackLogManager.readLastLines(200)
        }
    }

    fun markManualProblem() {
        playbackLogManager.markManualProblem()
        loadRecentLogs()
    }

    fun clearLogs() {
        viewModelScope.launch {
            playbackLogManager.clearLogs()
            _recentAuditLogs.value = "Bitácora borrada."
        }
    }

    fun getShareLogIntent(): Intent? {
        return playbackLogManager.getShareIntent()
    }

    private fun formatStorageSize(bytes: Long): String {
        if (bytes <= 0) return "0 MB"
        val mb = bytes.toDouble() / (1024 * 1024)
        return if (mb >= 1024) {
            String.format(java.util.Locale.US, "%.2f GB", mb / 1024)
        } else {
            String.format(java.util.Locale.US, "%.1f MB", mb)
        }
    }
}
