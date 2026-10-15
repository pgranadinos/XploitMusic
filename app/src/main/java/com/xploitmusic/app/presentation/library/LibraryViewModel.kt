package com.xploitmusic.app.presentation.library

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xploitmusic.app.data.local.entity.PlaylistWithSongs
import com.xploitmusic.app.domain.model.Song
import com.xploitmusic.app.domain.repository.FavoritesRepository
import com.xploitmusic.app.domain.repository.PlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val favoritesRepository: FavoritesRepository
) : ViewModel() {

    companion object {
        private const val TAG = "XploitDebug"
    }

    // ── Playlists (with songs pre-loaded, ordered by position) ──
    val allPlaylistsWithSongs: StateFlow<List<PlaylistWithSongs>> =
        playlistRepository.getAllPlaylistsWithSongs()
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _selectedPlaylist = MutableStateFlow<PlaylistWithSongs?>(null)
    val selectedPlaylist = _selectedPlaylist.asStateFlow()
    private var selectionJob: Job? = null

    private val _showCreateDialog = MutableStateFlow(false)
    val showCreateDialog = _showCreateDialog.asStateFlow()

    // ── Favorites ──
    val favorites: StateFlow<List<Song>> = favoritesRepository.getFavorites()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // ── Recents ──
    val recents: StateFlow<List<Song>> = favoritesRepository.getRecents()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // ── Most Played (NEW) ──
    val mostPlayed: StateFlow<List<Song>> = favoritesRepository.getMostPlayed()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // ── Dialog actions ──
    fun showCreatePlaylistDialog() { _showCreateDialog.value = true }
    fun dismissCreatePlaylistDialog() { _showCreateDialog.value = false }

    fun createPlaylist(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val id = playlistRepository.createPlaylist(name.trim())
            Log.d(TAG, "LibraryVM: Created playlist '$name' with id=$id")
        }
        _showCreateDialog.value = false
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            playlistRepository.deletePlaylist(playlistId)
            if (_selectedPlaylist.value?.playlist?.playlistId == playlistId) {
                clearSelection()
            }
            Log.d(TAG, "LibraryVM: Deleted playlist $playlistId")
        }
    }

    fun selectPlaylist(playlistId: Long) {
        selectionJob?.cancel()
        selectionJob = viewModelScope.launch {
            playlistRepository.getPlaylistWithSongs(playlistId).collect { pws ->
                _selectedPlaylist.value = pws
            }
        }
    }

    fun clearSelection() {
        selectionJob?.cancel()
        selectionJob = null
        _selectedPlaylist.value = null
    }

    suspend fun renamePlaylist(playlistId: Long, name: String): Boolean {
        return playlistRepository.renamePlaylist(playlistId, name)
    }

    fun addSongToPlaylist(playlistId: Long, song: Song) {
        viewModelScope.launch {
            playlistRepository.addSongToPlaylist(playlistId, song)
            Log.d(TAG, "LibraryVM: Added '${song.title}' to playlist $playlistId")
        }
    }

    fun removeSongFromPlaylist(playlistId: Long, songId: String) {
        viewModelScope.launch {
            playlistRepository.removeSongFromPlaylist(playlistId, songId)
            Log.d(TAG, "LibraryVM: Removed song $songId from playlist $playlistId")
        }
    }

    fun reorderPlaylist(playlistId: Long, fromPosition: Int, toPosition: Int) {
        viewModelScope.launch {
            playlistRepository.moveSongInPlaylist(playlistId, fromPosition, toPosition)
            Log.d(TAG, "LibraryVM: Reordered playlist $playlistId from $fromPosition to $toPosition")
        }
    }

    fun toggleFavorite(song: Song) {
        viewModelScope.launch {
            favoritesRepository.toggleFavorite(song)
        }
    }
}
