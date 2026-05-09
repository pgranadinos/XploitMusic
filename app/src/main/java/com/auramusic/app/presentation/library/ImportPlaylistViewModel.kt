package com.auramusic.app.presentation.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auramusic.app.data.local.entity.SpotifyTokenEntity
import com.auramusic.app.data.remote.SpotifyPlaylistDto
import com.auramusic.app.domain.repository.SpotifyRepository
import com.auramusic.app.domain.usecase.ImportPlaylistUseCase
import com.auramusic.app.domain.usecase.ImportProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class SpotifyImportState {
    object CheckingAuth : SpotifyImportState()
    object Unauthenticated : SpotifyImportState()
    object LoadingPlaylists : SpotifyImportState()
    data class PlaylistsLoaded(val playlists: List<SpotifyPlaylistDto>) : SpotifyImportState()
    data class Importing(val progress: ImportProgress) : SpotifyImportState()
    data class Error(val message: String) : SpotifyImportState()
}

@HiltViewModel
class ImportPlaylistViewModel @Inject constructor(
    private val spotifyRepository: SpotifyRepository,
    private val importUseCase: ImportPlaylistUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<SpotifyImportState>(SpotifyImportState.CheckingAuth)
    val uiState: StateFlow<SpotifyImportState> = _uiState

    val userProfile: StateFlow<SpotifyTokenEntity?> = spotifyRepository.observeUserProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        checkAuthStatus()
    }

    private fun checkAuthStatus() {
        viewModelScope.launch {
            spotifyRepository.isAuthenticated().collectLatest { isAuth ->
                if (isAuth) {
                    loadPlaylists()
                } else {
                    _uiState.value = SpotifyImportState.Unauthenticated
                }
            }
        }
    }

    fun getOAuthUrl(): Pair<String, String> {
        return spotifyRepository.buildAuthUrl()
    }

    fun exchangeToken(code: String, verifier: String) {
        viewModelScope.launch {
            val result = spotifyRepository.exchangeCode(code, verifier)
            if (result.isSuccess) {
                spotifyRepository.fetchAndStoreProfile()
                loadPlaylists()
            } else {
                _uiState.value = SpotifyImportState.Error("Error de autenticación")
            }
        }
    }

    fun loadPlaylists() {
        _uiState.value = SpotifyImportState.LoadingPlaylists
        viewModelScope.launch {
            // Refrescar token antes de consultar api
            spotifyRepository.refreshTokenIfNeeded()

            val result = spotifyRepository.getAllPlaylists()
            if (result.isSuccess) {
                _uiState.value = SpotifyImportState.PlaylistsLoaded(result.getOrDefault(emptyList()))
            } else {
                _uiState.value = SpotifyImportState.Error("Error al cargar playlists: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    fun startImport(playlist: SpotifyPlaylistDto) {
        viewModelScope.launch {
            importUseCase.execute(playlist).collectLatest { progress ->
                _uiState.value = SpotifyImportState.Importing(progress)
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            spotifyRepository.logout()
            _uiState.value = SpotifyImportState.Unauthenticated
        }
    }
}
