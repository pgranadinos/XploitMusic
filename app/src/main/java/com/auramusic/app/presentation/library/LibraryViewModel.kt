package com.auramusic.app.presentation.library

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auramusic.app.data.local.dao.PlaylistDao
import com.auramusic.app.data.local.dao.SongDao
import com.auramusic.app.data.local.entity.PlaylistSongCrossRef
import com.auramusic.app.data.local.entity.PlaylistWithSongs
import com.auramusic.app.data.local.entity.SongEntity
import com.auramusic.app.domain.model.Song
import com.auramusic.app.domain.repository.FavoritesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.auramusic.app.data.local.entity.PlaylistEntity
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val playlistDao: PlaylistDao,
    private val songDao: SongDao,
    private val favoritesRepository: FavoritesRepository
) : ViewModel() {

    companion object {
        private const val TAG = "XploitDebug"
    }

    // ── Playlists (with songs pre-loaded, ordered by position) ──
    val allPlaylistsWithSongs: StateFlow<List<PlaylistWithSongs>> =
        playlistDao.getAllPlaylistsWithSongs()
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _selectedPlaylist = MutableStateFlow<PlaylistWithSongs?>(null)
    val selectedPlaylist = _selectedPlaylist.asStateFlow()

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
            val entity = PlaylistEntity(name = name.trim())
            val id = playlistDao.createPlaylist(entity)
            Log.d(TAG, "LibraryVM: Created playlist '$name' with id=$id")
        }
        _showCreateDialog.value = false
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            playlistDao.deletePlaylist(playlistId)
            if (_selectedPlaylist.value?.playlist?.playlistId == playlistId) {
                _selectedPlaylist.value = null
            }
            Log.d(TAG, "LibraryVM: Deleted playlist $playlistId")
        }
    }

    fun selectPlaylist(playlistId: Long) {
        viewModelScope.launch {
            playlistDao.getPlaylistWithSongs(playlistId).collect { pws ->
                _selectedPlaylist.value = pws
            }
        }
    }

    fun clearSelection() {
        _selectedPlaylist.value = null
    }

    fun addSongToPlaylist(playlistId: Long, song: Song) {
        viewModelScope.launch {
            songDao.upsertNetworkSong(
                SongEntity(
                    id = song.id,
                    title = song.title,
                    artist = song.artist,
                    thumbnailUrl = song.thumbnailUrl,
                    durationSeconds = song.durationSeconds,
                    streamUrl = song.streamUrl
                )
            )
            playlistDao.addSongToPlaylist(
                PlaylistSongCrossRef(playlistId = playlistId, songId = song.id)
            )
            Log.d(TAG, "LibraryVM: Added '${song.title}' to playlist $playlistId")
        }
    }

    fun removeSongFromPlaylist(playlistId: Long, songId: String) {
        viewModelScope.launch {
            playlistDao.removeSongFromPlaylist(playlistId, songId)
            Log.d(TAG, "LibraryVM: Removed song $songId from playlist $playlistId")
        }
    }

    fun reorderPlaylist(playlistId: Long, fromPosition: Int, toPosition: Int) {
        viewModelScope.launch {
            playlistDao.moveSong(playlistId, fromPosition, toPosition)
            Log.d(TAG, "LibraryVM: Reordered playlist $playlistId from $fromPosition to $toPosition")
        }
    }

    fun toggleFavorite(song: Song) {
        viewModelScope.launch {
            favoritesRepository.toggleFavorite(song)
        }
    }
}
