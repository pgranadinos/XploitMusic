package com.auramusic.app.data.service

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class AuraMediaService : MediaLibraryService() {

    @Inject
    lateinit var player: ExoPlayer

    @Inject
    lateinit var skipHandler: SkipHandler

    private var mediaSession: MediaLibrarySession? = null

    override fun onCreate() {
        super.onCreate()

        // Deep-link PendingIntent: opens MainActivity → PlayerScreen via Compose NavHost
        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("app://xploitmusic/player")).apply {
            setPackage(packageName)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // ForwardingPlayer: always shows next/prev buttons in notification.
        // Instead of the buggy seekTo(duration) hack, it routes skip events
        // through SkipHandler → PlayerViewModel.forceSkipToNext()/skipPrevious()
        val forwardingPlayer = object : ForwardingPlayer(player) {
            override fun hasNextMediaItem(): Boolean = true
            override fun hasPreviousMediaItem(): Boolean = true

            override fun getAvailableCommands(): Player.Commands {
                return Player.Commands.Builder()
                    .addAllCommands()
                    .build()
            }

            override fun isCommandAvailable(command: Int): Boolean = true

            override fun seekToNext() {
                // If ExoPlayer actually has a next queued item, use it directly
                if (wrappedPlayer.hasNextMediaItem()) {
                    wrappedPlayer.seekToNext()
                } else {
                    // Route through SkipHandler → PlayerViewModel.forceSkipToNext()
                    skipHandler.requestSkipNext()
                }
            }

            override fun seekToNextMediaItem() {
                if (wrappedPlayer.mediaItemCount > 1 &&
                    wrappedPlayer.currentMediaItemIndex < wrappedPlayer.mediaItemCount - 1
                ) {
                    wrappedPlayer.seekToNextMediaItem()
                } else {
                    skipHandler.requestSkipNext()
                }
            }

            override fun seekToPrevious() {
                if (wrappedPlayer.currentPosition > 3000) {
                    wrappedPlayer.seekTo(0)
                } else if (wrappedPlayer.hasPreviousMediaItem()) {
                    wrappedPlayer.seekToPrevious()
                } else {
                    skipHandler.requestSkipPrevious()
                }
            }

            override fun seekToPreviousMediaItem() {
                if (wrappedPlayer.hasPreviousMediaItem()) {
                    wrappedPlayer.seekToPreviousMediaItem()
                } else {
                    skipHandler.requestSkipPrevious()
                }
            }
        }

        mediaSession = MediaLibrarySession.Builder(
            this,
            forwardingPlayer,
            MediaLibrarySessionCallback()
        )
            .setSessionActivity(pendingIntent)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaSession
    }

    override fun onDestroy() {
        mediaSession?.run {
            // player.release() // Fix SRE Performance: El player es Singleton (Hilt), no debe matarse.
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    private inner class MediaLibrarySessionCallback : MediaLibrarySession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val availablePlayerCommands = Player.Commands.Builder()
                .addAllCommands()
                .build()

            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailablePlayerCommands(availablePlayerCommands)
                .setAvailableSessionCommands(MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS)
                .build()
        }
    }
}
