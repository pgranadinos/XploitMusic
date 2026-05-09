package com.auramusic.app.data.repository

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.auramusic.app.data.service.AuraMediaService
import com.auramusic.app.data.service.OfflineAwareMediaSourceFactory
import com.auramusic.app.domain.model.Song
import com.auramusic.app.domain.repository.PlayerRepository
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayerRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val offlineMediaFactory: OfflineAwareMediaSourceFactory
) : PlayerRepository {

    companion object {
        private const val TAG = "XploitDebug"
    }

    private var mediaControllerFuture: ListenableFuture<MediaController>? = null
    private val mediaController: MediaController?
        get() = if (mediaControllerFuture?.isDone == true) mediaControllerFuture?.get() else null

    init {
        val sessionToken = SessionToken(context, ComponentName(context, AuraMediaService::class.java))
        mediaControllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        mediaControllerFuture?.addListener({
            Log.d(TAG, "PlayerRepo: MediaController connected")
        }, MoreExecutors.directExecutor())
    }

    private suspend fun getController(): MediaController? {
        if (mediaControllerFuture?.isDone == true) {
            return mediaControllerFuture?.get()
        }
        var attempt = 0
        while (mediaControllerFuture?.isDone != true && attempt < 10) {
            delay(100)
            attempt++
        }
        return try {
            mediaControllerFuture?.get()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Builds a MediaItem with full metadata so the system notification
     * displays the song title, artist, and artwork. Resolves offline URI if downloaded.
     */
    private suspend fun buildMediaItem(url: String, song: Song): MediaItem {
        val resolvedUri = offlineMediaFactory.resolveUri(song.id, url)
        
        val metadata = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.artist)
            .setArtworkUri(Uri.parse(song.thumbnailUrl))
            .build()

        return MediaItem.Builder()
            .setUri(resolvedUri.toString())
            .setMediaId(song.id)
            .setMediaMetadata(metadata)
            .build()
    }

    override val isPlaying: Flow<Boolean> = callbackFlow {
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

    override val currentPosition: Flow<Long> = callbackFlow {
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
                if (controller?.isPlaying == true) {
                    trySend(currentPos)
                }
                delay(500)
            }
        }

        awaitClose {
            isRunning = false
            job.cancel()
            controller?.removeListener(listener)
        }
    }

    override val duration: Flow<Long> = callbackFlow {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    trySend(mediaController?.duration ?: 0L)
                }
            }
        }
        val controller = getController()
        controller?.addListener(listener)
        trySend(controller?.duration ?: 0L)

        awaitClose { controller?.removeListener(listener) }
    }

    /** Emits Unit every time a track reaches STATE_ENDED */
    override val trackEnded: Flow<Unit> = callbackFlow {
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
    override val mediaItemTransition: Flow<String?> = callbackFlow {
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
        withContext(Dispatchers.Main) { getController()?.play() }
    }

    override suspend fun pause() {
        withContext(Dispatchers.Main) { getController()?.pause() }
    }

    override suspend fun seekTo(position: Long) {
        withContext(Dispatchers.Main) { getController()?.seekTo(position) }
    }

    override suspend fun prepare(url: String, song: Song) {
        val controller = getController() ?: return
        val mediaItem = buildMediaItem(url, song)
        withContext(Dispatchers.Main) {
            controller.setMediaItem(mediaItem)
            controller.prepare()
        }
        Log.d(TAG, "PlayerRepo: prepare() with metadata — title='${song.title}', artist='${song.artist}'")
    }

    override suspend fun addNextMediaItem(url: String, song: Song) {
        val controller = getController() ?: return
        val mediaItem = buildMediaItem(url, song)
        withContext(Dispatchers.Main) {
            // Add after the current item so ExoPlayer pre-buffers it
            controller.addMediaItem(mediaItem)
        }
        Log.d(TAG, "PlayerRepo: addNextMediaItem() queued '${song.title}'")
    }

    override suspend fun clearQueue() {
        val controller = getController() ?: return
        withContext(Dispatchers.Main) {
            val count = controller.mediaItemCount
            if (count > 1) {
                controller.removeMediaItems(1, count)
                Log.d(TAG, "PlayerRepo: clearQueue() removed ${count - 1} queued items")
            }
        }
    }

    override suspend fun hasNextMediaItem(): Boolean {
        val controller = getController() ?: return false
        // FIX: MediaController methods MUST be called from the main thread (Media3 requirement)
        return withContext(Dispatchers.Main) {
            controller.hasNextMediaItem()
        }
    }

    override suspend fun skipToNext() {
        val controller = getController() ?: return
        withContext(Dispatchers.Main) {
            if (controller.hasNextMediaItem()) {
                controller.seekToNextMediaItem()
                controller.play()
                Log.d(TAG, "PlayerRepo: skipToNext() — moved to next queued item")
            }
        }
    }

    // ── Shuffle & Repeat ──

    override fun setRepeatMode(repeatMode: Int) {
        // Media3 requires API calls from the main thread
        kotlinx.coroutines.MainScope().launch {
            withContext(Dispatchers.Main) {
                mediaController?.repeatMode = repeatMode
                Log.d(TAG, "PlayerRepo: setRepeatMode($repeatMode)")
            }
        }
    }

    override fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) {
        // Media3 requires API calls from the main thread
        kotlinx.coroutines.MainScope().launch {
            withContext(Dispatchers.Main) {
                mediaController?.shuffleModeEnabled = shuffleModeEnabled
                Log.d(TAG, "PlayerRepo: setShuffleModeEnabled($shuffleModeEnabled)")
            }
        }
    }

    // ── Error Handling ──

    override val playerError: Flow<String?> = callbackFlow {
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
                Log.e(TAG, "PlayerRepo: onPlayerError — code=${error.errorCode}, msg=$msg", error)
                trySend(msg)

                // Reset player error state so subsequent playback attempts work
                try {
                    mediaController?.prepare()
                } catch (e: Exception) {
                    Log.w(TAG, "PlayerRepo: prepare() after error failed: ${e.message}")
                }
            }
        }
        val controller = getController()
        controller?.addListener(listener)

        awaitClose { controller?.removeListener(listener) }
    }

    override fun release() {
        mediaControllerFuture?.let { MediaController.releaseFuture(it) }
    }
}
