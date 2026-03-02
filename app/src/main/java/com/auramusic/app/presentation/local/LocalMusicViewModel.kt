package com.auramusic.app.presentation.local

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auramusic.app.domain.model.Song
import com.auramusic.app.domain.repository.LocalAudioRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LocalMusicViewModel @Inject constructor(
    private val localAudioRepository: LocalAudioRepository
) : ViewModel() {

    companion object {
        private const val TAG = "XploitDebug"
    }

    private val _localSongs = MutableStateFlow<List<Song>>(emptyList())
    val localSongs = _localSongs.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _permissionGranted = MutableStateFlow(false)
    val permissionGranted = _permissionGranted.asStateFlow()

    fun onPermissionResult(granted: Boolean) {
        _permissionGranted.value = granted
        if (granted) loadLocalSongs()
    }

    fun loadLocalSongs() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val songs = localAudioRepository.getLocalSongs()
                _localSongs.value = songs
                Log.d(TAG, "LocalMusicVM: Loaded ${songs.size} local songs")
            } catch (e: Exception) {
                Log.e(TAG, "LocalMusicVM: Error loading local songs: ${e.message}", e)
            } finally {
                _isLoading.value = false
            }
        }
    }
}
