package com.auramusic.app.presentation.player

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auramusic.app.core.util.Resource
import com.auramusic.app.data.service.SkipHandler
import com.auramusic.app.domain.model.Song
import com.auramusic.app.domain.repository.FavoritesRepository
import com.auramusic.app.domain.repository.PlayerRepository
import com.auramusic.app.domain.repository.StreamRepository
import com.auramusic.app.domain.usecase.GetAudioStreamUseCase
import com.auramusic.app.domain.usecase.ToggleFavoriteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playerRepository: PlayerRepository,
    private val getAudioStreamUseCase: GetAudioStreamUseCase,
    private val getLyricsUseCase: com.auramusic.app.domain.usecase.GetLyricsUseCase,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    private val favoritesRepository: FavoritesRepository,
    private val streamRepository: StreamRepository,
    private val skipHandler: SkipHandler
) : ViewModel() {

    companion object {
        private const val TAG = "XploitDebug"
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

    // Track played song IDs to avoid repeats in autoplay radio
    private val _playedSongIds = mutableSetOf<String>()

    // Autoplay enabled state
    private val _autoplayEnabled = MutableStateFlow(true)
    val autoplayEnabled = _autoplayEnabled.asStateFlow()

    // Loading state
    private val _isLoadingStream = MutableStateFlow(false)
    val isLoadingStream = _isLoadingStream.asStateFlow()

    // Error message
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    // Track the pre-fetched next song so we can update _currentSong on transition
    private var _prefetchedNextSong: Song? = null
    private var _prefetchJob: Job? = null

    // Job for skip-to-next — cancelled on each new press to prevent race conditions
    private var _skipJob: Job? = null

    // Flag to ignore transitions caused by our own prepare() calls
    private var _ignoringTransition = false

    // Favorite state — reacts to currentSong changes
    val isFavorite: StateFlow<Boolean> = _currentSong
        .flatMapLatest { song ->
            if (song != null) favoritesRepository.isFavorite(song.id)
            else flowOf(false)
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    val isPlaying: StateFlow<Boolean> = playerRepository.isPlaying
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    val currentPosition: StateFlow<Long> = playerRepository.currentPosition
        .stateIn(viewModelScope, SharingStarted.Lazily, 0L)

    val duration: StateFlow<Long> = playerRepository.duration
        .stateIn(viewModelScope, SharingStarted.Lazily, 0L)

    init {
        // CRITICAL: Listen for ExoPlayer media item transitions (auto or manual).
        // This fires when ExoPlayer moves to the next queued item, which is the
        // primary mechanism for syncing the UI and notification on song change.
        viewModelScope.launch {
            playerRepository.mediaItemTransition.collect { mediaId ->
                Log.d(TAG, "PlayerVM: mediaItemTransition — mediaId='$mediaId', ignoring=$_ignoringTransition")

                if (_ignoringTransition) {
                    // This transition was caused by our own prepare() call — ignore it
                    _ignoringTransition = false
                    return@collect
                }

                if (mediaId == null) return@collect

                // ExoPlayer auto-transitioned to the pre-fetched next item
                val prefetched = _prefetchedNextSong
                if (prefetched != null && prefetched.id == mediaId) {
                    Log.d(TAG, "PlayerVM: 🎵 Auto-transition to pre-fetched: '${prefetched.title}'")
                    _prefetchedNextSong = null
                    onSongTransitioned(prefetched)
                } else {
                    // Try matching from playlist or autoplay queue
                    val matchedSong = findSongByMediaId(mediaId)
                    if (matchedSong != null) {
                        Log.d(TAG, "PlayerVM: 🎵 Auto-transition to known song: '${matchedSong.title}'")
                        onSongTransitioned(matchedSong)
                    } else {
                        Log.w(TAG, "PlayerVM: Unknown mediaId in transition: '$mediaId'")
                    }
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
                        Log.d(TAG, "PlayerVM: SkipHandler → forceSkipToNext()")
                        // Use same cancellation pattern as UI skipNext
                        _skipJob?.cancel()
                        viewModelScope.launch { playerRepository.pause() }
                        _skipJob = viewModelScope.launch { forceSkipToNext() }
                    }
                    SkipHandler.SkipDirection.PREVIOUS -> {
                        Log.d(TAG, "PlayerVM: SkipHandler → skipPrevious()")
                        skipPrevious()
                    }
                }
            }
        }
    }

    /**
     * Called when ExoPlayer has auto-transitioned to a new song.
     * Updates the UI state, fetches lyrics, refreshes related songs, and pre-fetches the next song.
     */
    private fun onSongTransitioned(song: Song) {
        _currentSong.value = song
        _lyrics.value = emptyList()
        _playedSongIds.add(song.id)

        // Update playlist index if applicable
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

        // Record play
        viewModelScope.launch {
            try {
                favoritesRepository.recordPlay(song)
            } catch (e: Exception) {
                Log.w(TAG, "PlayerVM: recordPlay failed: ${e.message}")
            }
        }

        // Fetch lyrics in background
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

        // ALWAYS refresh related songs for the new song (Spotify-like infinite radio)
        viewModelScope.launch {
            replenishAutoplayQueue(song)
            prefetchNextSong()
        }
    }

    /**
     * Tries to find a Song object by mediaId from playlist or autoplay queue.
     */
    private fun findSongByMediaId(mediaId: String): Song? {
        return _playlist.value.firstOrNull { it.id == mediaId }
            ?: _autoplayQueue.value.firstOrNull { it.id == mediaId }
    }

    fun playSong(song: Song, playlist: List<Song> = emptyList()) {
        Log.d(TAG, "PlayerVM: playSong called: ${song.title} (id=${song.id}), playlist size=${playlist.size}")
        if (playlist.isNotEmpty()) {
            _playlist.value = playlist
            _currentIndex = playlist.indexOfFirst { it.id == song.id }
        } else if (_playlist.value.isEmpty()) {
            _playlist.value = listOf(song)
            _currentIndex = 0
        }
        // Launch playback in viewModelScope — NOT coroutineScope 
        viewModelScope.launch {
            playInternal(song)
        }
    }

    /**
     * Plays a single song WITHOUT queueing a playlist.
     * Used for search results: clears any existing queue, plays the song,
     * and relies on relatedStreams (autoplay) for the next song.
     */
    fun playSingleSong(song: Song) {
        Log.d(TAG, "PlayerVM: playSingleSong called: ${song.title} (id=${song.id})")
        _playlist.value = emptyList()  // No playlist — autoplay queue is the source
        _currentIndex = -1
        _playedSongIds.clear()
        _playedSongIds.add(song.id)
        viewModelScope.launch {
            playInternal(song)
        }
    }

    /**
     * Core playback function.
     * CRITICAL FIX (FALLO 4): This must NOT use coroutineScope{} because that
     * waits for ALL children to complete (including slow getRelatedSongs).
     * Instead, we launch background tasks independently in viewModelScope.
     */
    private suspend fun playInternal(song: Song) {
        _currentSong.value = song
        _lyrics.value = emptyList()
        _errorMessage.value = null
        _isLoadingStream.value = true
        _autoplayQueue.value = emptyList()
        _prefetchedNextSong = null
        _prefetchJob?.cancel()

        // Mark that the next onMediaItemTransition is caused by our prepare() call
        _ignoringTransition = true

        Log.d(TAG, "PlayerVM: playInternal start for '${song.title}'")

        // Pause current playback and clear any previously queued items
        try {
            playerRepository.pause()
            playerRepository.clearQueue()
        } catch (e: Exception) {
            Log.w(TAG, "PlayerVM: Pause/clearQueue failed (ok if first play): ${e.message}")
        }

        val isLocal = song.id.startsWith("local_")

        // ── LOCAL SONG: play directly via content URI ──
        if (isLocal && !song.streamUrl.isNullOrBlank()) {
            try {
                Log.d(TAG, "PlayerVM: ✅ Local song — using content URI directly")
                playerRepository.prepare(song.streamUrl!!, song)
                _errorMessage.value = null
            } catch (e: Exception) {
                Log.e(TAG, "PlayerVM: ❌ Local playback error: ${e.message}", e)
                _errorMessage.value = "Error al reproducir archivo local"
                _ignoringTransition = false
            } finally {
                _isLoadingStream.value = false
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
        viewModelScope.launch {
            replenishAutoplayQueue(song)
            prefetchNextSong()
        }

        // 4. MAIN: Fetch stream URL and start playback — this is the critical path
        try {
            Log.d(TAG, "PlayerVM: Fetching stream URL for ${song.id}...")
            when (val result = getAudioStreamUseCase(song.id)) {
                is Resource.Success -> {
                    val streamUrl = result.data
                    if (!streamUrl.isNullOrBlank()) {
                        Log.d(TAG, "PlayerVM: ✅ Stream URL obtained (${streamUrl.take(80)}...)")
                        Log.d(TAG, "PlayerVM: Calling playerRepository.prepare()...")
                        playerRepository.prepare(streamUrl, song)
                        Log.d(TAG, "PlayerVM: ✅ prepare() called successfully")
                        _errorMessage.value = null

                        // After playback starts, try to pre-fetch the next song
                        // from the playlist (autoplay queue may not be ready yet)
                        launchPrefetch()
                    } else {
                        Log.e(TAG, "PlayerVM: ❌ Stream URL is null or blank")
                        _errorMessage.value = "No se encontró la URL del stream"
                        _ignoringTransition = false
                    }
                }
                is Resource.Error -> {
                    Log.e(TAG, "PlayerVM: ❌ Stream error: ${result.message}")
                    _errorMessage.value = result.message ?: "Error desconocido al obtener audio"
                    _ignoringTransition = false
                }
                else -> {
                    Log.e(TAG, "PlayerVM: ❌ Unexpected resource state")
                    _ignoringTransition = false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "PlayerVM: ❌ Exception in stream fetch: ${e.javaClass.simpleName}: ${e.message}", e)
            _errorMessage.value = "Error inesperado: ${e.localizedMessage}"
            _ignoringTransition = false
        } finally {
            _isLoadingStream.value = false
            Log.d(TAG, "PlayerVM: playInternal complete for '${song.title}'")
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
                val searchQueries = listOf("$artist mix", "$artist radio")
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

        // De-duplicate, filter played
        val seenIds = mutableSetOf<String>()
        val uniqueSongs = allNewSongs.filter { s ->
            s.id != song.id && s.id !in _playedSongIds && seenIds.add(s.id)
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
                    }
                }
                is Resource.Error -> {
                    Log.w(TAG, "PlayerVM: Pre-fetch failed: ${result.message}")
                }
                else -> { /* ignore */ }
            }
        } catch (e: Exception) {
            Log.w(TAG, "PlayerVM: Pre-fetch exception: ${e.message}")
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
        Log.d(TAG, "PlayerVM: skipNext called (UI button)")

        // 1. Cancel any in-flight skip request (prevents race condition on rapid presses)
        _skipJob?.cancel()

        // 2. Pause the player IMMEDIATELY so the old song stops
        viewModelScope.launch { playerRepository.pause() }

        // 3. Launch new cancellable skip job
        _skipJob = viewModelScope.launch {
            forceSkipToNext()
        }
    }

    /**
     * Force-skips to the next song immediately.
     * Called from both the UI skip button and the notification skip handler.
     *
     * This function is designed to be cancellation-safe:
     * - UI is updated IMMEDIATELY (song title, artwork, loading indicator)
     * - Stream URL resolution happens after → if cancelled by another skip, no harm done
     */
    private suspend fun forceSkipToNext() {
        // 1. If ExoPlayer has a pre-fetched next item, use it directly (instant)
        if (playerRepository.hasNextMediaItem()) {
            Log.d(TAG, "PlayerVM: forceSkipToNext — ExoPlayer has next item, using skipToNext()")
            playerRepository.skipToNext()
            return
        }

        // 2. Pull next song from playlist or autoplay queue
        val nextSong = getNextSongFromQueue()
        if (nextSong != null) {
            Log.d(TAG, "PlayerVM: forceSkipToNext — playing: ${nextSong.title}")

            // 3. Update UI IMMEDIATELY so user sees the song changed
            _currentSong.value = nextSong
            _lyrics.value = emptyList()
            _isLoadingStream.value = true
            _errorMessage.value = null
            _playedSongIds.add(nextSong.id)
            _prefetchedNextSong = null
            _prefetchJob?.cancel()
            _ignoringTransition = true

            // 4. Clear ExoPlayer queue
            try {
                playerRepository.clearQueue()
            } catch (e: Exception) {
                Log.w(TAG, "PlayerVM: clearQueue failed: ${e.message}")
            }

            // 5. Resolve stream URL (this is the cancellable part — if user presses Next again,
            //    this coroutine gets cancelled and the new one takes over)
            try {
                val isLocal = nextSong.id.startsWith("local_")
                if (isLocal && !nextSong.streamUrl.isNullOrBlank()) {
                    playerRepository.prepare(nextSong.streamUrl!!, nextSong)
                } else {
                    when (val result = getAudioStreamUseCase(nextSong.id)) {
                        is Resource.Success -> {
                            val streamUrl = result.data
                            if (!streamUrl.isNullOrBlank()) {
                                playerRepository.prepare(streamUrl, nextSong)
                                Log.d(TAG, "PlayerVM: forceSkipToNext — playback started")
                            } else {
                                _errorMessage.value = "No se encontró la URL del stream"
                                _ignoringTransition = false
                            }
                        }
                        is Resource.Error -> {
                            _errorMessage.value = result.message ?: "Error al obtener audio"
                            _ignoringTransition = false
                        }
                        else -> { _ignoringTransition = false }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Job was cancelled by a newer skip press — this is expected, rethrow
                Log.d(TAG, "PlayerVM: forceSkipToNext cancelled (new skip pressed)")
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "PlayerVM: forceSkipToNext stream error: ${e.message}", e)
                _errorMessage.value = "Error: ${e.localizedMessage}"
                _ignoringTransition = false
            } finally {
                _isLoadingStream.value = false
            }

            // 6. Background tasks: lyrics, related songs, prefetch
            viewModelScope.launch {
                try { favoritesRepository.recordPlay(nextSong) } catch (_: Exception) {}
            }
            viewModelScope.launch {
                try {
                    val lyricsResult = getLyricsUseCase(nextSong.title, nextSong.artist, nextSong.durationSeconds.toDouble())
                    if (lyricsResult is Resource.Success) {
                        _lyrics.value = com.auramusic.app.core.util.LrcParser.parse(lyricsResult.data?.syncedLyrics)
                    }
                } catch (_: Exception) {}
            }
            viewModelScope.launch {
                replenishAutoplayQueue(nextSong)
                prefetchNextSong()
            }
            return
        }

        // 5. Queue empty — fall through to autoplayNext which does fresh fetch + fallback
        Log.d(TAG, "PlayerVM: forceSkipToNext — queue empty, falling back to autoplayNext()")
        autoplayNext()
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
        Log.d(TAG, "PlayerVM: skipPrevious called, index=$_currentIndex")
        if (_currentIndex > 0) {
            _currentIndex--
            playSong(_playlist.value[_currentIndex])
        } else {
            seekTo(0)
        }
    }
}

