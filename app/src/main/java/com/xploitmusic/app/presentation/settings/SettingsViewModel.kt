package com.xploitmusic.app.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xploitmusic.app.data.repository.OfflineManager
import com.xploitmusic.app.domain.repository.QueueManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val queueManager: QueueManager,
    private val offlineManager: OfflineManager
) : ViewModel() {

    val isAutoplayEnabled: StateFlow<Boolean> = queueManager.isAutoplayEnabled

    val storageUsedFormatted: StateFlow<String> = offlineManager.getStorageUsed()
        .map { bytes -> formatStorageSize(bytes) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "0 MB")

    fun toggleAutoplay() {
        queueManager.toggleAutoplay()
    }

    fun clearAllDownloads() {
        viewModelScope.launch {
            offlineManager.clearAllDownloads()
        }
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
