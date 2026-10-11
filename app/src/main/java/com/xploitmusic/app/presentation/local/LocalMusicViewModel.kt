package com.xploitmusic.app.presentation.local

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xploitmusic.app.domain.model.LocalAudioFolder
import com.xploitmusic.app.domain.model.Song
import com.xploitmusic.app.domain.repository.LocalAudioRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
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

    private val _selectedFolders = MutableStateFlow<List<LocalAudioFolder>>(emptyList())
    val selectedFolders = _selectedFolders.asStateFlow()
    private var loadJob: Job? = null

    init {
        refreshSelectedFolders()
    }

    fun onPermissionResult(granted: Boolean) {
        _permissionGranted.value = granted
        if (!granted) {
            loadJob?.cancel()
            _localSongs.value = emptyList()
            _isLoading.value = false
        }
        refreshSelectedFolders()
        if (granted && _selectedFolders.value.isNotEmpty()) loadLocalSongs()
    }

    fun addSelectedFolder(uri: String) {
        if (localAudioRepository.addSelectedFolder(uri)) {
            refreshSelectedFolders()
            if (_permissionGranted.value) loadLocalSongs()
        }
    }

    fun removeSelectedFolder(uri: String) {
        localAudioRepository.removeSelectedFolder(uri)
        refreshSelectedFolders()
        if (_permissionGranted.value) loadLocalSongs()
    }

    private fun refreshSelectedFolders() {
        _selectedFolders.value = localAudioRepository.getSelectedFolders()
    }

    fun loadLocalSongs() {
        loadJob?.cancel()
        _localSongs.value = emptyList()
        loadJob = viewModelScope.launch {
            _isLoading.value = true
            try {
                val songs = localAudioRepository.getLocalSongs()
                currentCoroutineContext().ensureActive()
                _localSongs.value = songs
                Log.d(TAG, "LocalMusicVM: Loaded ${songs.size} local songs")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "LocalMusicVM: Error loading local songs: ${e.message}", e)
            } finally {
                if (currentCoroutineContext().isActive) _isLoading.value = false
            }
        }
    }
}
