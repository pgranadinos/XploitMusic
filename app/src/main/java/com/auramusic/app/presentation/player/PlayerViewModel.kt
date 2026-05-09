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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import com.auramusic.app.domain.model.PlaybackState

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
    val queueManager: QueueManager,
    private val offlineManager: OfflineManager,
    private val analyticsLogger: AuraAnalyticsLogger
) : ViewModel() {

    // ── Step 1: Explicit Playback State Machine ──

    companion object {
        private const val TAG = "XploitDebug"
        private const val STREAM_MAX_RETRIES = 2
        private const val STREAM_INITIAL_DELAY_MS = 500L
    }

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong = _currentSong.asStateFlow()

    private val _lyrics =
        MutableStateFlow<List<com.auramusic.app.domain.model.LyricLine>>(emptyList())
    val lyrics = _lyrics.asStateFlow()

    private val _playlist = MutableStateFlow<List<Song>>(emptyList())
    private var _currentIndex = -1

    // Autoplay queue (from relatedStreams)
    private val _autoplayQueue = MutableStateFlow<List<Song>>(emptyList())

    // Exposed UpNext queue for UI
    val upNext: StateFlow<List<Song>> = kotlinx.coroutines.flow.combine(_playlist, _autoplayQueue) { playlist, autoplay ->
        val upcomingPlaylist = if (playlist.isNotEmpty() && _currentIndex >= 0 && _currentIndex < playlist.size - 1) {
            playlist.drop(_currentIndex + 1)
        } else emptyList()
        upcomingPlaylist + autoplay
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Track played song IDs to avoid repeats in autoplay radio (Thread-safe to prevent crashes during skip)
    private val _playedSongIds: MutableSet<String> = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<String, Boolean>())

    // Autoplay enabled state
    private val _autoplayEnabled = MutableStateFlow(true)
    val autoplayEnabled = _autoplayEnabled.asStateFlow()

    // Shuffle & Repeat states
    private val _isShuffleEnabled = MutableStateFlow(false)
    val isShuffleEnabled = _isShuffleEnabled.asStateFlow()

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

        // Fallback: Listen for track ended (entire queue exhausted).
        // This fires when there are NO more items in ExoPlayer's queue.
        viewModelScope.launch {
            playerRepository.trackEnded.collect {
                Log.d(TAG, "PlayerVM: Track ended (queue empty), autoplay=${_autoplayEnabled.value}")
                if (_autoplayEnabled.value) {
                    autoplayNext()
                }
            }
        }

        // Listen for skip requests from the media notification (via SkipHandler)
        viewModelScope.launch {
            skipHandler.skipRequests.collect { direction ->
                when (direction) {
                    SkipHandler.SkipDirection.NEXT -> {
                        Log.d(TAG, "PlayerVM: SkipHandler → skipNext()")
                        skipNext()
                    }
                    SkipHandler.SkipDirection.PREVIOUS -> {
                        Log.d(TAG, "PlayerVM: SkipHandler → skipPrevious()")
                        skipPrevious()
                    }
                }
            }
        }

        // Listen for ExoPlayer errors (network failures, decode errors, etc.)
        // Step 4 (RC-2 fix): Routes through skipNext() which respects the state guard
        viewModelScope.launch {
            playerRepository.playerError.collect { errorMsg ->
                if (errorMsg != null) {
                    Log.e(TAG, "PlayerVM: Player error received: $errorMsg")
                    _errorMessage.value = errorMsg
                    _playbackState.value = PlaybackState.Error(errorMsg)
                    triggerAutoSkip()
                }
            }
        }
    }

    /**
     * UNIQUE entry point for UI state changes regarding the current song.
     * Called ONLY when ExoPlayer confirms a transition via mediaItemTransition.
     *
     * CRITICAL: Even for same-song (optimistic-update match), we MUST run
     * _playedSongIds.add() and _currentIndex update to prevent infinite same-song loops.
     */
    private fun onSongTransitioned(song: Song) {
        val isSameSong = _currentSong.value?.id == song.id
        
        if (!isSameSong) {
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
        _playedSongIds.add(song.id)

        // ALWAYS: push to history stack, but ONLY at live head
        // (don't grow history while navigating backward — that causes infinite append)
        if (_historyCursor < 0) {
            val lastInHistory = _historyStack.lastOrNull()
            if (lastInHistory?.id != song.id) {
                _historyStack.addLast(song)
                if (_historyStack.size > 50) _historyStack.removeFirst()
            }
        }

        // ALWAYS: update playlist index
        val playlistIndex = _playlist.value.indexOfFirst { it.id == song.id }
        if (playlistIndex >= 0) {
            _currentIndex = playlistIndex
        } else {
            // Consume from autoplay queue
            val queue = _autoplayQueue.value
            val queueIndex = queue.indexOfFirst { it.id == song.id }
            if (queueIndex >= 0) {
                _autoplayQueue.value = queue.drop(queueIndex + 1)
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
            
            // Record play locally and in BFF (debounced)
            try { favoritesRepository.recordPlay(song) } catch (_: Exception) {}
            try { streamRepository.recordTelemetryPlay(song.id) } catch (_: Exception) {}

            // Fetch lyrics in background (debounced)
            try {
                val result = getLyricsUseCase(song.title, song.artist, song.durationSeconds.toDouble())
                if (result is Resource.Success) {
                    val parsed = com.auramusic.app.core.util.LrcParser.parse(result.data?.syncedLyrics)
                    _lyrics.value = parsed
                    Log.d(TAG, "PlayerVM/Perf: Lyrics loaded post-debounce: ${parsed.size} lines")
                }
            } catch (_: Exception) {}

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

         return _playlist.value.firstOrNull { it.id == mediaId }
            ?: _autoplayQueue.value.firstOrNull { it.id == mediaId }
    }

    // ── Editable Queue Actions ──
    fun moveSongInQueue(fromIndex: Int, toIndex: Int) {
        val currentUpNext = upNext.value.toMutableList()
        if (fromIndex in currentUpNext.indices && toIndex in currentUpNext.indices) {
            val item = currentUpNext.removeAt(fromIndex)
            currentUpNext.add(toIndex, item)
            
            // Flatten into autoplayQueue to preserve order and simplify state
            val played = if (_playlist.value.isNotEmpty() && _currentIndex >= 0) {
                 _playlist.value.take(_currentIndex + 1)
            } else emptyList()
            
            _playlist.value = played
            _autoplayQueue.value = currentUpNext
        }
    }

    fun removeSongFromQueue(index: Int) {
        val currentUpNext = upNext.value.toMutableList()
        if (index in currentUpNext.indices) {
            currentUpNext.removeAt(index)
            
            val played = if (_playlist.value.isNotEmpty() && _currentIndex >= 0) {
                 _playlist.value.take(_currentIndex + 1)
            } else emptyList()
            
            _playlist.value = played
            _autoplayQueue.value = currentUpNext
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
        if (playlist.isNotEmpty()) {
            _playlist.value = playlist
            _currentIndex = playlist.indexOfFirst { it.id == song.id }
        } else if (_playlist.value.isEmpty()) {
            _playlist.value = listOf(song)
            _currentIndex = 0
        }
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
        _playlist.value = emptyList()
        _currentIndex = -1
        _playedSongIds.clear()
        _playedSongIds.add(song.id)
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
        // DO NOT mutate _currentSong here. Wait for ExoPlayer to emit transition.
        Log.d(TAG, "PlayerVM: playInternal command for '${song.title}' gen=$generation. ExoPlayer will take control.")
        val bufferStartTime = System.currentTimeMillis()
        analyticsLogger.logPlayAttempt(song.id, "PlayerViewModel")
        
        _errorMessage.value = null
        _playbackState.value = PlaybackState.Buffering(song)
        _prefetchedNextSong = song // Temporarily hold it so findSongByMediaId can resolve it
        _prefetchJob?.cancel()

        // CRITICAL: set _ignoringTransition=true BEFORE clearQueue so the spurious
        // ExoPlayer STATE_ENDED (fired when queue becomes empty) does NOT trigger autoplayNext.
        // forceSkipToNext already does this correctly; playInternal was missing it.
        _ignoringTransition = true

        // Clear existing queues except the playlist we are iterating on
        try {
            playerRepository.pause()
            playerRepository.clearQueue()
        } catch (e: Exception) {
            Log.w(TAG, "PlayerVM: Pause/clearQueue failed: ${e.message}")
        }

        val isLocal = song.id.startsWith("local_")
        val offlinePath = offlineManager.getOfflineFilePath(song.id)

        // ── OFFLINE DEGRADATION (C5): Play downloaded instance if available ──
        if (offlinePath != null) {
            try {
                Log.d(TAG, "PlayerVM: 📶✅ Degradación Elegante — Jugando archivo offline ($offlinePath)")
                playerRepository.prepare(offlinePath, song)
                playerRepository.play()
                _playbackState.value = PlaybackState.Playing(song, 0L)
                _errorMessage.value = null
                val timeMs = System.currentTimeMillis() - bufferStartTime
                com.auramusic.app.utils.AppLogger.logPlaybackState(song.id, "OFFLINE_PLAY", timeMs)
                analyticsLogger.logPlaySuccess(song.id, timeMs)
                
                // Track usage to avoid LRU Eviction
                viewModelScope.launch { offlineManager.touchAccess(song.id) }
            } catch (e: Exception) {
                Log.e(TAG, "PlayerVM: ❌ Offline playback error: ${e.message}", e)
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
                playerRepository.prepare(song.streamUrl!!, song)
                playerRepository.play()
                _playbackState.value = PlaybackState.Playing(song, 0L)
                _errorMessage.value = null
                val timeMs = System.currentTimeMillis() - bufferStartTime
                com.auramusic.app.utils.AppLogger.logPlaybackState(song.id, "LOCAL_PLAY", timeMs)
                analyticsLogger.logPlaySuccess(song.id, timeMs)
            } catch (e: Exception) {
                Log.e(TAG, "PlayerVM: ❌ Local playback error: ${e.message}", e)
                val timeMs = System.currentTimeMillis() - bufferStartTime
                com.auramusic.app.utils.AppLogger.logPlaybackState(song.id, "ERROR", timeMs)
                analyticsLogger.logPlayError(song.id, e.message ?: "Error local", timeMs)
                _errorMessage.value = "Error al reproducir archivo local"
                _ignoringTransition = false
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

        // 2. Fetch lyrics
        viewModelScope.launch {
            try {
                val result = getLyricsUseCase(song.title, song.artist, song.durationSeconds.toDouble())
                if (result is Resource.Success) {
                    val parsed = com.auramusic.app.core.util.LrcParser.parse(result.data?.syncedLyrics)
                    _lyrics.value = parsed
                    Log.d(TAG, "PlayerVM: Lyrics loaded: ${parsed.size} lines")
                }
            } catch (e: Exception) {
                Log.w(TAG, "PlayerVM: Lyrics fetch failed: ${e.message}")
            }
        }

        // 3. Fetch related songs for autoplay queue (shuffled, Spotify-like)
        // CRITICAL FIX: Do NOT call prefetchNextSong() here to prevent race conditions 
        // with the ExoPlayer instance prior to the primary stream loading.
        viewModelScope.launch {
            replenishAutoplayQueue(song)
        }

        // 4. MAIN: Fetch stream URL with retry + start playback
        try {
            Log.d(TAG, "PlayerVM: Fetching stream URL for ${song.id}...")
            // Step 7: Retry with exponential backoff
            when (val result = getStreamUrlWithRetry(song.id)) {
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
            _errorMessage.value = "Error inesperado: ${e.localizedMessage}"
            _playbackState.value = PlaybackState.Error(e.localizedMessage ?: "Error")
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
        } catch (e: Exception) {
            Log.w(TAG, "PlayerVM: relatedStreams fetch failed: ${e.message}")
        }

        // Source 2: Genre-based search for variety
        try {
            val artist = song.artist.takeIf { it.isNotBlank() && it != "Unknown" }
            if (artist != null) {
                val searchQueries = listOf("${artist} songs", "${artist} canciones")
                val query = searchQueries.random()
                val searchResult = streamRepository.searchSongs(query)
                if (searchResult is Resource.Success && !searchResult.data.isNullOrEmpty()) {
                    allNewSongs.addAll(searchResult.data)
                    Log.d(TAG, "PlayerVM: Source 2 (genre search '$query'): ${searchResult.data.size} songs")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "PlayerVM: Genre search failed: ${e.message}")
        }

        // De-duplicate, filter played, filter Mix/playlist entries (no direct stream URL)
        val MIX_PATTERN = Regex("^(Mix |Playlist |Best of |Top |Hits |Mix:)", RegexOption.IGNORE_CASE)
        val seenIds = mutableSetOf<String>()
        val uniqueSongs = allNewSongs.filter { s ->
            s.id != song.id
                && s.id !in _playedSongIds
                && seenIds.add(s.id)
                && !MIX_PATTERN.containsMatchIn(s.title) // Exclude YouTube Mix playlists
                && s.title.length < 120                   // Exclude giant playlist titles
        }

        // ── Anti-Artist Clustering: Round-Robin Interleaving ──
        // Group by artist, shuffle within each group for variety
        val groupedByArtist = uniqueSongs.groupBy { it.artist }.values
            .map { it.shuffled() }  // Shuffle within each artist group
            .toList()
            .shuffled()  // Randomize artist order

        val interleavedQueue = mutableListOf<Song>()
        var round = 0
        var added = true
        while (added) {
            added = false
            for (group in groupedByArtist) {
                if (round < group.size) {
                    interleavedQueue.add(group[round])
                    added = true
                }
            }
            round++
        }

        // Ensure first song is NOT from the same artist as the current song
        val currentArtist = song.artist
        val firstDiffIdx = interleavedQueue.indexOfFirst { it.artist != currentArtist }
        if (firstDiffIdx > 0) {
            // Swap the first different-artist song to position 0
            val temp = interleavedQueue[0]
            interleavedQueue[0] = interleavedQueue[firstDiffIdx]
            interleavedQueue[firstDiffIdx] = temp
        }

        // Merge with existing queue (keep unplayed items, append new unique ones)
        val existingQueue = _autoplayQueue.value.filter { it.id !in _playedSongIds }
        val existingIds = existingQueue.map { it.id }.toSet()
        val brandNew = interleavedQueue.filter { it.id !in existingIds }

        _autoplayQueue.value = existingQueue + brandNew
        Log.d(TAG, "PlayerVM: Autoplay queue replenished: ${_autoplayQueue.value.size} total (${brandNew.size} new, ${groupedByArtist.size} artists)")

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
        // 1. Check playlist (only if we have an active playlist, not from playSingleSong)
        if (_playlist.value.isNotEmpty() && _currentIndex >= 0 && _currentIndex < _playlist.value.lastIndex) {
            return _playlist.value[_currentIndex + 1]
        }
        // 2. Check autoplay queue — skip already-played songs
        val queue = _autoplayQueue.value
        val nextUnplayed = queue.firstOrNull { it.id !in _playedSongIds }
        if (nextUnplayed != null) {
            return nextUnplayed
        }
        // 3. If all songs in queue were played, return first anyway (loop)
        return queue.firstOrNull()
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

        // 2. No pre-fetched item — do full playInternal
        // Check playlist first
        if (_currentIndex < _playlist.value.lastIndex) {
            _currentIndex++
            Log.d(TAG, "PlayerVM: Autoplay from playlist [$_currentIndex]")
            playInternal(_playlist.value[_currentIndex])
            return
        }

        // Use autoplay queue (relatedStreams) — pick first unplayed song
        val queue = _autoplayQueue.value
        val nextFromQueue = queue.firstOrNull { it.id !in _playedSongIds } ?: queue.firstOrNull()
        if (nextFromQueue != null) {
            _autoplayQueue.value = queue.filter { it.id != nextFromQueue.id }
            _playedSongIds.add(nextFromQueue.id)
            Log.d(TAG, "PlayerVM: Autoplay from related: ${nextFromQueue.title}")
            analyticsLogger.logAutoplayStarted(_currentSong.value?.id ?: "none", nextFromQueue.id)
            playInternal(nextFromQueue)
            return
        }

        // 3. Queue empty — fetch fresh related songs from the current song
        val currentSong = _currentSong.value ?: return
        Log.d(TAG, "PlayerVM: Autoplay queue empty — fetching fresh related for '${currentSong.title}'")
        try {
            val related = streamRepository.getRelatedSongs(currentSong.id)
            if (related is Resource.Success && !related.data.isNullOrEmpty()) {
                val fresh = related.data.filter { it.id !in _playedSongIds }.shuffled()
                if (fresh.isNotEmpty()) {
                    val nextSong = fresh.first()
                    _autoplayQueue.value = fresh.drop(1)
                    _playedSongIds.add(nextSong.id)
                    Log.d(TAG, "PlayerVM: Autoplay from fresh related: ${nextSong.title}")
                    analyticsLogger.logAutoplayStarted(currentSong.id, nextSong.id)
                    playInternal(nextSong)
                    return
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "PlayerVM: Fresh related fetch failed: ${e.message}")
        }

        // 4. Ultimate fallback: search for artist mix
        val currentArtist = currentSong.artist
        Log.d(TAG, "PlayerVM: Autoplay fallback: searching '$currentArtist mix'")
        try {
            val result = streamRepository.searchSongs("$currentArtist mix")
            if (result is Resource.Success && !result.data.isNullOrEmpty()) {
                val nextSong = result.data.firstOrNull { it.id !in _playedSongIds }
                    ?: result.data.first()
                _playedSongIds.add(nextSong.id)
                analyticsLogger.logAutoplayStarted(currentSong.id, nextSong.id)
                playInternal(nextSong)
            } else {
                Log.w(TAG, "PlayerVM: Autoplay fallback returned no results")
            }
        } catch (e: Exception) {
            Log.e(TAG, "PlayerVM: Autoplay fallback failed: ${e.message}", e)
        }
    }

    fun toggleAutoplay() {
        _autoplayEnabled.value = !_autoplayEnabled.value
        Log.d(TAG, "PlayerVM: Autoplay toggled to ${_autoplayEnabled.value}")
    }

    fun toggleShuffle() {
        _isShuffleEnabled.value = !_isShuffleEnabled.value
        playerRepository.setShuffleModeEnabled(_isShuffleEnabled.value)
        Log.d(TAG, "PlayerVM: Shuffle toggled to ${_isShuffleEnabled.value}")
    }

    fun toggleRepeat() {
        // Cycle: OFF(0) → ALL(2) → ONE(1) → OFF(0)
        _repeatMode.value = when (_repeatMode.value) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        playerRepository.setRepeatMode(_repeatMode.value)
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

    // ── Offline Degradation UI Triggers (C5) ──
    fun downloadCurrentSong() {
        val song = _currentSong.value ?: return
        offlineManager.downloadTrack(song)
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
            } else {
                playerRepository.play()
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
                _playedSongIds.remove(forwardSong.id)
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

            // 5. Clear ExoPlayer queue + suppress stale transitions (on Main thread)
            withContext(Dispatchers.Main) {
                _ignoringTransition = true
                try {
                    playerRepository.clearQueue()
                } catch (e: Exception) {
                    Log.w(TAG, "PlayerVM: clearQueue failed: ${e.message}")
                }
            }

            // 6. Resolve stream URL (network call — runs on IO thread)
            try {
                val isLocal = nextSong.id.startsWith("local_")
                if (isLocal && !nextSong.streamUrl.isNullOrBlank()) {
                    withContext(Dispatchers.Main) {
                        playerRepository.prepare(nextSong.streamUrl!!, nextSong)
                        playerRepository.play()
                        _ignoringTransition = false // Allow real transition event through
                    }
                } else {
                    // Step 7: Retry with exponential backoff for skip path too
                    when (val result = getStreamUrlWithRetry(nextSong.id)) {
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
            viewModelScope.launch {
                try {
                    val lyricsResult = getLyricsUseCase(songForBackground.title, songForBackground.artist, songForBackground.durationSeconds.toDouble())
                    if (lyricsResult is Resource.Success) {
                        _lyrics.value = com.auramusic.app.core.util.LrcParser.parse(lyricsResult.data?.syncedLyrics)
                    }
                } catch (_: Exception) {}
            }
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
        // 1. Check playlist
        if (_playlist.value.isNotEmpty() && _currentIndex >= 0 && _currentIndex < _playlist.value.lastIndex) {
            _currentIndex++
            return _playlist.value[_currentIndex]
        }

        // 2. Check autoplay queue — pick first unplayed
        val queue = _autoplayQueue.value
        val next = queue.firstOrNull { it.id !in _playedSongIds }
        if (next != null) {
            _autoplayQueue.value = queue.filter { it.id != next.id }

            // PROACTIVE REPLENISHMENT: if queue is getting low, refill in background
            val remainingUnplayed = _autoplayQueue.value.count { it.id !in _playedSongIds }
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

    fun skipPrevious() {
        Log.d(TAG, "PlayerVM: skipPrevious called, index=$_currentIndex, cursor=$_historyCursor, history=${_historyStack.size}")
        val pos = currentPosition.value

        // If more than 5s into the song, restart it (Spotify-like behavior)
        if (pos > 5_000L) {
            seekTo(0)
            return
        }

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
            _playedSongIds.remove(previousSong.id)
            playHistorySong(previousSong, targetIdx)  // preserves cursor
            return
        }

        // Nothing to go back to — restart current song
        Log.d(TAG, "PlayerVM: skipPrevious — history exhausted, restarting current song")
        seekTo(0)
    }

    /**
     * Step 7: Stream URL fetch with exponential backoff retry.
     * Retries up to STREAM_MAX_RETRIES times with delays: 500ms, 1000ms.
     * This handles transient network failures and Piped API timeouts.
     */
    private suspend fun getStreamUrlWithRetry(songId: String): Resource<String> {
        var lastError: Resource<String> = Resource.Error("Unknown error")

        repeat(STREAM_MAX_RETRIES + 1) { attempt ->
            if (attempt > 0) {
                val delayMs = STREAM_INITIAL_DELAY_MS * (1 shl (attempt - 1))
                Log.d(TAG, "PlayerVM: Stream retry #$attempt after ${delayMs}ms for songId=$songId")
                delay(delayMs)
            }

            val result = getAudioStreamUseCase(songId)
            when (result) {
                is Resource.Success -> {
                    if (attempt > 0) {
                        Log.d(TAG, "PlayerVM: Stream retry #$attempt succeeded for songId=$songId")
                    }
                    return result
                }
                is Resource.Error -> {
                    lastError = result
                    Log.w(TAG, "PlayerVM: Stream attempt #${attempt + 1} failed: ${result.message}")
                }
                else -> {}
            }
        }

        return lastError
    }

    // ── Auto-Skip utility for failures ──
    private var _autoSkipJob: Job? = null

    private fun triggerAutoSkip() {
        _autoSkipJob?.cancel()
        _autoSkipJob = viewModelScope.launch {
            Log.d(TAG, "PlayerVM: ⚠️ Error detectado, saltando automáticamente a la próxima pista en 800ms...")
            delay(800) // Reduced from 2500ms to match Spotify-like responsiveness
            if (_playbackState.value is PlaybackState.Error || _playbackState.value is PlaybackState.Idle) {
                _playbackState.value = PlaybackState.Idle // Reset to allow skipNext to run
                skipNext()
            }
        }
    }

    // ── User Actions ──

    fun toggleDownload() {
        val song = _currentSong.value ?: return
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

