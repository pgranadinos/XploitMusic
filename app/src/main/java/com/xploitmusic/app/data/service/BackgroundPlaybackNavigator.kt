package com.xploitmusic.app.data.service

import android.util.Log
import com.xploitmusic.app.core.util.PlaybackLogManager
import com.xploitmusic.app.core.util.Resource
import com.xploitmusic.app.data.repository.OfflineManager
import com.xploitmusic.app.domain.model.Song
import com.xploitmusic.app.domain.repository.PlayerRepository
import com.xploitmusic.app.domain.repository.FavoritesRepository
import com.xploitmusic.app.domain.repository.QueueManager
import com.xploitmusic.app.domain.repository.StreamRepository
import com.xploitmusic.app.domain.recommendation.SongRecommendationSelector
import com.xploitmusic.app.domain.usecase.GetAudioStreamUseCase
import com.xploitmusic.app.presentation.player.NetworkErrorClassifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

internal sealed class ResolveResult {
    data class Success(val url: String) : ResolveResult()
    data class Unplayable(val reason: String) : ResolveResult()
    data class NetworkError(val cause: Throwable?, val message: String) : ResolveResult()
}

/** Handles notification navigation after the UI lifecycle has ended. */
@Singleton
class BackgroundPlaybackNavigator @Inject constructor(
    private val playerRepository: PlayerRepository,
    private val queueManager: QueueManager,
    private val favoritesRepository: FavoritesRepository,
    private val getAudioStreamUseCase: GetAudioStreamUseCase,
    private val streamRepository: StreamRepository,
    private val offlineManager: OfflineManager,
    private val playbackLogManager: PlaybackLogManager
) {
    companion object {
        private const val TAG = "XploitDebug"
        private const val MAX_UNPLAYABLE_SKIPS = 2

        private const val PREFETCH_RETRY_DELAY_1_MS = 2_000L
        private const val PREFETCH_RETRY_DELAY_2_MS = 5_000L
        private const val PREFETCH_RETRY_DELAY_3_MS = 10_000L
        private val PREFETCH_RETRY_DELAYS = listOf(PREFETCH_RETRY_DELAY_1_MS, PREFETCH_RETRY_DELAY_2_MS, PREFETCH_RETRY_DELAY_3_MS)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val navigationMutex = Mutex()
    private var navigationJob: Job? = null
    private var prefetchRetryJob: Job? = null
    private val prefetchedSources = mutableMapOf<String, Deferred<ResolveResult>>()

    /** Resolve the queued song before a car/headphone Next command needs it. */
    fun prefetchNextTrack() {
        prefetchRetryJob?.cancel()
        prefetchRetryJob = scope.launch {
            val next = queueManager.peekNext()
            if (next == null) {
                prefetchedSources.values.forEach { it.cancel() }
                prefetchedSources.clear()
                return@launch
            }
            val existingPrefetch = prefetchedSources[next.id]
            if (existingPrefetch != null) {
                if (existingPrefetch.isCompleted) {
                    try {
                        val result = existingPrefetch.await()
                        if (result is ResolveResult.Success) {
                            enqueuePrefetchedNextTrack(next, result.url)
                        } else {
                            forgetPrefetchedSource(next.id, existingPrefetch)
                        }
                    } catch (cancelled: CancellationException) {
                        currentCoroutineContext().ensureActive()
                        forgetPrefetchedSource(next.id, existingPrefetch)
                    } catch (error: Exception) {
                        forgetPrefetchedSource(next.id, existingPrefetch)
                        Log.w(TAG, "BackgroundNav: retrying prequeue for '${next.title}'", error)
                    }
                }
                return@launch
            }
            prefetchedSources.filterKeys { it != next.id }.values.forEach { it.cancel() }
            prefetchedSources.keys.retainAll(setOf(next.id))

            var attempt = 0
            while (attempt <= 3) {
                if (queueManager.peekNext()?.id != next.id) return@launch

                if (attempt > 0) {
                    val delayMs = PREFETCH_RETRY_DELAYS.getOrElse(attempt - 1) { 10_000L }
                    playbackLogManager.log("PREFETCH", "INFO", "Reintento $attempt/3 para '${next.title}'")
                    delay(delayMs)
                    if (queueManager.peekNext()?.id != next.id) return@launch
                }

                val prefetchedSource = scope.async(Dispatchers.IO) {
                    resolveSourceUncached(next)
                }
                prefetchedSources[next.id] = prefetchedSource

                try {
                    val result = prefetchedSource.await()
                    when (result) {
                        is ResolveResult.Success -> {
                            enqueuePrefetchedNextTrack(next, result.url)
                            return@launch
                        }
                        is ResolveResult.NetworkError -> {
                            forgetPrefetchedSource(next.id, prefetchedSource)
                            playbackLogManager.log("PREFETCH", "WARN", "No se pudo preparar la siguiente canción '${next.title}': ${result.message}")
                            attempt++
                        }
                        is ResolveResult.Unplayable -> {
                            forgetPrefetchedSource(next.id, prefetchedSource)
                            playbackLogManager.log("PREFETCH", "WARN", "No se pudo preparar la siguiente canción '${next.title}': ${result.reason}")
                            return@launch
                        }
                    }
                } catch (cancelled: CancellationException) {
                    currentCoroutineContext().ensureActive()
                    forgetPrefetchedSource(next.id, prefetchedSource)
                    Log.d(TAG, "BackgroundNav: prefetch superseded for '${next.title}'")
                    return@launch
                } catch (error: Exception) {
                    forgetPrefetchedSource(next.id, prefetchedSource)
                    Log.w(TAG, "BackgroundNav: prefetch failed for '${next.title}'", error)
                    playbackLogManager.log("PREFETCH", "WARN", "No se pudo preparar la siguiente canción '${next.title}': ${error.message}")
                    attempt++
                }
            }
        }
    }

    private fun forgetPrefetchedSource(songId: String, expected: Deferred<ResolveResult>) {
        if (prefetchedSources[songId] === expected) prefetchedSources.remove(songId)
    }

    /** Queue the resolved source in Media3 while the current track is still playing. */
    internal suspend fun enqueuePrefetchedNextTrack(song: Song, source: String) =
        navigationMutex.withLock {
            if (source.isBlank() || queueManager.peekNext()?.id != song.id) return@withLock
            if (playerRepository.hasNextMediaItem()) {
                Log.d(TAG, "BackgroundNav: native next item already queued; skipping '${song.title}'")
                return@withLock
            }

            playerRepository.addNextMediaItem(source, song)
            Log.d(TAG, "BackgroundNav: prequeued next track '${song.title}' in Media3")
        }

    fun cancelPendingNavigation() {
        scope.launch {
            prefetchRetryJob?.cancel()
            navigationJob?.cancel()
        }
    }

    private fun requestNavigation(automatic: Boolean = false, previous: Boolean = false) {
        scope.launch {
            if (automatic && navigationJob?.isActive == true) return@launch
            navigationJob?.cancel()
            navigationJob = scope.launch {
                try {
                    withTimeout(40_000L) {
                        when {
                            previous -> navigatePrevious()
                            automatic -> navigateAutomatically()
                            else -> navigateNext()
                        }
                    }
                } catch (error: TimeoutCancellationException) {
                    Log.w(TAG, "BackgroundNav: transition timed out", error)
                    playbackLogManager.log("PAUSA", "WARN", "origen=timeout-navegacion")
                    playerRepository.pause()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Log.w(TAG, "BackgroundNav: transition failed", error)
                    playbackLogManager.log("PAUSA", "WARN", "origen=timeout-navegacion-generico")
                    playerRepository.pause()
                }
            }
        }
    }

    fun requestSkipNext() {
        requestNavigation()
    }

    fun requestSkipPrevious() {
        requestNavigation(previous = true)
    }

    fun requestAutomaticNext() {
        requestNavigation(automatic = true)
    }

    internal suspend fun navigateAutomatically() {
        if (queueManager.repeatMode.value == com.xploitmusic.app.domain.model.RepeatMode.ONE) {
            playerRepository.seekTo(0)
            playerRepository.play()
            return
        }
        if (!queueManager.hasNext() && !queueManager.isAutoplayEnabled.value) {
            Log.d(TAG, "BackgroundNav: automatic next ignored — autoplay disabled")
            return
        }
        navigateNext()
    }

    internal suspend fun navigateNext() = navigationMutex.withLock {
        val originalIndex = queueManager.currentIndex.value
        val attemptedIds = mutableSetOf<String>()
        var installedIndex = originalIndex

        try {
        repeat(MAX_UNPLAYABLE_SKIPS) {
            val nextSong = nextCandidate() ?: run {
                playbackLogManager.log("NAVEGACION", "ERROR", "Navegación cancelada: ninguna canción de la cola tiene fuente")
                queueManager.skipToIndex(installedIndex)
                return@withLock
            }
            if (!attemptedIds.add(nextSong.id)) {
                playbackLogManager.log("NAVEGACION", "ERROR", "Navegación cancelada: ciclo detectado en la cola")
                queueManager.skipToIndex(installedIndex)
                return@withLock
            }

            var result = resolveSource(nextSong)

            // Si falla por red, reintenta una vez tras 2s sin contar salto
            if (result is ResolveResult.NetworkError) {
                playbackLogManager.log("NAVEGACION", "WARN", "Error de red al resolver '${nextSong.title}'. Reintentando en 2s...")
                delay(2000L)
                result = resolveSourceUncached(nextSong)
            }

            when (result) {
                is ResolveResult.NetworkError -> {
                    playbackLogManager.log("PAUSA", "WARN", "origen=fallo-red-resolver")
                    playerRepository.pause()
                    playbackLogManager.log("NAVEGACION", "ERROR", "Sin red para cargar '${nextSong.title}'")
                    queueManager.skipToIndex(installedIndex)
                    return@withLock
                }
                is ResolveResult.Unplayable -> {
                    Log.w(TAG, "BackgroundNav: no playable source for '${nextSong.title}' (${result.reason})")
                    playbackLogManager.log("NAVEGACION", "WARN", "Sin fuente para '${nextSong.title}'")
                    return@repeat // Cuenta como salto para canciones no reproducibles
                }
                is ResolveResult.Success -> {
                    try {
                        currentCoroutineContext().ensureActive()
                        playerRepository.prepare(result.url, nextSong)
                        installedIndex = queueManager.currentIndex.value
                        playerRepository.play()
                        try {
                            playerRepository.awaitPlaybackStarted(nextSong.id)
                        } catch (timeoutErr: Exception) {
                            val errMsg = timeoutErr.message.orEmpty()
                            val currentId = playerRepository.currentMediaId()
                            if ((timeoutErr is TimeoutCancellationException || errMsg.contains("timeout") || errMsg.contains("timed out") || errMsg.contains("15 s")) &&
                                currentId == nextSong.id) {
                                playbackLogManager.log("PAUSA", "WARN", "origen=reintento-play-tras-timeout")
                                playerRepository.play()
                                return@withLock
                            } else {
                                throw timeoutErr
                            }
                        }
                        Log.d(TAG, "BackgroundNav: playing next '${nextSong.title}'")
                        return@withLock
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        val errMsg = error.message.orEmpty()
                        if (errMsg.contains("La canción cambió durante la carga")) {
                            playbackLogManager.log("PAUSA", "WARN", "origen=omitida-cambio-de-cancion")
                            return@withLock
                        }
                        if (NetworkErrorClassifier.isNetworkError(error, error.message)) {
                            playbackLogManager.log("PAUSA", "WARN", "origen=fallo-red-reproducir")
                            playerRepository.pause()
                            playbackLogManager.log("NAVEGACION", "ERROR", "Sin red para cargar '${nextSong.title}'")
                            queueManager.skipToIndex(installedIndex)
                            return@withLock
                        } else {
                            playbackLogManager.log("PAUSA", "WARN", "origen=fallo-no-red-reproducir")
                        }
                        playbackLogManager.log("NAVEGACION", "WARN", "Fallo al iniciar '${nextSong.title}': ${error::class.java.simpleName} - ${error.message}")
                        playerRepository.pause()
                        Log.w(TAG, "BackgroundNav: failed '${nextSong.title}'", error)
                        playbackLogManager.log("NAVEGACION", "WARN", "Sin fuente para '${nextSong.title}'")
                    }
                }
            }
        }

        playbackLogManager.log("NAVEGACION", "ERROR", "Navegación cancelada: ninguna canción de la cola tiene fuente")
        queueManager.skipToIndex(installedIndex)
        } catch (cancelled: CancellationException) {
            queueManager.skipToIndex(installedIndex)
            throw cancelled
        }
    }

    internal suspend fun navigatePrevious() = navigationMutex.withLock {
        val originalIndex = queueManager.currentIndex.value
        var installedIndex = originalIndex
        val previousSong = queueManager.skipToPrevious()
        if (previousSong == null) {
            playerRepository.seekTo(0)
            return@withLock
        }

        try {
        val result = resolveSource(previousSong)
        if (result !is ResolveResult.Success) {
            queueManager.skipToIndex(originalIndex)
            return@withLock
        }

        try {
            currentCoroutineContext().ensureActive()
            playerRepository.prepare(result.url, previousSong)
            installedIndex = queueManager.currentIndex.value
            playerRepository.play()
            playerRepository.awaitPlaybackStarted(previousSong.id)
            Log.d(TAG, "BackgroundNav: playing previous '${previousSong.title}'")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            playbackLogManager.log("PAUSA", "WARN", "origen=otro")
            playerRepository.pause()
            queueManager.skipToIndex(installedIndex)
            Log.w(TAG, "BackgroundNav: failed previous '${previousSong.title}'", error)
        }
        } catch (cancelled: CancellationException) {
            queueManager.skipToIndex(installedIndex)
            throw cancelled
        }
    }

    private suspend fun nextCandidate(): Song? {
        queueManager.skipToNext()?.let { return it }

        val seed = queueManager.currentTrack.value ?: return null
        val related = try {
            val related = streamRepository.getRelatedSongs(seed.id)
            if (related is Resource.Success) related.data.orEmpty() else emptyList()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w(TAG, "BackgroundNav: related fallback failed", error)
            emptyList()
        }

        val usefulRelated = SongRecommendationSelector.select(
            candidates = related,
            seedArtist = seed.artist,
            excludedIds = queueManager.playedIds + seed.id,
            limit = 10
        )
        val candidates = if (usefulRelated.size >= 5) {
            related
        } else {
            try {
                val search = streamRepository.searchSongs("${seed.artist} canciones populares")
                if (search is Resource.Success) related + search.data.orEmpty() else related
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w(TAG, "BackgroundNav: search fallback failed", error)
                related
            }
        }

        val preferredArtists = try {
            (favoritesRepository.getFavorites().first() + favoritesRepository.getMostPlayed().first())
                .map { it.artist }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w(TAG, "BackgroundNav: listening profile unavailable", error)
            emptyList()
        }
        val selected = SongRecommendationSelector.select(
            candidates = candidates,
            seedArtist = seed.artist,
            preferredArtists = preferredArtists,
            excludedIds = queueManager.playedIds + seed.id,
            limit = 20
        )
        queueManager.appendAutoplayTracks(selected)
        return queueManager.skipToNext()
    }

    private suspend fun resolveSource(song: Song): ResolveResult {
        val prefetched = prefetchedSources.remove(song.id)
        if (prefetched != null) {
            try {
                val result = prefetched.await()
                if (result is ResolveResult.Success) return result
            } catch (cancelled: CancellationException) {
                currentCoroutineContext().ensureActive()
                Log.w(TAG, "BackgroundNav: prefetch cancelled for ${song.id}; resolving on demand")
            } catch (error: Exception) {
                if (NetworkErrorClassifier.isNetworkError(error, error.message)) {
                    return ResolveResult.NetworkError(error, error.message ?: "Error de red")
                }
                Log.w(TAG, "BackgroundNav: prefetch failed for ${song.id}; resolving on demand", error)
            }
        }
        return resolveSourceUncached(song)
    }

    private suspend fun resolveSourceUncached(song: Song): ResolveResult {
        try {
            offlineManager.getOfflineFilePath(song.id)?.let { return ResolveResult.Success(it) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (NetworkErrorClassifier.isNetworkError(error, error.message)) {
                return ResolveResult.NetworkError(error, error.message ?: "Error de red")
            }
            Log.w(TAG, "BackgroundNav: offline lookup failed for ${song.id}", error)
        }

        if (song.id.startsWith("local_")) {
            val url = song.streamUrl?.takeIf { it.isNotBlank() }
            return if (url != null) ResolveResult.Success(url) else ResolveResult.Unplayable("Sin fuente local")
        }

        return try {
            val result = getAudioStreamUseCase(song.id)
            if (result is Resource.Success) {
                val url = result.data?.takeIf { it.isNotBlank() }
                if (url != null) ResolveResult.Success(url) else ResolveResult.Unplayable("Stream URL vacía")
            } else {
                val msg = result.message.orEmpty()
                if (NetworkErrorClassifier.isNetworkError(null, msg)) {
                    ResolveResult.NetworkError(null, msg)
                } else {
                    ResolveResult.Unplayable(msg)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (NetworkErrorClassifier.isNetworkError(error, error.message)) {
                ResolveResult.NetworkError(error, error.message ?: "Error de red")
            } else {
                ResolveResult.Unplayable(error.message ?: "Error al obtener audio")
            }
        }
    }
}
