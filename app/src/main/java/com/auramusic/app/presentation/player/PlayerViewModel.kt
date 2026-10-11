package com.auramusic.app.presentation.player

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.auramusic.app.core.util.Resource
import com.auramusic.app.data.local.dao.PlaylistDao
import com.auramusic.app.data.local.dao.SongDao
import com.auramusic.app.data.local.entity.PlaylistEntity
import com.auramusic.app.data.local.entity.PlaylistSongCrossRef
import com.auramusic.app.data.local.entity.SongEntity
import com.auramusic.app.data.local.entity.DownloadedTrackEntity
import com.auramusic.app.data.repository.OfflineManager
import com.auramusic.app.data.service.SkipHandler
import com.auramusic.app.domain.model.Song
import com.auramusic.app.domain.repository.FavoritesRepository
import com.auramusic.app.domain.repository.PlayerRepository
import com.auramusic.app.domain.repository.QueueManager
import com.auramusic.app.domain.repository.StreamRepository
import com.auramusic.app.domain.recommendation.SongRecommendationSelector
import com.auramusic.app.domain.usecase.GetAudioStreamUseCase
import com.auramusic.app.domain.usecase.ToggleFavoriteUseCase
import com.auramusic.app.core.analytics.AuraAnalyticsLogger
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
import com.auramusic.app.domain.model.PlaybackState
import com.auramusic.app.domain.model.RepeatMode

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playerRepository: PlayerRepository,
    private val getAudioStreamUseCase: GetAudioStreamUseCase,
    private val getLyricsUseCase: com.auramusic.app.domain.usecase.GetLyricsUseCase,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    private val favoritesRepository: FavoritesRepository,
    private val streamRepository: StreamRepository,
    private val skipHandler: SkipHandler,
    private val playlistDao: PlaylistDao,
    private val songDao: SongDao,
    private val queueManager: QueueManager,
    private val offlineManager: OfflineManager,
    private val analyticsLogger: AuraAnalyticsLogger
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
        MutableStateFlow<List<com.auramusic.app.domain.model.LyricLine>>(emptyList())
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
            playerRepository.playerError.collect { errorMsg ->
                if (errorMsg != null) {
                    Log.e(TAG, "PlayerVM: Player error received: $errorMsg")
                    // Friendly message for bot/sign-in/403 errors
                    val friendlyMsg = makeFriendlyError(errorMsg)
                    _errorMessage.value = friendlyMsg
                    _playbackState.value = PlaybackState.Error(friendlyMsg)
                    triggerAutoSkip()
                }
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
                                com.auramusic.app.domain.model.LyricLine(0L, "♪ Instrumental ♪")
                            )
                            else -> com.auramusic.app.core.util.LrcParser.parse(
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
    private suspend fun playInternal(song: Song, generation: Long = _playGeneration) {
        // OPTIMISTIC UPDATE: Set _currentSong immediately so the UI shows the song
        // while we fetch the stream URL. ExoPlayer transition will confirm later.
        _currentSong.value = song
        _lyrics.value = emptyList()
        loadLyrics(song)
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
                com.auramusic.app.utils.AppLogger.logPlaybackState(song.id, "OFFLINE_PLAY", timeMs)
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
                com.auramusic.app.utils.AppLogger.logPlaybackState(song.id, "LOCAL_PLAY", timeMs)
                analyticsLogger.logPlaySuccess(song.id, timeMs)
                resetAutoSkipCounter() // BUG 7: Reset on successful play
            } catch (e: Exception) {
                Log.e(TAG, "PlayerVM: ❌ Local playback error: ${e.message}", e)
                val timeMs = System.currentTimeMillis() - bufferStartTime
                com.auramusic.app.utils.AppLogger.logPlaybackState(song.id, "ERROR", timeMs)
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
                    _errorMessage.value = result.message ?: "Error al obtener audio. Saltando..."
                    _playbackState.value = PlaybackState.Error(result.message ?: "Error")
                    analyticsLogger.logPlayError(song.id, result.message ?: "Error stream", 0L)
                    triggerAutoSkip()
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
            val friendlyMsg = makeFriendlyError(e.localizedMessage ?: "Error desconocido")
            _errorMessage.value = friendlyMsg
            _playbackState.value = PlaybackState.Error(friendlyMsg)
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
            Log.w(TAG, "PlayerVM: relatedStreams fetch failed: ${e.message}")
        }

        val relatedSelection = SongRecommendationSelector.select(
            candidates = allNewSongs,
            seedArtist = song.artist,
            excludedIds = queueManager.playedIds + song.id,
            limit = 8
        )
        if (relatedSelection.size < 5) {
            val artist = song.artist.takeIf { it.isNotBlank() && it != "Unknown" }
            if (artist != null) {
                try {
                    val result = streamRepository.searchSongs("$artist canciones populares")
                    if (result is Resource.Success) allNewSongs.addAll(result.data.orEmpty())
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    Log.w(TAG, "PlayerVM: Popular-song search failed: ${e.message}")
                }
            }
        }
        val preferredArtists = try {
            (favoritesRepository.getFavorites().first() + favoritesRepository.getMostPlayed().first())
                .map { it.artist }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptyList()
        }
        val interleavedQueue = SongRecommendationSelector.select(
            candidates = allNewSongs,
            seedArtist = song.artist,
            preferredArtists = preferredArtists,
            excludedIds = queueManager.playedIds + song.id,
            limit = 50
        )

        // Append only new tracks; QueueManager owns ordering and deduplication.
        val existingIds = queueManager.queue.value.map { it.id }.toSet()
        val brandNew = interleavedQueue.filter { it.id !in existingIds }

        queueManager.appendAutoplayTracks(brandNew)
        Log.d(TAG, "PlayerVM: Autoplay queue replenished: ${queueManager.upNext.value.size} upcoming (${brandNew.size} new)")

        // Step 6 (RC-5 fix): Invalidate stale prefetch if queue changed
        if (brandNew.isNotEmpty() && _prefetchedNextSong != null) {
            val nextExpected = getNextSong()
            if (nextExpected?.id != _prefetchedNextSong?.id) {
                Log.d(TAG, "PlayerVM: Prefetch invalidated — queue changed, expected=${nextExpected?.title}, prefetched=${_prefetchedNextSong?.title}")
                _prefetchedNextSong = null
            }
        }
    }

    /**
     * Launches a coroutine to pre-fetch the next song's stream URL
     * and add it to ExoPlayer's queue for near-gapless playback.
     */
    private fun launchPrefetch() {
        _prefetchJob?.cancel()
        _prefetchJob = viewModelScope.launch {
            prefetchNextSong()
        }
    }

    /**
     * Determines the next song and pre-fetches its stream URL,
     * then adds it to ExoPlayer's native queue.
     */
    private suspend fun prefetchNextSong() {
        // Don't pre-fetch if ExoPlayer already has a queued item
        if (playerRepository.hasNextMediaItem()) {
            Log.d(TAG, "PlayerVM: Pre-fetch skipped — next item already queued")
            return
        }

        val nextSong = getNextSong()
        if (nextSong == null) {
            Log.d(TAG, "PlayerVM: Pre-fetch skipped — no next song available")
            return
        }

        Log.d(TAG, "PlayerVM: Pre-fetching stream for '${nextSong.title}'...")
        try {
            when (val result = getAudioStreamUseCase(nextSong.id)) {
                is Resource.Success -> {
                    val streamUrl = result.data
                    if (!streamUrl.isNullOrBlank()) {
                        playerRepository.addNextMediaItem(streamUrl, nextSong)
                        _prefetchedNextSong = nextSong
                        Log.d(TAG, "PlayerVM: ✅ Pre-fetched and queued '${nextSong.title}'")
                    } else {
                        // Blank URL from prefetch — nullify to force full fetch on skip
                        _prefetchedNextSong = null
                        Log.w(TAG, "PlayerVM: Pre-fetch: blank URL for '${nextSong.title}', skipping queue")
                    }
                }
                is Resource.Error -> {
                    _prefetchedNextSong = null
                    Log.w(TAG, "PlayerVM: Pre-fetch failed for '${nextSong.id}': ${result.message}")
                }
                else -> { _prefetchedNextSong = null }
            }
        } catch (e: Exception) {
            _prefetchedNextSong = null
            Log.w(TAG, "PlayerVM: Pre-fetch exception for '${nextSong.id}': ${e.message}")
        }
    }

    /**
     * Returns the next song in the playlist or autoplay queue, without consuming it.
     */
    private fun getNextSong(): Song? {
        return queueManager.peekNext()
    }

    /**
     * Called when there's no pre-fetched item and we need to manually
     * start the next song (trackEnded fallback or skip button).
     */
    private suspend fun autoplayNext() {
        // 1. If ExoPlayer already has a pre-fetched next item, just let it play
        //    (ExoPlayer auto-transitions and onMediaItemTransition handles the rest)
        if (playerRepository.hasNextMediaItem()) {
            Log.d(TAG, "PlayerVM: autoplayNext — ExoPlayer has next item, using skipToNext()")
            playerRepository.skipToNext()
            return
        }

        // 2. No pre-fetched item — consume the next item from QueueManager.
        val nextFromQueue = queueManager.skipToNext()
        if (nextFromQueue != null) {
            Log.d(TAG, "PlayerVM: Autoplay from queue: ${nextFromQueue.title}")
            analyticsLogger.logAutoplayStarted(_currentSong.value?.id ?: "none", nextFromQueue.id)
            launchAutoplayPlayback(nextFromQueue)
            return
        }

        // 3. Queue empty — fetch fresh related songs from the current song
        val currentSong = _currentSong.value ?: return
        Log.d(TAG, "PlayerVM: Autoplay queue empty — fetching fresh related for '${currentSong.title}'")
        try {
            val related = streamRepository.getRelatedSongs(currentSong.id)
            if (related is Resource.Success && !related.data.isNullOrEmpty()) {
                val fresh = SongRecommendationSelector.select(
                    candidates = related.data,
                    seedArtist = currentSong.artist,
                    excludedIds = queueManager.playedIds + currentSong.id,
                    limit = 20
                )
                if (fresh.isNotEmpty()) {
                    queueManager.appendAutoplayTracks(fresh)
                    val nextSong = queueManager.skipToNext()
                    if (nextSong != null) {
                        Log.d(TAG, "PlayerVM: Autoplay from fresh related: ${nextSong.title}")
                        analyticsLogger.logAutoplayStarted(currentSong.id, nextSong.id)
                        launchAutoplayPlayback(nextSong)
                        return
                    }
                }
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Log.w(TAG, "PlayerVM: Fresh related fetch failed: ${e.message}")
        }

        // 4. Fallback: search for individual popular songs by the same artist.
        val currentArtist = currentSong.artist
        Log.d(TAG, "PlayerVM: Autoplay fallback: searching popular songs by '$currentArtist'")
        try {
            val result = streamRepository.searchSongs("$currentArtist canciones populares")
            if (result is Resource.Success && !result.data.isNullOrEmpty()) {
                val nextSongs = SongRecommendationSelector.select(
                    candidates = result.data,
                    seedArtist = currentSong.artist,
                    excludedIds = queueManager.playedIds + currentSong.id,
                    limit = 10
                )
                queueManager.appendAutoplayTracks(nextSongs)
                queueManager.skipToNext()?.let { queuedSong ->
                    analyticsLogger.logAutoplayStarted(currentSong.id, queuedSong.id)
                    launchAutoplayPlayback(queuedSong)
                }
            } else {
                Log.w(TAG, "PlayerVM: Autoplay fallback returned no results")
            }
        } catch (e: Exception) {
            Log.e(TAG, "PlayerVM: Autoplay fallback failed: ${e.message}", e)
        }
    }

    private fun launchAutoplayPlayback(song: Song) {
        _playInternalJob?.cancel()
        val generation = ++_playGeneration
        _playInternalJob = viewModelScope.launch {
            playInternal(song, generation)
        }
    }

    fun toggleAutoplay() {
        queueManager.toggleAutoplay()
        Log.d(TAG, "PlayerVM: Autoplay toggled to ${autoplayEnabled.value}")
    }

    fun toggleShuffle() {
        queueManager.toggleShuffle()
        // QueueManager owns ordering. Media3 only holds current + one prefetched item,
        // so native shuffle would create a second, conflicting order.
        playerRepository.setShuffleModeEnabled(false)
        _prefetchJob?.cancel()
        _prefetchedNextSong = null
        viewModelScope.launch {
            playerRepository.clearUpcomingMediaItems()
            prefetchNextSong()
        }
        Log.d(TAG, "PlayerVM: Shuffle toggled to ${queueManager.isShuffleEnabled.value}")
    }

    fun toggleRepeat() {
        // Cycle: OFF(0) → ALL(2) → ONE(1) → OFF(0)
        _repeatMode.value = when (_repeatMode.value) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        queueManager.setRepeatMode(RepeatMode.fromExoPlayerMode(_repeatMode.value))
        // Media3 owns repeat-one. Repeat-all is handled by the complete logical queue.
        val nativeRepeatMode = if (_repeatMode.value == Player.REPEAT_MODE_ONE) {
            Player.REPEAT_MODE_ONE
        } else {
            Player.REPEAT_MODE_OFF
        }
        playerRepository.setRepeatMode(nativeRepeatMode)
        Log.d(TAG, "PlayerVM: Repeat mode changed to ${_repeatMode.value}")
    }

    fun toggleFavorite() {
        val song = _currentSong.value ?: return
        viewModelScope.launch {
            try {
                val newState = toggleFavoriteUseCase(song)
                Log.d(TAG, "PlayerVM: Favorite toggled for '${song.title}': $newState")
            } catch (e: Exception) {
                Log.e(TAG, "PlayerVM: toggleFavorite failed: ${e.message}", e)
            }
        }
    }

    fun removeDownload(songId: String? = null) {
        val id = songId ?: _currentSong.value?.id ?: return
        viewModelScope.launch {
            offlineManager.deleteDownload(id)
        }
    }

    // ── Playlist Integration (F2.2) ──

    val playlists: StateFlow<List<PlaylistEntity>> = playlistDao.getAllPlaylists()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun addCurrentSongToPlaylist(playlistId: Long) {
        val song = _currentSong.value ?: return
        viewModelScope.launch {
            // Ensure the song exists in the songs table
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
            Log.d(TAG, "PlayerVM: Added '${song.title}' to playlist $playlistId")
        }
    }

    fun createPlaylistAndAddSong(name: String) {
        if (name.isBlank()) return
        val song = _currentSong.value ?: return
        viewModelScope.launch {
            val playlistId = playlistDao.createPlaylist(PlaylistEntity(name = name.trim()))
            Log.d(TAG, "PlayerVM: Created playlist '$name' with id=$playlistId")
            // Ensure the song exists in the songs table
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
            Log.d(TAG, "PlayerVM: Added '${song.title}' to new playlist '$name'")
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun togglePlayPause() {
        viewModelScope.launch {
            Log.d(TAG, "PlayerVM: togglePlayPause, isPlaying=${isPlaying.value}")
            if (isPlaying.value) {
                playerRepository.pause()
                // Update playback state to Paused so UI reflects correctly
                val song = _currentSong.value
                if (song != null) {
                    _playbackState.value = PlaybackState.Paused(song, currentPosition.value)
                }
            } else {
                playerRepository.play()
                // Update playback state to Playing
                val song = _currentSong.value
                if (song != null) {
                    _playbackState.value = PlaybackState.Playing(song, currentPosition.value)
                }
            }
        }
    }

    fun seekTo(position: Long) {
        viewModelScope.launch {
            playerRepository.seekTo(position)
        }
    }

    fun skipNext() {
        Log.d(TAG, "PlayerVM: skipNext called, cursor=$_historyCursor, historySize=${_historyStack.size}")
        _autoSkipJob?.cancel()

        // CANCEL any in-flight skip before doing anything else
        _skipJob?.cancel()
        _skipJob = null
        _ignoringTransition = false // Reset in case previous skip leaked

        // Release any stuck Buffering state
        if (_playbackState.value is PlaybackState.Buffering) {
            _playbackState.value = PlaybackState.Idle
            Log.d(TAG, "PlayerVM: skipNext aborted previous buffering skip, resetting state")
        }

        // BIDIRECTIONAL NAVIGATION: if we're in "back mode" (cursor set),
        // go FORWARD through history instead of picking a new song.
        if (_historyCursor >= 0) {
            val forwardIdx = _historyCursor + 1
            if (forwardIdx < _historyStack.size) {
                val forwardSong = _historyStack[forwardIdx]
                // Advance cursor; if at live head reset to -1
                val newCursor = if (forwardIdx == _historyStack.size - 1) -1 else forwardIdx
                Log.d(TAG, "PlayerVM: skipNext via history forward[$forwardIdx] → '${forwardSong.title}', cursor=$newCursor")
                playHistorySong(forwardSong, newCursor)
                return
            } else {
                // Already at live head
                _historyCursor = -1
            }
        }

        // NORMAL MODE: push current to history, pick next new song
        val previousSong = _currentSong.value
        if (previousSong != null) {
            val lastInHistory = _historyStack.lastOrNull()
            if (lastInHistory?.id != previousSong.id) {
                _historyStack.addLast(previousSong)
                if (_historyStack.size > 50) _historyStack.removeFirst()
            }
        }

        // OPTIMISTIC UPDATE: show next song immediately while stream fetch runs
        val nextSongOptimistic = getNextSong()
        if (nextSongOptimistic != null) {
            _currentSong.value = nextSongOptimistic
            _lyrics.value = emptyList()
            _errorMessage.value = null
        }

        // Pause old audio immediately
        viewModelScope.launch { playerRepository.pause() }

        val generation = ++_playGeneration
        _skipJob = viewModelScope.launch(Dispatchers.IO) {
            delay(80) // Physical debounce

            val songToSkipTo = getNextSong()
            withContext(Dispatchers.Main) {
                if (songToSkipTo != null) {
                    _playbackState.value = PlaybackState.Buffering(songToSkipTo)
                }
            }

            try {
                forceSkipToNext(generation)
            } finally {
                withContext(Dispatchers.Main) {
                    // Always release suppression flag
                    _ignoringTransition = false
                    if (_playbackState.value is PlaybackState.Buffering) {
                        _playbackState.value = PlaybackState.Idle
                    }
                }
            }
        }
    }

    /**
     * Force-skips to the next song immediately.
     * Called from skipNext() on Dispatchers.IO.
     *
     * CRITICAL FIX: Network calls run on IO thread, ExoPlayer calls use
     * withContext(Main). Loading guard in skipNext() prevents overlapping executions.
     * If the queue is empty, it force-replenishes synchronously before giving up.
     */
    private suspend fun forceSkipToNext(generation: Long = _playGeneration) {
        // 1. If ExoPlayer has a pre-fetched next item, use it directly (instant)
        val hasNext = withContext(Dispatchers.Main) { playerRepository.hasNextMediaItem() }
        if (hasNext) {
            Log.d(TAG, "PlayerVM: forceSkipToNext — ExoPlayer has next item, using skipToNext()")
            withContext(Dispatchers.Main) {
                playerRepository.skipToNext()
            }
            return
        }

        // 2. Pull next song from playlist or autoplay queue
        var nextSong = getNextSongFromQueue()

        // 3. If queue is empty, force-replenish synchronously (on IO thread)
        if (nextSong == null && _currentSong.value != null) {
            Log.d(TAG, "PlayerVM: forceSkipToNext — queue empty, force-replenishing...")
            replenishAutoplayQueue(_currentSong.value!!)
            nextSong = getNextSongFromQueue()
        }

        if (nextSong == null) {
            // 8. Queue still empty after replenishment — fallback to autoplayNext
            Log.d(TAG, "PlayerVM: forceSkipToNext — queue empty after replenish, falling back to autoplayNext()")
            autoplayNext()
            return
        }

        Log.d(TAG, "PlayerVM: forceSkipToNext — preparing stream fetch for: ${nextSong.title}")

            // Register as prefetched so MediaItemTransition can pick it up when ExoPlayer plays it
            withContext(Dispatchers.Main) {
                _prefetchedNextSong = nextSong
                _prefetchJob?.cancel()
            }

            // 5. Suppress the synchronous transition while prepare() atomically
            // replaces the native queue. Never clear first: that emits STATE_ENDED.
            withContext(Dispatchers.Main) {
                _ignoringTransition = true
            }

            // 6. Resolve stream URL (network call — runs on IO thread)
            try {
                val isLocal = nextSong.id.startsWith("local_")
                val localStreamUrl = nextSong.streamUrl
                if (isLocal && !localStreamUrl.isNullOrBlank()) {
                    withContext(Dispatchers.Main) {
                        playerRepository.prepare(localStreamUrl, nextSong)
                        playerRepository.play()
                        _ignoringTransition = false // Allow real transition event through
                    }
                } else {
                    // Step 7: Retry with exponential backoff for skip path too
                    when (val result = getStreamUrl(nextSong.id)) {
                        is Resource.Success -> {
                            val streamUrl = result.data
                            if (!streamUrl.isNullOrBlank()) {
                                withContext(Dispatchers.Main) {
                                    // Guard: discard if a newer play already started
                                    if (_playGeneration != generation) {
                                        Log.d(TAG, "PlayerVM: forceSkipToNext stale gen=$generation, discarding")
                                        _ignoringTransition = false
                                        return@withContext
                                    }
                                    playerRepository.prepare(streamUrl, nextSong)
                                    playerRepository.play()
                                    _playbackState.value = PlaybackState.Playing(nextSong, 0L)
                                    _ignoringTransition = false
                                    resetAutoSkipCounter() // BUG 7: Reset on successful play
                                }
                                Log.d(TAG, "PlayerVM: forceSkipToNext — playback started, state=PLAYING")
                            } else {
                                withContext(Dispatchers.Main) {
                                    _ignoringTransition = false
                                    _errorMessage.value = "No se encontró la URL del stream"
                                    _playbackState.value = PlaybackState.Error("No se encontró la URL del stream")
                                }
                            }
                        }
                        is Resource.Error -> {
                            withContext(Dispatchers.Main) {
                                _ignoringTransition = false
                                if (_playGeneration == generation) {
                                    Log.e(TAG, "PlayerVM: forceSkipToNext stream error: ${result.message}")
                                    _errorMessage.value = result.message ?: "Error al obtener audio"
                                    _playbackState.value = PlaybackState.Error(result.message ?: "Error")
                                    triggerAutoSkip() // Auto-advance on fetch failure
                                }
                            }
                        }
                        else -> {
                            _ignoringTransition = false
                            withContext(Dispatchers.Main) {
                                if (_playGeneration == generation) {
                                    _playbackState.value = PlaybackState.Error("Unknown")
                                    triggerAutoSkip()
                                }
                            }
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                Log.d(TAG, "PlayerVM: forceSkipToNext cancelled (new skip pressed)")
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "PlayerVM: forceSkipToNext stream error: ${e.message}", e)
                analyticsLogger.logSkipFailed(nextSong.id, e.message ?: "Failure in skip")
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "Error: ${e.localizedMessage}"
                    _playbackState.value = PlaybackState.Error("Error")
                }
            }

            // 7. Background tasks: lyrics, related songs, prefetch
            val songForBackground = nextSong
            viewModelScope.launch {
                try { favoritesRepository.recordPlay(songForBackground) } catch (_: Exception) {}
            }
            loadLyrics(songForBackground)
            viewModelScope.launch {
                replenishAutoplayQueue(songForBackground)
                prefetchNextSong()
            }
        }

    /**
     * Atomically pulls the next song from playlist or autoplay queue.
     * Returns null if both are exhausted.
     *
     * PROACTIVE REPLENISHMENT: If the remaining queue size drops to ≤ 3,
     * a background coroutine fires to refill the queue before it empties.
     */
    private fun getNextSongFromQueue(): Song? {
        val next = queueManager.skipToNext()
        if (next != null) {
            // PROACTIVE REPLENISHMENT: if queue is getting low, refill in background
            val playedIds = queueManager.playedIds
            val remainingUnplayed = queueManager.upNext.value.count { it.id !in playedIds }
            if (remainingUnplayed <= 3) {
                val seedSong = _currentSong.value ?: next
                Log.d(TAG, "PlayerVM: Queue low ($remainingUnplayed left) — proactively replenishing from '${seedSong.title}'")
                viewModelScope.launch {
                    replenishAutoplayQueue(seedSong)
                }
            }

            return next
        }

        return null
    }

    override fun onSkipNext() {
        Log.d(TAG, "PlayerVM: SkipHandler → skipNext()")
        skipNext()
    }

    override fun onAutomaticNext() {
        if (_ignoringTransition || _playbackState.value is PlaybackState.Buffering) {
            Log.d(TAG, "PlayerVM: Ignoring STATE_ENDED during an explicit transition")
            return
        }

        Log.d(
            TAG,
            "PlayerVM: Natural track end — repeat=${queueManager.repeatMode.value}, autoplay=${autoplayEnabled.value}"
        )
        viewModelScope.launch {
            when (queueManager.repeatMode.value) {
                RepeatMode.ONE -> {
                    playerRepository.seekTo(0L)
                    playerRepository.play()
                }
                else -> {
                    if (queueManager.hasNext() || autoplayEnabled.value) {
                        autoplayNext()
                    }
                }
            }
        }
    }

    fun skipPrevious() {
        Log.d(TAG, "PlayerVM: skipPrevious called, index=${queueManager.currentIndex.value}, cursor=$_historyCursor, history=${_historyStack.size}")
        // Cancel any in-flight skip
        _skipJob?.cancel()
        _skipJob = null
        _ignoringTransition = false

        // Determine current cursor position in history
        val startIdx = if (_historyCursor >= 0) _historyCursor else _historyStack.size - 1
        val targetIdx = startIdx - 1

        if (targetIdx >= 0 && targetIdx < _historyStack.size) {
            val previousSong = _historyStack[targetIdx]
            Log.d(TAG, "PlayerVM: skipPrevious from history[$targetIdx] → '${previousSong.title}'")
            playHistorySong(previousSong, targetIdx)  // preserves cursor
            return
        }

        // After restoring a session the in-memory history may be empty.
        queueManager.skipToPrevious()?.let { previousSong ->
            playHistorySong(previousSong, -1)
            return
        }

        // Nothing to go back to — restart current song
        Log.d(TAG, "PlayerVM: skipPrevious — history exhausted, restarting current song")
        seekTo(0)
    }

    override fun onSkipPrevious() {
        Log.d(TAG, "PlayerVM: SkipHandler → skipPrevious()")
        skipPrevious()
    }

    override fun onCleared() {
        skipHandler.detach(this)
        super.onCleared()
    }

    /** ProviderGateway already tries the configured stream providers in order. */
    private suspend fun getStreamUrl(songId: String): Resource<String> {
        return getAudioStreamUseCase(songId).also { result ->
            if (result is Resource.Error) {
                Log.w(TAG, "PlayerVM: Stream provider fallback exhausted for $songId: ${result.message}")
            }
        }
    }

    // ── Auto-Skip utility for failures ──
    private var _autoSkipJob: Job? = null
    @Volatile private var _consecutiveAutoSkips = 0

    private fun triggerAutoSkip() {
        _autoSkipJob?.cancel()

        // BUG 7 FIX: Limit consecutive auto-skips to prevent infinite loop
        _consecutiveAutoSkips++
        if (_consecutiveAutoSkips > MAX_CONSECUTIVE_AUTO_SKIPS) {
            Log.w(TAG, "PlayerVM: ⛔ Auto-skip limit reached ($_consecutiveAutoSkips). Stopping playback.")
            _errorMessage.value = "No se pueden reproducir las canciones. Verifica tu conexión e intenta más tarde."
            _playbackState.value = PlaybackState.Error("Múltiples canciones fallaron")
            _consecutiveAutoSkips = 0
            return
        }

        _autoSkipJob = viewModelScope.launch {
            Log.d(TAG, "PlayerVM: ⚠️ Error detectado, auto-skip #$_consecutiveAutoSkips/$MAX_CONSECUTIVE_AUTO_SKIPS en 800ms...")
            delay(800)
            if (_playbackState.value is PlaybackState.Error || _playbackState.value is PlaybackState.Idle) {
                _playbackState.value = PlaybackState.Idle // Reset to allow skipNext to run
                skipNext()
            }
        }
    }

    /** Reset the consecutive auto-skip counter on successful playback */
    private fun resetAutoSkipCounter() {
        if (_consecutiveAutoSkips > 0) {
            Log.d(TAG, "PlayerVM: ✅ Resetting auto-skip counter (was $_consecutiveAutoSkips)")
            _consecutiveAutoSkips = 0
        }
    }

    // ── User Actions ──

    fun toggleDownload() {
        val song = _currentSong.value ?: return
        if (song.id.startsWith("local_")) {
            Log.d(TAG, "PlayerVM: '${song.title}' is already local; download ignored")
            return
        }
        val currentDownload = downloadState.value

        if (currentDownload != null && currentDownload.status == "COMPLETED") {
            viewModelScope.launch { offlineManager.deleteDownload(song.id) }
        } else if (currentDownload != null && (currentDownload.status == "QUEUED" || currentDownload.status == "DOWNLOADING")) {
            offlineManager.cancelDownload(song.id)
        } else if (currentDownload != null && currentDownload.status == "FAILED") {
            offlineManager.retryFailed(song.id)
        } else {
            offlineManager.downloadTrack(song)
        }
    }
}

