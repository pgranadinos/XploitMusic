package com.xploitmusic.app.presentation.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xploitmusic.app.data.local.entity.SpotifyTokenEntity
import com.xploitmusic.app.data.local.SpotifyConfigurationStore
import com.xploitmusic.app.data.remote.SpotifyPlaylistDto
import com.xploitmusic.app.domain.repository.SpotifyRepository
import com.xploitmusic.app.domain.usecase.ImportPlaylistUseCase
import com.xploitmusic.app.domain.usecase.ImportProgress
import com.xploitmusic.app.domain.model.SpotifyOAuthCallback
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
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
    private val importUseCase: ImportPlaylistUseCase,
    private val configurationStore: SpotifyConfigurationStore
) : ViewModel() {

    private enum class RetryAction { AUTH, PLAYLISTS }

    private val _uiState = MutableStateFlow<SpotifyImportState>(SpotifyImportState.CheckingAuth)
    val uiState: StateFlow<SpotifyImportState> = _uiState

    val userProfile: StateFlow<SpotifyTokenEntity?> = spotifyRepository.observeUserProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val spotifyClientId: StateFlow<String> = configurationStore.clientId

    private val _authUrlEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val authUrlEvents: SharedFlow<String> = _authUrlEvents.asSharedFlow()

    private var retryAction = RetryAction.PLAYLISTS
    private var playlistLoadJob: Job? = null
    private var importJob: Job? = null

    init {
        checkAuthStatus()
        observeOAuthCallbacks()
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

    private fun observeOAuthCallbacks() {
        viewModelScope.launch {
            spotifyRepository.observeOAuthCallbacks().collect { callback ->
                handleOAuthCallback(callback)
            }
        }
    }

    fun startOAuth(clientId: String = "") {
        if (clientId.isNotBlank()) {
            configurationStore.saveClientId(clientId)
        }
        val activeClientId = spotifyClientId.value
        if (activeClientId.isBlank()) {
            showAuthError("Client ID de Spotify no configurado en el proyecto")
            return
        }
        _uiState.value = SpotifyImportState.CheckingAuth
        viewModelScope.launch {
            spotifyRepository.buildAuthUrl()
                .onSuccess { _authUrlEvents.emit(it) }
                .onFailure { error ->
                    showAuthError(error.message ?: "No se pudo iniciar Spotify")
                }
        }
    }

    private fun handleOAuthCallback(callback: SpotifyOAuthCallback) {
        val error = callback.error
        if (error != null) {
            showAuthError(error)
            return
        }

        val code = callback.code
        val state = callback.state
        if (code.isNullOrBlank() || state.isNullOrBlank()) {
            showAuthError("La respuesta de Spotify está incompleta")
            return
        }

        viewModelScope.launch {
            _uiState.value = SpotifyImportState.CheckingAuth
            val result = spotifyRepository.exchangeCode(code, state)
            if (result.isSuccess) {
                spotifyRepository.fetchAndStoreProfile()
            } else {
                showAuthError(
                    "Error de autenticación: ${result.exceptionOrNull()?.message ?: "respuesta inválida"}"
                )
            }
        }
    }

    private fun showAuthError(message: String) {
        retryAction = RetryAction.AUTH
        _uiState.value = SpotifyImportState.Error(message)
    }

    fun loadPlaylists() {
        if (playlistLoadJob?.isActive == true) return
        retryAction = RetryAction.PLAYLISTS
        _uiState.value = SpotifyImportState.LoadingPlaylists
        playlistLoadJob = viewModelScope.launch {
            val result = spotifyRepository.getAllPlaylists()
            if (result.isSuccess) {
                _uiState.value = SpotifyImportState.PlaylistsLoaded(result.getOrDefault(emptyList()))
            } else {
                _uiState.value = SpotifyImportState.Error("Error al cargar playlists: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    fun startImport(playlist: SpotifyPlaylistDto) {
        if (importJob?.isActive == true) return
        val ownerId = playlist.owner?.id
        if (ownerId.isNullOrBlank()) {
            retryAction = RetryAction.PLAYLISTS
            _uiState.value = SpotifyImportState.Error("No se puede determinar el propietario de la playlist.")
            return
        }
        val currentUserId = userProfile.value?.spotifyUserId.orEmpty()
        val isOwner = ownerId == currentUserId
        val isCollaborative = playlist.collaborative
        if (!isOwner && !isCollaborative) {
            retryAction = RetryAction.PLAYLISTS
            _uiState.value = SpotifyImportState.Error("Solo se pueden importar playlists propias o colaborativas.")
            return
        }

        importJob = viewModelScope.launch {
            importUseCase.execute(playlist).collectLatest { progress ->
                val error = progress.errorMessage
                if (error != null) {
                    retryAction = RetryAction.PLAYLISTS
                    _uiState.value = SpotifyImportState.Error(error)
                } else {
                    _uiState.value = SpotifyImportState.Importing(progress)
                }
            }
        }
    }

    fun cancelImport() {
        importJob?.cancel()
        importJob = null
        loadPlaylists()
    }

    fun retry() {
        when (retryAction) {
            RetryAction.AUTH -> _uiState.value = SpotifyImportState.Unauthenticated
            RetryAction.PLAYLISTS -> loadPlaylists()
        }
    }

    fun logout() {
        viewModelScope.launch {
            spotifyRepository.logout()
            _uiState.value = SpotifyImportState.Unauthenticated
        }
    }
}
