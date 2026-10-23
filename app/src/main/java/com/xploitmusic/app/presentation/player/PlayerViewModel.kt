package com.xploitmusic.app.presentation.player

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.xploitmusic.app.core.util.Resource
import com.xploitmusic.app.data.local.entity.PlaylistEntity
import com.xploitmusic.app.domain.repository.PlaylistRepository
import com.xploitmusic.app.data.local.entity.DownloadedTrackEntity
import com.xploitmusic.app.data.repository.OfflineManager
import com.xploitmusic.app.data.service.SkipHandler
import com.xploitmusic.app.domain.model.Song
import com.xploitmusic.app.domain.repository.FavoritesRepository
import com.xploitmusic.app.domain.repository.PlayerRepository
import com.xploitmusic.app.domain.repository.QueueManager
import com.xploitmusic.app.domain.repository.StreamRepository
import com.xploitmusic.app.domain.recommendation.SongRecommendationSelector
import com.xploitmusic.app.domain.usecase.GetAudioStreamUseCase
import com.xploitmusic.app.domain.usecase.ToggleFavoriteUseCase
import com.xploitmusic.app.core.analytics.XploitAnalyticsLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import com.xploitmusic.app.domain.model.PlaybackState
import com.xploitmusic.app.domain.model.RepeatMode

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playerRepository: PlayerRepository,
    private val getAudioStreamUseCase: GetAudioStreamUseCase,
    private val getLyricsUseCase: com.xploitmusic.app.domain.usecase.GetLyricsUseCase,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    private val favoritesRepository: FavoritesRepository,
    private val streamRepository: StreamRepository,
    private val skipHandler: SkipHandler,
    private val playlistRepository: PlaylistRepository,
    private val queueManager: QueueManager,
    private val offlineManager: OfflineManager,
    private val analyticsLogger: XploitAnalyticsLogger,
    private val playbackLogManager: com.xploitmusic.app.core.util.PlaybackLogManager
) : ViewModel(), SkipHandler.Delegate {

    // ── Step 1: Explicit Playback State Machine ──

    companion object {
        private const val TAG = "XploitDebug"
        private const val MAX_CONSECUTIVE_AUTO_SKIPS = 5
    }

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong = _currentSong.asStateFlow()
    private val _sessionChecked = MutableStateFlow(false)
    val sessionChecked = _sessionChecked.asStateFlow()

    private val _lyrics =
        MutableStateFlow<List<com.xploitmusic.app.domain.model.LyricLine>>(emptyList())
    val lyrics = _lyrics.asStateFlow()

    private val _isLyricsLoading = MutableStateFlow(false)
    val isLyricsLoading: StateFlow<Boolean> = _isLyricsLoading.asStateFlow()

    val upNext: StateFlow<List<Song>> = queueManager.upNext

    // QueueManager keeps this setting alive after the UI lifecycle ends.
    val autoplayEnabled: StateFlow<Boolean> = queueManager.isAutoplayEnabled

    // Shuffle & Repeat states
    val isShuffleEnabled: StateFlow<Boolean> = queueManager.isShuffleEnabled

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    // ── Step 1+2: State-based playback tracking ──
    private val _playbackState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    // Derived loading state for backward compat with UI
    val isLoadingStream: StateFlow<Boolean> = _playbackState
        .map { it is PlaybackState.Buffering }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // Error message
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    private var pendingResumePositionMs: Long? = null
    private var pendingResumePositionSongId: String? = null
    private var currentSongRetryCount = 0

    // Track the pre-fetched next song so we can update _currentSong on transition
    private var _prefetchedNextSong: Song? = null
    private var _prefetchJob: Job? = null
    
    // Performance: Job to debounce heavy DB/Network loads on quick skips
    private var _transitionDebounceJob: Job? = null
    private var _lyricsJob: Job? = null

    // Job for skip-to-next — cancelled on each new press to prevent race conditions
    private var _skipJob: Job? = null

    // Job for any explicit playInternal launch (playSong/playSingleSong/playHistorySong)
    // Cancels stale fetches when user navigates quickly
    private var _playInternalJob: Job? = null

    // Generation counter: increments on every new playInternal start.
    // Stale HTTP completions check this to avoid overwriting state.
    @Volatile private var _playGeneration: Long = 0L

    // Flag to ignore transitions caused by our own prepare() calls
    private var _ignoringTransition = false

    // History stack for bidirectional navigation (like browser back/forward)
    // _historyCursor points to current position; -1 = at the "live" head of history
    private val _historyStack = ArrayDeque<Song>(50)
    private var _historyCursor = -1  // -1 = at live head (normal playback mode)

    // Favorite state — reacts to currentSong changes
    val isFavorite: StateFlow<Boolean> = _currentSong
        .flatMapLatest { song ->
            if (song != null) favoritesRepository.isFavorite(song.id)
            else flowOf(false)
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    // Download state for current song
    val downloadState: StateFlow<DownloadedTrackEntity?> = _currentSong
        .flatMapLatest { song ->
            if (song != null) {
                // Re-emits whenever any download changes, then filters to our song
                offlineManager.getAllDownloads().map { list -> list.find { it.songId == song.id } }
            } else {
                flowOf(null)
            }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val isPlaying: StateFlow<Boolean> = playerRepository.isPlaying
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    val currentPosition: StateFlow<Long> = playerRepository.currentPosition
        .stateIn(viewModelScope, SharingStarted.Lazily, 0L)

    val duration: StateFlow<Long> = playerRepository.duration
        .stateIn(viewModelScope, SharingStarted.Lazily, 0L)

    // All playlists for "Add to Playlist" sheet
    val playlists: StateFlow<List<PlaylistEntity>> = playlistRepository.getAllPlaylists()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        skipHandler.attach(this)

        // ── BACKGROUND SYNC: Restore _currentSong from ExoPlayer when returning from background ──
        // CRITICAL FIX (Bug 3): Changed from one-shot to continuous sync.
        // If the ViewModel is (re)created but ExoPlayer already has a song loaded,
        // this collector picks it up so the UI never shows "No media...".
        // It also handles the case where MediaController reconnects after service restart.
        viewModelScope.launch {
            playerRepository.currentSong.collect { songFromPlayer ->
                if (songFromPlayer != null && _currentSong.value == null) {
                    Log.d(TAG, "PlayerVM: 🔄 Background sync — restoring '${songFromPlayer.title}' from MediaController")
                    _currentSong.value = songFromPlayer
                    _playbackState.value = PlaybackState.Playing(songFromPlayer, 0L)
                    _errorMessage.value = null
                } else if (songFromPlayer != null && _currentSong.value != null
                    && _playbackState.value is PlaybackState.Idle
                    && songFromPlayer.id == _currentSong.value?.id) {
                    // Recover from Idle state if ExoPlayer still has the same song loaded
                    Log.d(TAG, "PlayerVM: 🔄 Recovering from Idle — ExoPlayer still has '${songFromPlayer.title}'")
                    _playbackState.value = PlaybackState.Playing(songFromPlayer, 0L)
                    _errorMessage.value = null
                }
                _sessionChecked.value = true
            }
        }

        // ── PERIODIC RECONNECTION FALLBACK ──
        // If after 5 seconds the UI still shows no song but the service is alive,
        // force a re-read from the player repository.
        viewModelScope.launch {
            delay(5000)
            if (_currentSong.value == null) {
                Log.d(TAG, "PlayerVM: 🔄 Periodic fallback — checking if ExoPlayer has a song...")
                try {
                    // Force collect one value from currentSong
                    playerRepository.currentSong.collect { songFromPlayer ->
                        if (songFromPlayer != null) {
                            Log.d(TAG, "PlayerVM: 🔄 Periodic fallback — found '${songFromPlayer.title}'")
                            _currentSong.value = songFromPlayer
                            _playbackState.value = PlaybackState.Playing(songFromPlayer, 0L)
                            _errorMessage.value = null
                        }
                        // Only take the first emission
                        return@collect
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "PlayerVM: Periodic fallback failed: ${e.message}")
                }
            }
        }

        // CRITICAL FIX: Single Source of Truth from ExoPlayer's mediaItemTransition.
        // This fires when ExoPlayer successfully loads a new item (manual or auto-skip).
        viewModelScope.launch {
            playerRepository.mediaItemTransition.collect { mediaId ->
                Log.d(TAG, "PlayerVM: mediaItemTransition Event — mediaId='$mediaId'")

                if (mediaId == null) return@collect

                // Suppress stale ExoPlayer events fired during clearQueue/prepare
                // to prevent _currentSong reverting to old song while buffering next.
                if (_ignoringTransition) {
                    Log.d(TAG, "PlayerVM: Suppressing stale transition during skip: '$mediaId'")
                    return@collect
                }

                // Find the song matching the mediaId from predefined queues
                val song = findSongByMediaId(mediaId)
                if (song != null) {
                    Log.d(TAG, "PlayerVM: 🎵 Transition matched known song: '${song.title}'")
                    onSongTransitioned(song)
                } else {
                    Log.w(TAG, "PlayerVM: ⚠️ Transition mediaId unknown: '$mediaId'. UI might desync.")
                }
            }
        }

        // Listen for ExoPlayer errors (network failures, decode errors, etc.)
        viewModelScope.launch {
            playerRepository.playerError.collect { playerErr ->
                val errorMsg = playerErr.message
                val code = playerErr.code
                val codeName = playerErr.codeName
                Log.e(TAG, "PlayerVM: Player error received — code=$code ($codeName), msg=$errorMsg")

                val currentSong = _currentSong.value
                val isRetryableCode = code == androidx.media3.common.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ||
                    code == androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                    code == androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
                    code == androidx.media3.common.PlaybackException.ERROR_CODE_IO_UNSPECIFIED ||
                    codeName.contains("403") ||
                    errorMsg.contains("Source error") ||
                    errorMsg.contains("HTTP")

                playbackLogManager.log(
                    "DEBUG",
                    "INFO",
                    "DEBUG_DIAGNOSTIC | code=$code ($codeName) | msg='$errorMsg' | songId=${currentSong?.id} | retryCount=$currentSongRetryCount | gen=$_playGeneration | isRetryable=$isRetryableCode"
                )

                if (currentSong != null && currentSongRetryCount < 2 && isRetryableCode) {
                    val capturedGen = _playGeneration
                    val capturedSongId = currentSong.id
                    val savedPosition = currentPosition.value

                    currentSongRetryCount++
                    pendingResumePositionMs = if (savedPosition > 1000L) savedPosition else null
                    pendingResumePositionSongId = capturedSongId

                    streamRepository.invalidateStream(capturedSongId)

                    Log.w(TAG, "PlayerVM: 🔄 Stream error for '${currentSong.title}' ($codeName). Retrying fetch (attempt $currentSongRetryCount)...")
                    playbackLogManager.log("PLAYBACK", "WARN", "Retrying stream fetch for song '${currentSong.title}' ($capturedSongId) due to error $codeName ($errorMsg) (attempt $currentSongRetryCount)")

                    viewModelScope.launch {
                        delay(currentSongRetryCount * 1000L) // Increasing backoff between retries (1s, 2s)
                        if (_playGeneration == capturedGen && _currentSong.value?.id == capturedSongId) {
                            playInternal(currentSong, capturedGen, isRetry = true)
                        } else {
                            Log.d(TAG, "PlayerVM: Playback target or generation changed during retry — ignoring retry.")
                        }
                    }
                } else {
                    val isNoNetwork = playbackLogManager.currentNetworkType.value == "Sin red"
                    if (isNoNetwork && currentSong != null) {
                        val savedPosition = currentPosition.value
                        pendingResumePositionMs = if (savedPosition > 1000L) savedPosition else null
                        pendingResumePositionSongId = currentSong.id
                        val friendlyMsg = "Sin conexión a internet — la reproducción se reanudará al reconectar."
                        _errorMessage.value = friendlyMsg
                        _playbackState.value = PlaybackState.Error(friendlyMsg)
                        playbackLogManager.log("PLAYBACK", "WARN", "Reproducción pausada por falta de red en '${currentSong.title}' a los ${savedPosition}ms")
                    } else {
                        val friendlyMsg = makeFriendlyError(errorMsg)
                        _errorMessage.value = friendlyMsg
                        _playbackState.value = PlaybackState.Error(friendlyMsg)
                        triggerAutoSkip()
                    }
                }
            }
        }

        // Observe network changes during active playback to invalidate old URLs and resolve fresh streams
        viewModelScope.launch {
            var previousNetworkType = playbackLogManager.currentNetworkType.value
            playbackLogManager.networkEvents.collect { newNetworkType ->
                val song = _currentSong.value
                val state = _playbackState.value
                val isPlayingOrBuffering = isPlaying.value || state is PlaybackState.Playing || state is PlaybackState.Buffering
                val isWaitingForNetwork = pendingResumePositionSongId == song?.id && (state is PlaybackState.Error || !isPlaying.value)

                playbackLogManager.log(
                    "DEBUG",
                    "INFO",
                    "DEBUG_DIAGNOSTIC_NETWORK | previous='$previousNetworkType' | new='$newNetworkType' | songId=${song?.id} | isPlaying=${isPlaying.value} | playbackState=$state | waitingForNet=$isWaitingForNetwork"
                )

                if (song != null && (isPlayingOrBuffering || isWaitingForNetwork) && previousNetworkType != "UNKNOWN" && previousNetworkType != newNetworkType && newNetworkType != "Sin red") {
                    val savedPosition = pendingResumePositionMs ?: currentPosition.value
                    val capturedGen = _playGeneration
                    val capturedSongId = song.id

                    pendingResumePositionMs = if (savedPosition > 1000L) savedPosition else null
                    pendingResumePositionSongId = capturedSongId

                    streamRepository.invalidateStream(capturedSongId)
                    playbackLogManager.log("NETWORK", "WARN", "Red restablecida ($previousNetworkType -> $newNetworkType). Resolviendo stream fresco para '${song.title}' desde ${savedPosition}ms")

                    playInternal(song, capturedGen, isRetry = true)
                }
                previousNetworkType = newNetworkType
            }
        }
    }

    fun requestLyrics() {
        val song = _currentSong.value ?: return
        if (_lyrics.value.isEmpty() && !_isLyricsLoading.value) {
            loadLyrics(song)
        }
    }

    private fun loadLyrics(song: Song) {
        if (_currentSong.value?.id == song.id && _lyrics.value.isNotEmpty()) return

        _lyricsJob?.cancel()
        _lyricsJob = viewModelScope.launch {
            _isLyricsLoading.value = true
            try {
                val result = getLyricsUseCase(
                    song.title,
                    song.artist,
                    song.durationSeconds.toDouble()
                )
                if (_currentSong.value?.id != song.id) return@launch

                _lyrics.value = when (result) {
                    is Resource.Success -> {
                        val lyricsResult = result.data
                        when {
                            lyricsResult == null -> emptyList()
                            lyricsResult.instrumental -> listOf(
                                com.xploitmusic.app.domain.model.LyricLine(0L, "♪ Instrumental ♪")
                            )
                            else -> com.xploitmusic.app.core.util.LrcParser.parse(
                                syncedLyrics = lyricsResult.syncedLyrics,
                                plainLyrics = lyricsResult.plainLyrics,
                                durationSeconds = lyricsResult.duration
                            )
                        }
                    }
                    is Resource.Error -> {
                        Log.w(TAG, "PlayerVM: Lyrics unavailable for '${song.title}': ${result.message}")
                        emptyList()
                    }
                    is Resource.Loading -> emptyList()
                }
                Log.d(TAG, "PlayerVM: Lyrics loaded for '${song.title}': ${_lyrics.value.size} lines")
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (_currentSong.value?.id == song.id) _lyrics.value = emptyList()
                Log.w(TAG, "PlayerVM: Lyrics fetch failed: ${e.message}")
            } finally {
                if (_currentSong.value?.id == song.id) {
                    _isLyricsLoading.value = false
                }
            }
        }
    }

    /**
     * Converts raw error messages into user-friendly text.
     * Catches bot detection, sign-in requirements, and HTTP 403 errors.
     */
    private fun makeFriendlyError(raw: String): String {
        val lower = raw.lowercase()
        return when {
            lower.contains("sign in") || lower.contains("login") ||
                lower.contains("bot") || lower.contains("captcha") ->
                "Servidor ocupado. Saltando a la siguiente..."
            lower.contains("403") || lower.contains("forbidden") ->
                "Acceso restringido. Probando otra fuente..."
            lower.contains("404") || lower.contains("not found") ->
                "Audio no disponible. Saltando..."
            lower.contains("timeout") || lower.contains("timed out") ->
                "Conexión lenta. Reintentando..."
            lower.contains("no internet") || lower.contains("network") ->
                "Sin conexión a internet"
            else -> raw
        }
    }

    /**
     * UNIQUE entry point for UI state changes regarding the current song.
     * Called ONLY when ExoPlayer confirms a transition via mediaItemTransition.
     *
     * CRITICAL: Even for same-song (optimistic-update match), we MUST run
     * QueueManager state must advance even for same-song optimistic transitions.
     */
    private fun onSongTransitioned(song: Song) {
        val isSameSong = _currentSong.value?.id == song.id
        
        if (!isSameSong) {
            _lyricsJob?.cancel()
            _isLyricsLoading.value = false
            _currentSong.value = song
            _lyrics.value = emptyList()
            _errorMessage.value = null
        }

        // ALWAYS: release Buffering state on any ExoPlayer-confirmed transition
        if (_playbackState.value is PlaybackState.Buffering) {
            _playbackState.value = PlaybackState.Playing(song, 0L)
            Log.d(TAG, "PlayerVM: Buffering released by transition — state=PLAYING")
        }

        // ALWAYS: mark as played + update index (prevents same-song prefetch loop)
        queueManager.markAsPlayed(song.id)

        // ALWAYS: push to history stack, but ONLY at live head
        // (don't grow history while navigating backward — that causes infinite append)
        if (_historyCursor < 0) {
            val lastInHistory = _historyStack.lastOrNull()
            if (lastInHistory?.id != song.id) {
                _historyStack.addLast(song)
                if (_historyStack.size > 50) _historyStack.removeFirst()
            }
        }

        if (isSameSong) {
            Log.d(TAG, "PlayerVM: Same-song transition (optimistic match) — side effects applied, skipping debounce")
            return
        }

        // Performance: Debounce heavy operations (DB/Network/Lyrics) by 2 seconds
        _transitionDebounceJob?.cancel()
        _transitionDebounceJob = viewModelScope.launch {
            delay(2000)
            
            // Record play locally (debounced)
            try { favoritesRepository.recordPlay(song) } catch (_: Exception) {}

            loadLyrics(song)

            // Refresh related songs and prefetch (debounced)
            replenishAutoplayQueue(song)
            prefetchNextSong()
            Log.d(TAG, "PlayerVM/Perf: Finished debounce tasks for ${song.title}")
        }
    }

    /**
     * Tries to find a Song object by mediaId from all possible sources.
     */
    private fun findSongByMediaId(mediaId: String): Song? {
         if (_currentSong.value?.id == mediaId) return _currentSong.value
         if (_prefetchedNextSong?.id == mediaId) return _prefetchedNextSong

         return queueManager.queue.value.firstOrNull { it.id == mediaId }
    }

    // ── Editable Queue Actions ──
    fun moveSongInQueue(fromIndex: Int, toIndex: Int) {
        val offset = queueManager.currentIndex.value + 1
        if (fromIndex in upNext.value.indices && toIndex in upNext.value.indices) {
            queueManager.moveInQueue(offset + fromIndex, offset + toIndex)
        }
    }

    fun removeSongFromQueue(index: Int) {
        if (index in upNext.value.indices) {
            queueManager.removeFromQueue(queueManager.currentIndex.value + 1 + index)
        }
    }

    fun playSong(song: Song, playlist: List<Song> = emptyList()) {
        Log.d(TAG, "PlayerVM: playSong called: ${song.title} (id=${song.id}), playlist size=${playlist.size}")
        _autoSkipJob?.cancel()
        _skipJob?.cancel()
        _skipJob = null
        _playInternalJob?.cancel()  // Cancel any stale playInternal
        _ignoringTransition = false
        _historyCursor = -1
        _historyStack.clear()
        queueManager.resetPlayedHistory()
        val requestedQueue = if (playlist.isEmpty()) {
            listOf(song)
        } else if (playlist.any { it.id == song.id }) {
            playlist
        } else {
            listOf(song) + playlist
        }
        val startIndex = requestedQueue.indexOfFirst { it.id == song.id }
        queueManager.setQueue(requestedQueue, startIndex)
        val generation = ++_playGeneration
        _playInternalJob = viewModelScope.launch {
            playInternal(song, generation)
        }
    }

    /**
     * Internal: plays a song from history navigation WITHOUT resetting _historyCursor.
     * Used by skipPrevious/skipNext in history mode only.
     */
    private fun playHistorySong(song: Song, newCursor: Int) {
        Log.d(TAG, "PlayerVM: playHistorySong cursor=$newCursor → '${song.title}'")
        _autoSkipJob?.cancel()
        _skipJob?.cancel()
        _skipJob = null
        _playInternalJob?.cancel()  // Cancel stale forceSkipToNext's playInternal
        _ignoringTransition = false
        _historyCursor = newCursor
        val generation = ++_playGeneration
        _playInternalJob = viewModelScope.launch {
            playInternal(song, generation)
        }
    }

    /**
     * Plays a single song WITHOUT queueing a playlist.
     * Used for search results: clears any existing queue, plays the song,
     * and relies on relatedStreams (autoplay) for the next song.
     */
    fun playSingleSong(song: Song) {
        Log.d(TAG, "PlayerVM: playSingleSong called: ${song.title} (id=${song.id})")
        _skipJob?.cancel()
        _skipJob = null
        _playInternalJob?.cancel()
        _ignoringTransition = false
        _historyCursor = -1
        _historyStack.clear()
        queueManager.resetPlayedHistory()
        queueManager.setQueue(listOf(song), startIndex = 0)
        val generation = ++_playGeneration
        _playInternalJob = viewModelScope.launch {
            playInternal(song, generation)
        }
    }

    /**
     * Core playback function.
     * CRITICAL FIX (FALLO 4): This must NOT use coroutineScope{} because that
     * waits for ALL children to complete (including slow getRelatedSongs).
     * Instead, we launch background tasks independently in viewModelScope.
     */
    private suspend fun playInternal(song: Song, generation: Long = _playGeneration, isRetry: Boolean = false) {
        if (!isRetry) {
            currentSongRetryCount = 0
            pendingResumePositionMs = null
            pendingResumePositionSongId = null
        }
        // OPTIMISTIC UPDATE: Set _currentSong immediately so the UI shows the song
        // while we fetch the stream URL. ExoPlayer transition will confirm later.
        _currentSong.value = song
        _lyrics.value = emptyList()
        loadLyrics(song)
        playbackLogManager.logAudioOutputRoute()
        Log.d(TAG, "PlayerVM: playInternal command for '${song.title}' gen=$generation")
        val bufferStartTime = System.currentTimeMillis()
        analyticsLogger.logPlayAttempt(song.id, "PlayerViewModel")
        
        _errorMessage.value = null
        _playbackState.value = PlaybackState.Buffering(song)
        _prefetchedNextSong = song // Temporarily hold it so findSongByMediaId can resolve it
        _prefetchJob?.cancel()

        // Ignore the synchronous transition emitted while setMediaItem replaces the source.
        _ignoringTransition = true

        // prepare() uses setMediaItem(), which atomically replaces the native queue.
        // Clearing first creates a false STATE_ENDED and must never be done here.
        try {
            playerRepository.pause()
        } catch (e: Exception) {
            Log.w(TAG, "PlayerVM: Pause failed: ${e.message}")
        }

        val isLocal = song.id.startsWith("local_")
        val offlinePath = offlineManager.getOfflineFilePath(song.id)

        // ── OFFLINE DEGRADATION (C5): Play downloaded instance if available ──
        if (offlinePath != null) {
            try {
                Log.d(TAG, "PlayerVM: 📶✅ Degradación Elegante — Jugando archivo offline ($offlinePath)")
                playerRepository.prepare(offlinePath, song)
                playerRepository.play()
                _ignoringTransition = false // BUG 1 FIX: Reset flag so future transitions work
                _playbackState.value = PlaybackState.Playing(song, 0L)
                _errorMessage.value = null
                val timeMs = System.currentTimeMillis() - bufferStartTime
                com.xploitmusic.app.utils.AppLogger.logPlaybackState(song.id, "OFFLINE_PLAY", timeMs)
                analyticsLogger.logPlaySuccess(song.id, timeMs)
                resetAutoSkipCounter() // BUG 7: Reset on successful play
                
                // Track usage to avoid LRU Eviction
                viewModelScope.launch { offlineManager.touchAccess(song.id) }
            } catch (e: Exception) {
                Log.e(TAG, "PlayerVM: ❌ Offline playback error: ${e.message}", e)
                _ignoringTransition = false // BUG 1 FIX: Reset even on error
                _errorMessage.value = "Error al reproducir archivo offline. Usando red..."
                _playbackState.value = PlaybackState.Error("Error al reproducir archivo offline")
            }

            // Still fetch related queue & log DB
            viewModelScope.launch {
                try { favoritesRepository.recordPlay(song) } catch (_: Exception) {}
            }
            viewModelScope.launch { replenishAutoplayQueue(song) }
            return
        }

        // ── LOCAL SONG: play directly via content URI ──
        if (isLocal && !song.streamUrl.isNullOrBlank()) {
            try {
                Log.d(TAG, "PlayerVM: ✅ Local song — using content URI directly")
                playerRepository.prepare(song.streamUrl, song)
                playerRepository.play()
                _ignoringTransition = false // BUG 1 FIX: Reset flag so future transitions work
                _playbackState.value = PlaybackState.Playing(song, 0L)
                _errorMessage.value = null
                val timeMs = System.currentTimeMillis() - bufferStartTime
                com.xploitmusic.app.utils.AppLogger.logPlaybackState(song.id, "LOCAL_PLAY", timeMs)
                analyticsLogger.logPlaySuccess(song.id, timeMs)
                resetAutoSkipCounter() // BUG 7: Reset on successful play
            } catch (e: Exception) {
                Log.e(TAG, "PlayerVM: ❌ Local playback error: ${e.message}", e)
                val timeMs = System.currentTimeMillis() - bufferStartTime
                com.xploitmusic.app.utils.AppLogger.logPlaybackState(song.id, "ERROR", timeMs)
                analyticsLogger.logPlayError(song.id, e.message ?: "Error local", timeMs)
                _errorMessage.value = "Error al reproducir archivo local"
                _ignoringTransition = false // BUG 1 FIX: Already here, but kept for clarity
                _playbackState.value = PlaybackState.Error("Error al reproducir archivo local")
            }

            // Record play in DB
            viewModelScope.launch {
                try { favoritesRepository.recordPlay(song) } catch (_: Exception) {}
            }
            return
        }

        // ── ONLINE SONG: Pause without auto-skipping if network is completely disconnected ──
        if (playbackLogManager.currentNetworkType.value == "Sin red") {
            val friendlyMsg = "Sin conexión a internet — la reproducción se reanudará al reconectar."
            pendingResumePositionSongId = song.id
            _errorMessage.value = friendlyMsg
            _playbackState.value = PlaybackState.Error(friendlyMsg)
            playbackLogManager.log("PLAYBACK", "WARN", "Canción '${song.title}' solicitada sin red. Pausando en espera de conexión.")
            return
        }

        // ── ONLINE SONG: full fetch pipeline ──

        // Launch background tasks independently (fire-and-forget in viewModelScope)
        // These run in parallel and do NOT block the stream URL fetch below

        // 1. Record play in DB
        viewModelScope.launch {
            try {
                favoritesRepository.recordPlay(song)
            } catch (e: Exception) {
                Log.w(TAG, "PlayerVM: recordPlay failed: ${e.message}")
            }
        }

        // 2. Fetch related songs for autoplay queue (shuffled, Spotify-like)
        // CRITICAL FIX: Do NOT call prefetchNextSong() here to prevent race conditions 
        // with the ExoPlayer instance prior to the primary stream loading.
        viewModelScope.launch {
            replenishAutoplayQueue(song)
        }

        // 4. MAIN: Fetch stream URL with retry + start playback
        try {
            Log.d(TAG, "PlayerVM: Fetching stream URL for ${song.id}...")
            // Step 7: Retry with exponential backoff
            when (val result = getStreamUrl(song.id)) {
                is Resource.Success -> {
                    // GUARD: If a newer song started loading while we fetched, discard this result.
                    // Needed because OkHttp blocks the IO thread and can't be truly cancelled.
                    if (_playGeneration != generation) {
                        Log.d(TAG, "PlayerVM: stale fetch for '${song.title}' (gen=$generation, current=${_playGeneration}) — discarding")
                        return
                    }
                    val streamUrl = result.data
                    if (!streamUrl.isNullOrBlank()) {
                        Log.d(TAG, "PlayerVM: ✅ Stream URL obtained (${streamUrl.take(80)}...)")
                        playerRepository.prepare(streamUrl, song)
                        if (pendingResumePositionSongId == song.id) {
                            val posToSeek = pendingResumePositionMs
                            pendingResumePositionMs = null
                            pendingResumePositionSongId = null
                            if (posToSeek != null && posToSeek > 0L) {
                                playerRepository.seekTo(posToSeek)
                            }
                        } else {
                            pendingResumePositionMs = null
                            pendingResumePositionSongId = null
                        }
                        playerRepository.play()
                        _ignoringTransition = false // Allow the real MediaItemTransition through
                        _playbackState.value = PlaybackState.Playing(song, 0L)
                        Log.d(TAG, "PlayerVM: ✅ prepare() + play() — state=PLAYING")
                        _errorMessage.value = null
                        analyticsLogger.logPlaySuccess(song.id, 0L)
                        resetAutoSkipCounter() // BUG 7: Reset on successful play
                        launchPrefetch()
                    } else {
                        Log.e(TAG, "PlayerVM: ❌ Stream URL is null or blank")
                        _ignoringTransition = false
                        _errorMessage.value = "No se encontró el audio. Saltando..."
                        _playbackState.value = PlaybackState.Error("No se encontró el audio. Saltando...")
                        analyticsLogger.logPlayError(song.id, "Stream URL blank", 0L)
                        triggerAutoSkip()
                    }
                }
                is Resource.Error -> {
                    if (_playGeneration != generation) {
                        Log.d(TAG, "PlayerVM: stale error for '${song.title}' — discarding")
                        _ignoringTransition = false
                        return
                    }
                    Log.e(TAG, "PlayerVM: ❌ Stream error: ${result.message}")
                    _ignoringTransition = false
                    val errMsg = result.message.orEmpty()
                    val isNetworkError = errMsg.contains("No se pudo resolver") ||
                        errMsg.contains("Network or timeout") ||
                        errMsg.contains("Unable to resolve host") ||
                        errMsg.contains("Sin conexión") ||
                        errMsg.contains("timeout") ||
                        errMsg.contains("timed out")

                    if (isNetworkError) {
                        val friendlyMsg = "Sin conexión a internet — la reproducción se reanudará al reconectar."
                        pendingResumePositionSongId = song.id
                        _errorMessage.value = friendlyMsg
                        _playbackState.value = PlaybackState.Error(friendlyMsg)
                        playbackLogManager.log("PLAYBACK", "WARN", "Error de red al extraer stream de '${song.title}'. Pausando en espera de conexión.")
                    } else {
                        _errorMessage.value = result.message ?: "Error al obtener audio. Saltando..."
                        _playbackState.value = PlaybackState.Error(result.message ?: "Error")
                        analyticsLogger.logPlayError(song.id, result.message ?: "Error stream", 0L)
                        triggerAutoSkip()
                    }
                }
                else -> {
                    if (_playGeneration != generation) { _ignoringTransition = false; return }
                    _ignoringTransition = false
                    _playbackState.value = PlaybackState.Error("Error desconocido")
                    analyticsLogger.logPlayError(song.id, "Unknown error", 0L)
                    triggerAutoSkip()
                }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) {
                _ignoringTransition = false
                throw e
            }
            Log.e(TAG, "PlayerVM: ❌ Exception in stream fetch: ${e.javaClass.simpleName}: ${e.message}", e)
            _ignoringTransition = false
            val isNetExc = e is java.io.IOException
            if (isNetExc) {
                val friendlyMsg = "Sin conexión a internet — la reproducción se reanudará al reconectar."
                pendingResumePositionSongId = song.id
                _errorMessage.value = friendlyMsg
                _playbackState.value = PlaybackState.Error(friendlyMsg)
                playbackLogManager.log("PLAYBACK", "WARN", "Excepción de red al extraer stream de '${song.title}'. Pausando en espera de conexión.")
            } else {
                val friendlyMsg = makeFriendlyError(e.message ?: "Error al cargar audio")
                _errorMessage.value = friendlyMsg
                _playbackState.value = PlaybackState.Error(friendlyMsg)
                analyticsLogger.logPlayError(song.id, e.message ?: "Exception stream", 0L)
                triggerAutoSkip()
            }
        } finally {
            // If still LOADING (no success/error set above), reset to IDLE
            if (_playbackState.value is PlaybackState.Buffering) {
                _ignoringTransition = false
                _playbackState.value = PlaybackState.Idle
            }
            Log.d(TAG, "PlayerVM: playInternal complete for '${song.title}', state=${_playbackState.value}")
        }
    }

    /**
     * Fetches related songs using two sources, then applies Anti-Artist Clustering
     * (round-robin interleaving by artist) for a Spotify-like radio experience.
     *
     * Sources:
     * 1. Piped's relatedStreams (YouTube sidebar recommendations)
     * 2. Genre-based search ("[artist] mix" / "[artist] radio")
     *
     * After fetching, songs are:
     * - De-duplicated
     * - Filtered of already-played songs  
     * - Grouped by artist
     * - Interleaved round-robin (1 song per artist per round)
     * - First song guaranteed to be from a different artist than the current one
     */
    private suspend fun replenishAutoplayQueue(song: Song) {
        val allNewSongs = mutableListOf<Song>()

        // Source 1: Piped relatedStreams
        try {
            val related = streamRepository.getRelatedSongs(song.id)
            if (related is Resource.Success && !related.data.isNullOrEmpty()) {
                allNewSongs.addAll(related.data)
                Log.d(TAG, "PlayerVM: Source 1 (relatedStreams): ${related.data.size} songs")
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Log.w(TAG, "PlayerVM: Related fetch failed: ${e.message}")
        }

        // Source 2: Fallback search if related returned < 5 songs
        if (allNewSongs.size < 5) {
            try {
                val searchResult = streamRepository.searchSongs("${song.artist} canciones populares")
                if (searchResult is Resource.Success && !searchResult.data.isNullOrEmpty()) {
                    allNewSongs.addAll(searchResult.data)
                    Log.d(TAG, "PlayerVM: Source 2 (search fallback): ${searchResult.data.size} songs")
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Log.w(TAG, "PlayerVM: Fallback search failed: ${e.message}")
            }
        }

        val preferredArtists = try {
            (favoritesRepository.getFavorites().first() + favoritesRepository.getMostPlayed().first())
                .map { it.artist }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            emptyList()
        }

        val selected = SongRecommendationSelector.select(
            candidates = allNewSongs,
            seedArtist = song.artist,
            preferredArtists = preferredArtists,
            excludedIds = queueManager.playedIds + song.id,
            limit = 20
        )

        if (selected.isNotEmpty()) {
            queueManager.appendAutoplayTracks(selected)
            Log.d(TAG, "PlayerVM: Appended ${selected.size} autoplay tracks (${upNext.value.size} upcoming)")
        }
    }

    private fun launchPrefetch() {
        _prefetchJob?.cancel()
        _prefetchJob = viewModelScope.launch {
            prefetchNextSong()
        }
    }

    /**
     * Pre-fetches the stream URL for the NEXT song in queue and queues it
     * into ExoPlayer as an upcoming MediaItem.
     *
     * ExoPlayer will automatically crossfade/transition to it smoothly
     * when the current song ends — zero gap playback!
     */
    private suspend fun prefetchNextSong() {
        val nextSong = queueManager.peekNext()
        if (nextSong == null) {
            Log.d(TAG, "PlayerVM: No next song in queue to pre-fetch")
            return
        }

        Log.d(TAG, "PlayerVM: Pre-fetching next song: '${nextSong.title}' (${nextSong.id})")
        _prefetchedNextSong = nextSong

        // Local or offline songs don't need stream URL resolution
        val isLocal = nextSong.id.startsWith("local_")
        val offlinePath = offlineManager.getOfflineFilePath(nextSong.id)

        val nextUrl = when {
            offlinePath != null -> offlinePath
            isLocal -> nextSong.streamUrl
            else -> {
                when (val result = getStreamUrl(nextSong.id)) {
                    is Resource.Success -> result.data
                    else -> null
                }
            }
        }

        if (!nextUrl.isNullOrBlank()) {
            Log.d(TAG, "PlayerVM: ✅ Pre-fetched URL for '${nextSong.title}', queueing in ExoPlayer...")
            playerRepository.addNextMediaItem(nextUrl, nextSong)
        } else {
            Log.w(TAG, "PlayerVM: ⚠️ Could not pre-fetch stream URL for '${nextSong.title}'")
        }
    }

    fun togglePlayPause() {
        viewModelScope.launch {
            if (isPlaying.value) {
                playerRepository.pause()
            } else {
                if (_playbackState.value is PlaybackState.Idle && _currentSong.value != null) {
                    playInternal(_currentSong.value!!)
                } else {
                    playerRepository.play()
                }
            }
        }
    }

    fun seekTo(position: Long) {
        viewModelScope.launch {
            playerRepository.seekTo(position)
        }
    }

    /**
     * Skip to next track.
     *
     * Behavior:
     * - If user is navigating in history mode (_historyCursor >= 0), moves forward in history.
     * - Otherwise uses QueueManager.skipToNext() to find the next item.
     * - Cancels previous skipJob to prevent race conditions on rapid clicks.
     */
    fun skipNext() {
        Log.d(TAG, "PlayerVM: User clicked skipNext (cursor=$_historyCursor, historySize=${_historyStack.size})")

        _skipJob?.cancel()
        _skipJob = viewModelScope.launch {
            delay(80)
            val generation = ++_playGeneration
            try {
                playerRepository.pause()
            } catch (_: Exception) {}

            playerRepository.clearUpcomingMediaItems()

            // Mode A: Navigating within history stack
            if (_historyCursor in 0 until _historyStack.lastIndex) {
                val nextCursor = _historyCursor + 1
                val song = _historyStack[nextCursor]
                Log.d(TAG, "PlayerVM: History next → index $nextCursor: '${song.title}'")
                _currentSong.value = song
                // When cursor reaches the end of history, return to live mode
                playHistorySong(song, if (nextCursor == _historyStack.lastIndex) -1 else nextCursor)
                return@launch
            }

            // Mode B: At live head (or cursor reset) → normal QueueManager navigation
            _historyCursor = -1

            val nextSong = queueManager.skipToNext()
            if (nextSong != null) {
                Log.d(TAG, "PlayerVM: QueueManager next → '${nextSong.title}'")
                _currentSong.value = nextSong
                playInternal(nextSong, generation)
            } else {
                Log.d(TAG, "PlayerVM: Queue end reached and autoplay disabled. Stopping.")
                playerRepository.pause()
                _errorMessage.value = "Fin de la cola de reproducción"
            }
        }
    }

    /**
     * Skip to previous track.
     *
     * Behavior:
     * - If position > 3 seconds, seeks to 0:00 of the same track.
     * - If in history mode (_historyCursor >= 0), steps back in history.
     * - If at live head (_historyCursor == -1), moves to the second-to-last item in history stack.
     * - Cancels previous skipJob to prevent race conditions on rapid clicks.
     */
    fun skipPrevious() {
        Log.d(TAG, "PlayerVM: User clicked skipPrevious (pos=${currentPosition.value}ms, cursor=$_historyCursor)")

        // 1. If played > 3s, seek to 0:00 of current track
        if (currentPosition.value > 3000L) {
            seekTo(0)
            return
        }

        _skipJob?.cancel()
        _skipJob = viewModelScope.launch {
            playerRepository.clearUpcomingMediaItems()

            // 2. Determine target song from history stack
            val targetCursor: Int
            val targetSong: Song?

            if (_historyCursor > 0) {
                // Already step-navigating in history → move 1 step back
                targetCursor = _historyCursor - 1
                targetSong = _historyStack.getOrNull(targetCursor)
            } else if (_historyStack.size >= 2) {
                // At live head → jump to second-to-last item in history
                targetCursor = _historyStack.lastIndex - 1
                targetSong = _historyStack.getOrNull(targetCursor)
            } else {
                // History empty or single item → try QueueManager
                targetCursor = -1
                targetSong = queueManager.skipToPrevious()
            }

            if (targetSong != null) {
                Log.d(TAG, "PlayerVM: skipPrevious → '${targetSong.title}' (cursor=$targetCursor)")
                if (targetCursor >= 0) {
                    playHistorySong(targetSong, targetCursor)
                } else {
                    playInternal(targetSong)
                }
            } else {
                seekTo(0)
            }
        }
    }

    // ── Delegate overrides for SkipHandler (Notification / Headset / Wear / AA) ──
    override fun onSkipNext() = skipNext()
    override fun onSkipPrevious() = skipPrevious()
    override fun onAutomaticNext() {
        if (_ignoringTransition || _playbackState.value is PlaybackState.Buffering) return
        viewModelScope.launch {
            Log.d(TAG, "PlayerVM: Automatic next triggered by natural track completion")
            _autoSkipJob?.cancel()
            _skipJob?.cancel()
            _skipJob = null
            _playInternalJob?.cancel()
            _ignoringTransition = false

            if (_repeatMode.value == Player.REPEAT_MODE_ONE) {
                playerRepository.seekTo(0)
                playerRepository.play()
                return@launch
            }

            val nextSong = queueManager.skipToNext()
            if (nextSong != null) {
                playInternal(nextSong)
            } else {
                val seed = _currentSong.value ?: return@launch
                val result = streamRepository.getRelatedSongs(seed.id)
                val candidates = if (result is Resource.Success) result.data.orEmpty() else emptyList()
                val autoplay = SongRecommendationSelector.select(
                    candidates = candidates,
                    seedArtist = seed.artist,
                    excludedIds = queueManager.playedIds + seed.id,
                    limit = 15
                )
                if (autoplay.isNotEmpty()) {
                    queueManager.appendAutoplayTracks(autoplay)
                    val autoplayNext = queueManager.skipToNext()
                    if (autoplayNext != null) playInternal(autoplayNext)
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        skipHandler.detach(this)
        _transitionDebounceJob?.cancel()
        _lyricsJob?.cancel()
        _skipJob?.cancel()
        _autoSkipJob?.cancel()
        _playInternalJob?.cancel()
        _prefetchJob?.cancel()
    }

    fun toggleFavorite() {
        val song = _currentSong.value ?: return
        viewModelScope.launch {
            val isFavNow = toggleFavoriteUseCase(song)
            Log.d(TAG, "PlayerVM: Toggled favorite for '${song.title}' -> $isFavNow")
        }
    }

    /** Toggles single-track download / removal */
    fun toggleDownload() {
        val song = _currentSong.value ?: return
        viewModelScope.launch {
            val state = downloadState.value
            if (state?.status == "COMPLETED") {
                offlineManager.deleteDownload(song.id)
            } else if (state?.status == "FAILED") {
                offlineManager.retryFailed(song.id)
            } else {
                offlineManager.downloadTrack(song)
            }
        }
    }

    /**
     * Adds the currently playing song to a local Room playlist.
     */
    fun addCurrentSongToPlaylist(playlistId: Long) {
        val song = _currentSong.value ?: return
        viewModelScope.launch {
            try {
                playlistRepository.addSongToPlaylist(playlistId, song)
                Log.d(TAG, "PlayerVM: Added '${song.title}' to playlist $playlistId")
            } catch (e: Exception) {
                Log.e(TAG, "PlayerVM: Error adding song to playlist: ${e.message}", e)
            }
        }
    }

    /**
     * Creates a new playlist and adds the current song to it.
     */
    fun createPlaylistAndAddSong(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            try {
                val id = playlistRepository.createPlaylist(name.trim())
                addCurrentSongToPlaylist(id)
            } catch (e: Exception) {
                Log.e(TAG, "PlayerVM: Error creating playlist: ${e.message}", e)
            }
        }
    }

    fun toggleAutoplay() {
        queueManager.toggleAutoplay()
    }

    fun toggleShuffle() {
        queueManager.toggleShuffle()
        playerRepository.setShuffleModeEnabled(isShuffleEnabled.value)
    }

    fun toggleRepeat() {
        val nextMode = when (_repeatMode.value) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        _repeatMode.value = nextMode
        playerRepository.setRepeatMode(nextMode)

        // Keep QueueManager in sync
        val typedMode = when (nextMode) {
            Player.REPEAT_MODE_ONE -> RepeatMode.ONE
            Player.REPEAT_MODE_ALL -> RepeatMode.ALL
            else -> RepeatMode.OFF
        }
        queueManager.setRepeatMode(typedMode)
    }

    fun clearError() {
        _errorMessage.value = null
    }

    // --- Automatic Error Skipping State (Step 7) ---
    private var consecutiveAutoSkips = 0
    private var _autoSkipJob: Job? = null

    private fun triggerAutoSkip() {
        _autoSkipJob?.cancel()
        _autoSkipJob = viewModelScope.launch {
            consecutiveAutoSkips++
            Log.w(TAG, "PlayerVM: Auto-skip triggered (consecutive count: $consecutiveAutoSkips)")

            if (consecutiveAutoSkips >= MAX_CONSECUTIVE_AUTO_SKIPS) {
                Log.e(TAG, "PlayerVM: Reached MAX_CONSECUTIVE_AUTO_SKIPS ($MAX_CONSECUTIVE_AUTO_SKIPS). Stopping playback.")
                _errorMessage.value = "Múltiples errores de red. Reproducción pausada."
                _playbackState.value = PlaybackState.Error("Múltiples errores de red.")
                consecutiveAutoSkips = 0
                return@launch
            }

            // Brief pause so user sees error banner before skip
            delay(1500)
            
            // Advance to next available track using same logic as manual skip
            forceSkipToNext()
        }
    }

    private fun resetAutoSkipCounter() {
        consecutiveAutoSkips = 0
    }

    /**
     * Forces skip to next available track in auto-skip situations.
     */
    private suspend fun forceSkipToNext() {
        playerRepository.clearUpcomingMediaItems()
        _historyCursor = -1

        val nextSong = queueManager.skipToNext()
        if (nextSong != null) {
            Log.d(TAG, "PlayerVM: Auto-skip -> QueueManager next '${nextSong.title}'")
            playInternal(nextSong)
        } else {
            Log.d(TAG, "PlayerVM: Auto-skip reached end of queue")
            playerRepository.pause()
            _errorMessage.value = "No hay más canciones disponibles"
        }
    }

    // --- Helper function for Retry logic (Step 7) ---
    private suspend fun getStreamUrl(songId: String): Resource<String> {
        var result = getAudioStreamUseCase(songId)
        
        // Initial quick retry for minor network glitches
        if (result is Resource.Error) {
            delay(1000)
            result = getAudioStreamUseCase(songId)
        }
        
        return result
    }
}
