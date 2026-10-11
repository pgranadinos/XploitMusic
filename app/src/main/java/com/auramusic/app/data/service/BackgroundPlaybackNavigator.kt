package com.auramusic.app.data.service

import android.util.Log
import com.auramusic.app.core.util.Resource
import com.auramusic.app.data.repository.OfflineManager
import com.auramusic.app.domain.model.Song
import com.auramusic.app.domain.repository.PlayerRepository
import com.auramusic.app.domain.repository.FavoritesRepository
import com.auramusic.app.domain.repository.QueueManager
import com.auramusic.app.domain.repository.StreamRepository
import com.auramusic.app.domain.recommendation.SongRecommendationSelector
import com.auramusic.app.domain.usecase.GetAudioStreamUseCase
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
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** Handles notification navigation after the UI lifecycle has ended. */
@Singleton
class BackgroundPlaybackNavigator @Inject constructor(
    private val playerRepository: PlayerRepository,
    private val queueManager: QueueManager,
    private val favoritesRepository: FavoritesRepository,
    private val getAudioStreamUseCase: GetAudioStreamUseCase,
    private val streamRepository: StreamRepository,
    private val offlineManager: OfflineManager
) {
    companion object {
        private const val TAG = "XploitDebug"
        private const val MAX_UNPLAYABLE_SKIPS = 2
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val navigationMutex = Mutex()
    private var navigationJob: Job? = null
    private val prefetchedSources = mutableMapOf<String, Deferred<String?>>()

    /** Resolve the queued song before a car/headphone Next command needs it. */
    fun prefetchNextTrack() {
        scope.launch {
            val next = queueManager.peekNext()
            if (next == null) {
                prefetchedSources.values.forEach { it.cancel() }
                prefetchedSources.clear()
                return@launch
            }
            val existingPrefetch = prefetchedSources[next.id]
            if (existingPrefetch != null) {
                // A source can resolve before the MediaController finishes connecting.
                // Retry the enqueue on the next player event instead of treating the
                // cached URL as proof that Media3 already has the item.
                if (existingPrefetch.isCompleted) {
                    try {
                        val source = existingPrefetch.await()?.takeIf { it.isNotBlank() }
                        if (source == null) {
                            forgetPrefetchedSource(next.id, existingPrefetch)
                        } else {
                            enqueuePrefetchedNextTrack(next, source)
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
            val prefetchedSource = scope.async(Dispatchers.IO) {
                resolveSourceUncached(next)
            }
            prefetchedSources[next.id] = prefetchedSource
            try {
                val source = prefetchedSource.await()?.takeIf { it.isNotBlank() }
                if (source != null) {
                    enqueuePrefetchedNextTrack(next, source)
                } else {
                    forgetPrefetchedSource(next.id, prefetchedSource)
                    Log.w(TAG, "BackgroundNav: no source to prequeue for '${next.title}'")
                }
            } catch (cancelled: CancellationException) {
                currentCoroutineContext().ensureActive()
                forgetPrefetchedSource(next.id, prefetchedSource)
                Log.d(TAG, "BackgroundNav: prefetch superseded for '${next.title}'")
            } catch (error: Exception) {
                forgetPrefetchedSource(next.id, prefetchedSource)
                Log.w(TAG, "BackgroundNav: prefetch failed for '${next.title}'", error)
            }
        }
    }

    private fun forgetPrefetchedSource(songId: String, expected: Deferred<String?>) {
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
        scope.launch { navigationJob?.cancel() }
    }

    private fun requestNavigation(automatic: Boolean = false, previous: Boolean = false) {
        scope.launch {
            if (automatic && navigationJob?.isActive == true) return@launch
            navigationJob?.cancel()
            navigationJob = scope.launch {
                try {
                    // A complete stream-provider fallback pass can take ~24 s;
                    // leave room for Media3 to buffer the selected source too.
                    withTimeout(40_000L) {
                        when {
                            previous -> navigatePrevious()
                            automatic -> navigateAutomatically()
                            else -> navigateNext()
                        }
                    }
                } catch (error: TimeoutCancellationException) {
                    Log.w(TAG, "BackgroundNav: transition timed out", error)
                    playerRepository.pause()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Log.w(TAG, "BackgroundNav: transition failed", error)
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
        if (queueManager.repeatMode.value == com.auramusic.app.domain.model.RepeatMode.ONE) {
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
                queueManager.skipToIndex(installedIndex)
                return@withLock
            }
            if (!attemptedIds.add(nextSong.id)) {
                queueManager.skipToIndex(installedIndex)
                return@withLock
            }

            val source = resolveSource(nextSong)
            if (source == null) {
                Log.w(TAG, "BackgroundNav: no playable source for '${nextSong.title}'")
                return@repeat
            }

            try {
                currentCoroutineContext().ensureActive()
                playerRepository.prepare(source, nextSong)
                installedIndex = queueManager.currentIndex.value
                playerRepository.play()
                playerRepository.awaitPlaybackStarted(nextSong.id)
                Log.d(TAG, "BackgroundNav: playing next '${nextSong.title}'")
                return@withLock
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                playerRepository.pause()
                Log.w(TAG, "BackgroundNav: failed '${nextSong.title}'", error)
            }
        }

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
        val source = resolveSource(previousSong)
        if (source == null) {
            queueManager.skipToIndex(originalIndex)
            return@withLock
        }

        try {
            currentCoroutineContext().ensureActive()
            playerRepository.prepare(source, previousSong)
            installedIndex = queueManager.currentIndex.value
            playerRepository.play()
            playerRepository.awaitPlaybackStarted(previousSong.id)
            Log.d(TAG, "BackgroundNav: playing previous '${previousSong.title}'")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
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

    private suspend fun resolveSource(song: Song): String? {
        val prefetched = prefetchedSources.remove(song.id)
        if (prefetched != null) {
            try {
                prefetched.await()?.takeIf { it.isNotBlank() }?.let { return it }
            } catch (cancelled: CancellationException) {
                currentCoroutineContext().ensureActive()
                Log.w(TAG, "BackgroundNav: prefetch cancelled for ${song.id}; resolving on demand")
            } catch (error: Exception) {
                Log.w(TAG, "BackgroundNav: prefetch failed for ${song.id}; resolving on demand", error)
            }
        }
        return resolveSourceUncached(song)
    }

    private suspend fun resolveSourceUncached(song: Song): String? {
        try {
            offlineManager.getOfflineFilePath(song.id)?.let { return it }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w(TAG, "BackgroundNav: offline lookup failed for ${song.id}", error)
        }

        if (song.id.startsWith("local_")) {
            return song.streamUrl?.takeIf { it.isNotBlank() }
        }

        return try {
            val result = getAudioStreamUseCase(song.id)
            if (result is Resource.Success) result.data?.takeIf { it.isNotBlank() } else null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w(TAG, "BackgroundNav: stream lookup failed for ${song.id}", error)
            null
        }
    }
}
