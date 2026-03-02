package com.auramusic.app.data.repository

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.auramusic.app.data.service.AuraMediaService
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
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayerRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
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
     * displays the song title, artist, and artwork.
     */
    private fun buildMediaItem(url: String, song: Song): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.artist)
            .setArtworkUri(Uri.parse(song.thumbnailUrl))
            .build()

        return MediaItem.Builder()
            .setUri(url)
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
        getController()?.play()
    }

    override suspend fun pause() {
        getController()?.pause()
    }

    override suspend fun seekTo(position: Long) {
        getController()?.seekTo(position)
    }

    override suspend fun prepare(url: String, song: Song) {
        val controller = getController() ?: return
        val mediaItem = buildMediaItem(url, song)
        controller.setMediaItem(mediaItem)
        controller.prepare()
        controller.play()
        Log.d(TAG, "PlayerRepo: prepare() with metadata — title='${song.title}', artist='${song.artist}'")
    }

    override suspend fun addNextMediaItem(url: String, song: Song) {
        val controller = getController() ?: return
        val mediaItem = buildMediaItem(url, song)
        // Add after the current item so ExoPlayer pre-buffers it
        controller.addMediaItem(mediaItem)
        Log.d(TAG, "PlayerRepo: addNextMediaItem() queued '${song.title}'")
    }

    override suspend fun clearQueue() {
        val controller = getController() ?: return
        // Remove all items except the currently playing one
        val count = controller.mediaItemCount
        if (count > 1) {
            controller.removeMediaItems(1, count)
            Log.d(TAG, "PlayerRepo: clearQueue() removed ${count - 1} queued items")
        }
    }

    override suspend fun hasNextMediaItem(): Boolean {
        val controller = getController() ?: return false
        return controller.hasNextMediaItem()
    }

    override suspend fun skipToNext() {
        val controller = getController() ?: return
        if (controller.hasNextMediaItem()) {
            controller.seekToNextMediaItem()
            controller.play()
            Log.d(TAG, "PlayerRepo: skipToNext() — moved to next queued item")
        }
    }

    override fun release() {
        mediaControllerFuture?.let { MediaController.releaseFuture(it) }
    }
}
