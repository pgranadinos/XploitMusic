package com.xploitmusic.app.data.repository

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.C
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.xploitmusic.app.data.service.XploitMediaService
import com.xploitmusic.app.data.service.OfflineAwareMediaSourceFactory
import com.xploitmusic.app.core.media.toMediaMetadata
import com.xploitmusic.app.core.media.toSongOrNull
import com.xploitmusic.app.domain.model.Song
import com.xploitmusic.app.domain.repository.PlayerRepository
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class PlayerRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val offlineMediaFactory: OfflineAwareMediaSourceFactory,
    private val playbackLogManager: com.xploitmusic.app.core.util.PlaybackLogManager
) : PlayerRepository {

    companion object {
        private const val TAG = "XploitDebug"
        private const val CONTROLLER_CONNECTION_TIMEOUT_MS = 10_000L
    }

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val connection = MediaControllerConnection {
        val sessionToken = SessionToken(context, ComponentName(context, XploitMediaService::class.java))
        MediaController.Builder(context, sessionToken).buildAsync()
    }

    private suspend fun getController(): MediaController? = connection.get()
    private suspend fun requireController(): MediaController = checkNotNull(getController()) {
        "El servicio de reproducción no está conectado"
    }

    private val connections = flow {
        while (true) {
            val controller = getController()
            emit(controller)
            while (controller?.isConnected == true) delay(500)
            delay(500)
        }
    }.flowOn(Dispatchers.Main.immediate)
        .shareIn(repositoryScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun <T> observeController(block: suspend ProducerScope<T>.() -> Unit): Flow<T> =
        connections.flatMapLatest { callbackFlow(block) }.flowOn(Dispatchers.Main.immediate)

    /**
     * Builds a MediaItem with full metadata so the system notification
     * displays the song title, artist, and artwork. Resolves offline URI if downloaded.
     */
    private suspend fun buildMediaItem(url: String, song: Song): MediaItem {
        val resolvedUri = offlineMediaFactory.resolveUri(song.id, url)
        
        return MediaItem.Builder()
            .setUri(resolvedUri.toString())
            .setMediaId(song.id)
            .setMediaMetadata(song.toMediaMetadata())
            .build()
    }

    /**
     * Emits the currently loaded Song reconstructed from ExoPlayer's MediaItem metadata.
     * Uses callbackFlow + onMediaItemTransition so it's fully reactive and non-blocking.
     * Emits null if no media item is loaded or metadata is empty.
     */
    override val currentSong: Flow<Song?> = observeController {
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                trySend(mediaItem?.toSongOrNull())
            }
        }

        val controller = getController()
        controller?.addListener(listener)
        trySend(controller?.currentMediaItem?.toSongOrNull())

        awaitClose { controller?.removeListener(listener) }
    }

    override val isPlaying: Flow<Boolean> = observeController {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                trySend(isPlaying)
            }
        }

        val controller = getController()
        controller?.addListener(listener)
        trySend(controller?.isPlaying == true)

        awaitClose {
            controller?.removeListener(listener)
        }
    }

    override val currentPosition: Flow<Long> = observeController {
        var isRunning = true
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                // Trigger polling
            }
        }

        val controller = getController()
        controller?.addListener(listener)

        val job = launch {
            while (isRunning) {
                val currentPos = controller?.currentPosition ?: 0L
                // BUG 2 FIX: Always emit position, not just when playing.
                // This keeps the slider and time display accurate during pause.
                trySend(currentPos)
                delay(500)
            }
        }

        awaitClose {
            isRunning = false
            job.cancel()
            controller?.removeListener(listener)
        }
    }

    override val duration: Flow<Long> = observeController {
        val controller = getController()
        fun emitDuration() {
            val reported = controller?.duration ?: 0L
            val metadataDuration = controller?.currentMediaItem
                ?.toSongOrNull()
                ?.durationSeconds
                ?.coerceIn(0L, Long.MAX_VALUE / 1_000L)
                ?.times(1_000L)
                ?: 0L
            trySend(
                if (reported == C.TIME_UNSET || reported <= 0L) metadataDuration else reported
            )
        }
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                emitDuration()
            }

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                emitDuration()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                emitDuration()
            }
        }
        controller?.addListener(listener)
        emitDuration()

        awaitClose { controller?.removeListener(listener) }
    }

    /** Emits Unit every time a track reaches STATE_ENDED */
    override val trackEnded: Flow<Unit> = observeController {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    Log.d(TAG, "PlayerRepo: Track ended, emitting event")
                    trySend(Unit)
                }
            }
        }
        val controller = getController()
        controller?.addListener(listener)

        awaitClose { controller?.removeListener(listener) }
    }

    /** Emits the mediaId of the new track when ExoPlayer transitions between items */
    override val mediaItemTransition: Flow<String?> = observeController {
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val mediaId = mediaItem?.mediaId
                Log.d(TAG, "PlayerRepo: onMediaItemTransition — mediaId='$mediaId', reason=$reason")
                trySend(mediaId)
            }
        }
        val controller = getController()
        controller?.addListener(listener)

        awaitClose { controller?.removeListener(listener) }
    }

    override suspend fun play() {
        withContext(Dispatchers.Main) { requireController().play() }
    }

    override suspend fun pause() {
        withContext(Dispatchers.Main) { getController()?.pause() }
    }

    override suspend fun seekTo(position: Long) {
        val controller = getController() ?: return
        withContext(Dispatchers.Main) {
            if (!controller.isCurrentMediaItemSeekable) {
                Log.w(TAG, "PlayerRepo: seekTo ignored — current item is not seekable")
                return@withContext
            }
            val knownDuration = controller.duration
            val target = if (knownDuration != C.TIME_UNSET && knownDuration > 0L) {
                position.coerceIn(0L, knownDuration)
            } else {
                position.coerceAtLeast(0L)
            }
            controller.seekTo(target)
            Log.d(TAG, "PlayerRepo: seekTo($target)")
        }
    }

    override suspend fun prepare(url: String, song: Song) {
        val mediaItem = buildMediaItem(url, song)
        withContext(Dispatchers.Main) {
            val controller = requireController()
            controller.setMediaItem(mediaItem)
            controller.prepare()
        }
        Log.d(TAG, "PlayerRepo: prepare() with metadata — title='${song.title}', artist='${song.artist}'")
    }

    override suspend fun addNextMediaItem(url: String, song: Song) {
        val controller = requireController()
        val mediaItem = buildMediaItem(url, song)
        withContext(Dispatchers.Main) {
            // Add after the current item so ExoPlayer pre-buffers it
            val insertIndex = (controller.currentMediaItemIndex + 1)
                .coerceIn(0, controller.mediaItemCount)
            controller.addMediaItem(insertIndex, mediaItem)
        }
        Log.d(TAG, "PlayerRepo: addNextMediaItem() queued '${song.title}'")
    }

    override suspend fun awaitPlaybackStarted(songId: String) = withContext(Dispatchers.Main) {
        withTimeout(15_000L) {
            val controller = requireController()
            while (true) {
                check(controller.isConnected) { "Se desconectó el servicio de reproducción" }
                check(controller.currentMediaItem?.mediaId == songId) { "La canción cambió durante la carga" }
                controller.playerError?.let { throw it }
                if (controller.isPlaying) break
                delay(100)
            }
        }
    }

    override suspend fun clearUpcomingMediaItems() {
        val controller = getController() ?: return
        withContext(Dispatchers.Main) {
            val currentIndex = controller.currentMediaItemIndex
            val firstUpcoming = currentIndex + 1
            if (currentIndex >= 0 && firstUpcoming < controller.mediaItemCount) {
                controller.removeMediaItems(firstUpcoming, controller.mediaItemCount)
                Log.d(TAG, "PlayerRepo: cleared prefetched media items")
            }
        }
    }

    override suspend fun clearQueue() {
        val controller = getController() ?: return
        withContext(Dispatchers.Main) {
            val count = controller.mediaItemCount
            if (count > 0) {
                // BUG 6 FIX: Clear ALL media items (including current) to prevent spurious
                // transitions during the gap between clearQueue() and the next prepare().
                // Previous code left item 0 intact, creating a window where ExoPlayer could
                // transition to that stale item.
                controller.clearMediaItems()
                Log.d(TAG, "PlayerRepo: clearQueue() cleared all $count items")
            }
        }
    }

    override suspend fun hasNextMediaItem(): Boolean {
        val controller = getController() ?: return false
        // FIX: MediaController methods MUST be called from the main thread (Media3 requirement)
        return withContext(Dispatchers.Main) {
            hasQueuedNext(controller)
        }
    }

    override suspend fun skipToNext() {
        val controller = getController() ?: return
        withContext(Dispatchers.Main) {
            if (hasQueuedNext(controller)) {
                controller.seekToNextMediaItem()
                controller.play()
                Log.d(TAG, "PlayerRepo: skipToNext() — moved to next queued item")
            }
        }
    }

    private fun hasQueuedNext(controller: MediaController): Boolean {
        val currentIndex = controller.currentMediaItemIndex
        return currentIndex >= 0 && currentIndex < controller.mediaItemCount - 1
    }

    // ── Shuffle & Repeat ──

    override fun setRepeatMode(repeatMode: Int) {
        repositoryScope.launch {
            try {
                getController()?.repeatMode = repeatMode
                Log.d(TAG, "PlayerRepo: setRepeatMode($repeatMode)")
            } catch (e: Exception) {
                Log.w(TAG, "PlayerRepo: setRepeatMode failed: ${e.message}")
            }
        }
    }

    override fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) {
        repositoryScope.launch {
            try {
                getController()?.shuffleModeEnabled = shuffleModeEnabled
                Log.d(TAG, "PlayerRepo: setShuffleModeEnabled($shuffleModeEnabled)")
            } catch (e: Exception) {
                Log.w(TAG, "PlayerRepo: setShuffleModeEnabled failed: ${e.message}")
            }
        }
    }

    // ── Error Handling ──

    override val playerError: Flow<com.xploitmusic.app.domain.model.PlayerError> = observeController {
        val controller = getController()
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                val msg = when (error.errorCode) {
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                        "Error de red: verifica tu conexión a internet"
                    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
                        "Error del servidor de audio (HTTP ${error.message})"
                    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ->
                        "Stream de audio no encontrado"
                    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                    PlaybackException.ERROR_CODE_DECODING_FAILED ->
                        "Error al decodificar el audio"
                    else ->
                        "Error de reproducción: ${error.localizedMessage}"
                }
                val playerErrorObj = com.xploitmusic.app.domain.model.PlayerError(
                    code = error.errorCode,
                    codeName = error.errorCodeName,
                    message = msg
                )
                Log.e(TAG, "PlayerRepo: onPlayerError — code=${error.errorCode} (${error.errorCodeName}), msg=$msg", error)
                playbackLogManager.log("PLAYER_REPO", "ERROR", "Error recibido en repositorio: code=${error.errorCode} (${error.errorCodeName})")
                trySend(playerErrorObj)

                // Observe only. Navigation owns retries; replaying here races with skips.
            }
        }
        controller?.addListener(listener)

        awaitClose { controller?.removeListener(listener) }
    }

    override fun release() {
        repositoryScope.launch {
            connection.release()
            repositoryScope.cancel()
        }
    }
}
