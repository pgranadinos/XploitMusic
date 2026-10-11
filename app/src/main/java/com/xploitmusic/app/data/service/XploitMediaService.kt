package com.xploitmusic.app.data.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
import androidx.media3.session.SessionResult
import com.xploitmusic.app.R
import com.xploitmusic.app.core.media.toSongOrNull
import com.xploitmusic.app.core.media.toMediaMetadata
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media3.session.DefaultMediaNotificationProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import com.xploitmusic.app.domain.repository.FavoritesRepository
import com.xploitmusic.app.domain.repository.QueueManager
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

internal const val ACTION_TOGGLE_FAVORITE = "com.xploitmusic.app.action.TOGGLE_FAVORITE"
internal val TOGGLE_FAVORITE_COMMAND = SessionCommand(ACTION_TOGGLE_FAVORITE, Bundle.EMPTY)

internal fun notificationSessionCommands(baseCommands: SessionCommands): SessionCommands =
    baseCommands.buildUpon().add(TOGGLE_FAVORITE_COMMAND).build()

internal fun favoriteCommandButton(isFavorite: Boolean): CommandButton = CommandButton.Builder()
    .setSessionCommand(TOGGLE_FAVORITE_COMMAND)
    .setIconResId(if (isFavorite) R.drawable.ic_favorite else R.drawable.ic_favorite_border)
    .setDisplayName(if (isFavorite) "Quitar de favoritos" else "Añadir a favoritos")
    .build()

private val REQUIRED_CONTROLLER_COMMANDS = setOf(
    Player.COMMAND_PLAY_PAUSE,
    Player.COMMAND_PREPARE,
    Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
    Player.COMMAND_SEEK_TO_NEXT,
    Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
    Player.COMMAND_SEEK_TO_PREVIOUS,
    Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
)

internal fun notificationCommands(baseCommands: Player.Commands): Player.Commands {
    val commands = Player.Commands.Builder().addAll(baseCommands)
    REQUIRED_CONTROLLER_COMMANDS.forEach(commands::add)
    return commands.build()
}

internal fun mediaButtonRequestsPlay(intent: Intent, playWhenReady: Boolean): Boolean {
    @Suppress("DEPRECATION")
    val event = if (Build.VERSION.SDK_INT >= 33) {
        intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
    } else {
        intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT) as? KeyEvent
    } ?: return false

    if (event.action != KeyEvent.ACTION_DOWN) return false
    return when (event.keyCode) {
        KeyEvent.KEYCODE_MEDIA_PLAY -> true
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        KeyEvent.KEYCODE_HEADSETHOOK -> !playWhenReady
        else -> false
    }
}

/** ExoPlayer requires a URI even while the saved song's metadata is being restored. */
internal fun resumptionPlaceholderItem(saved: SavedPlaybackSession): MediaItem {
    val song = saved.songs[saved.index]
    val sourceUri = song.streamUrl?.takeIf { it.isNotBlank() }
        ?: "https://pending.invalid/resumption"
    return MediaItem.Builder()
        .setMediaId(song.id)
        .setUri(sourceUri)
        .setMediaMetadata(song.toMediaMetadata())
        .build()
}

internal class PlaybackEndListener(
    private val onPlaybackEnded: () -> Unit
) : Player.Listener {
    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_ENDED) onPlaybackEnded()
    }
}

internal class NotificationForwardingPlayer(
    player: Player,
    private val skipHandler: SkipHandler,
    private val onPendingResumeRequest: (Boolean) -> Boolean,
    private val onPendingResumePauseRequest: () -> Boolean,
    private val cancelPendingResumeForNavigation: () -> Unit
) : ForwardingPlayer(player) {

    override fun getAvailableCommands(): Player.Commands {
        return notificationCommands(wrappedPlayer.availableCommands)
    }

    override fun play() {
        if (onPendingResumeRequest(true)) return
        if (wrappedPlayer.playbackState == Player.STATE_IDLE && wrappedPlayer.mediaItemCount > 0) {
            wrappedPlayer.prepare()
        }
        wrappedPlayer.play()
    }

    override fun prepare() {
        if (onPendingResumeRequest(wrappedPlayer.playWhenReady)) return
        wrappedPlayer.prepare()
    }

    override fun pause() {
        if (onPendingResumePauseRequest()) return
        wrappedPlayer.pause()
    }

    override fun isCommandAvailable(command: Int): Boolean {
        return command in REQUIRED_CONTROLLER_COMMANDS || wrappedPlayer.isCommandAvailable(command)
    }

    override fun seekToNext() {
        if (wrappedPlayer.hasNextMediaItem()) {
            wrappedPlayer.seekToNext()
        } else {
            cancelPendingResumeForNavigation()
            skipHandler.requestSkipNext()
        }
    }

    override fun seekToNextMediaItem() {
        if (wrappedPlayer.hasNextMediaItem()) {
            wrappedPlayer.seekToNextMediaItem()
        } else {
            cancelPendingResumeForNavigation()
            skipHandler.requestSkipNext()
        }
    }

    override fun seekToPrevious() {
        seekToPreviousMediaItem()
    }

    override fun seekToPreviousMediaItem() {
        if (wrappedPlayer.hasPreviousMediaItem()) {
            wrappedPlayer.seekToPreviousMediaItem()
        } else {
            cancelPendingResumeForNavigation()
            skipHandler.requestSkipPrevious()
        }
    }
}

/** Keeps the logical queue aligned with the item Media3 actually selected. */
internal class QueueSyncListener(
    private val queueManager: QueueManager,
    private val onQueueTransition: () -> Unit = {}
) : Player.Listener {
    override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
        val mediaId = mediaItem?.mediaId?.takeIf { it.isNotBlank() } ?: return
        val index = queueManager.queue.value.indexOfFirst { it.id == mediaId }
        if (index >= 0 && index != queueManager.currentIndex.value) {
            queueManager.skipToIndex(index)
            onQueueTransition()
        }
    }
}

@AndroidEntryPoint
class XploitMediaService : MediaLibraryService() {

    @Inject
    lateinit var player: ExoPlayer

    @Inject
    lateinit var skipHandler: SkipHandler

    @Inject
    lateinit var queueManager: QueueManager

    @Inject
    lateinit var favoritesRepository: FavoritesRepository

    @Inject lateinit var sessionStore: PlaybackSessionStore
    @Inject lateinit var resumptionResolver: PlaybackResumptionResolver
    @Inject lateinit var backgroundPlaybackNavigator: BackgroundPlaybackNavigator
    private var restoreJob: Job? = null
    private var resumeFuture: SettableFuture<MediaSession.MediaItemsWithStartPosition>? = null
    private var resumeJob: Job? = null
    private var resumeWatchdog: Job? = null
    private var pendingResumeJob: Job? = null
    private var pendingResumption: SavedPlaybackSession? = null
    private var restoreCompleted = false
    private var pendingPlayRequested = false
    private var pendingResumeShouldPlay = false
    private var pausedSinceElapsedMs: Long? = null
    private var showingResumeNotification = false

    private val sessionSaveListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            saveSession()
            maybePrefetchNextTrack()
            if (showingResumeNotification &&
                (player.playbackState == Player.STATE_BUFFERING || player.playbackState == Player.STATE_READY)) {
                showingResumeNotification = false
                resumeWatchdog?.cancel()
                // The media-button event already promoted this service. Refresh its
                // notification in place instead of requesting a second FGS promotion
                // after Android's temporary media-button start allowance has expired.
                mediaSession?.let { onUpdateNotification(it, startInForegroundRequired = false) }
            }
        }
    }

    private fun saveSession() {
        val song = player.currentMediaItem?.toSongOrNull() ?: return
        val songs = queueManager.queue.value
        val index = queueManager.currentIndex.value
        // A pending skip may have changed the queue index before the player.
        if (songs.getOrNull(index)?.id != song.id) return
        val snapshot = SavedPlaybackSession(songs, index, player.currentPosition)
        sessionStore.save(snapshot)
        if (!player.playWhenReady && player.playbackState == Player.STATE_READY) {
            val pausedSince = pausedSinceElapsedMs ?: android.os.SystemClock.elapsedRealtime().also {
                pausedSinceElapsedMs = it
            }
            if (android.os.SystemClock.elapsedRealtime() - pausedSince >= 10 * 60_000L) {
                pendingResumption = snapshot
            }
        } else {
            pausedSinceElapsedMs = null
        }
    }

    private suspend fun restoreSession() {
        try {
            if (player.mediaItemCount > 0 || queueManager.queue.value.isNotEmpty()) return
            val saved = resumptionResolver.loadSavedSession() ?: return
            // Restore metadata and queue immediately; resolve the stream only when Play is requested.
            if (player.mediaItemCount > 0 || queueManager.queue.value.isNotEmpty()) return
            queueManager.setQueue(saved.songs, saved.index)
            pendingResumption = saved
            player.playWhenReady = false
            player.setMediaItem(resumptionPlaceholderItem(saved), saved.positionMs)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            android.util.Log.w("XploitDebug", "Session restoration failed", error)
        } finally {
            restoreCompleted = true
            if (pendingPlayRequested && pendingResumption != null) {
                pendingPlayRequested = false
                handlePendingResumeRequest(playWhenReady = true)
            }
        }
    }

    private var mediaSession: MediaLibrarySession? = null
    private lateinit var queueSyncListener: QueueSyncListener
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var favoriteStateJob: Job? = null
    private var currentFavorite = false

    @Inject
    lateinit var playbackLogManager: com.xploitmusic.app.core.util.PlaybackLogManager

    private lateinit var audioManager: android.media.AudioManager
    private val audioDeviceCallback = object : android.media.AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out android.media.AudioDeviceInfo>) {
            addedDevices.forEach { device ->
                val typeName = deviceTypeToString(device.type)
                playbackLogManager.log("BLUETOOTH", "INFO", "Audio output device connected: $typeName (${device.productName})")
            }
        }
        override fun onAudioDevicesRemoved(removedDevices: Array<out android.media.AudioDeviceInfo>) {
            removedDevices.forEach { device ->
                val typeName = deviceTypeToString(device.type)
                playbackLogManager.log("BLUETOOTH", "WARN", "Audio output device disconnected: $typeName (${device.productName})")
            }
        }
    }

    private fun deviceTypeToString(type: Int): String {
        return when (type) {
            android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth A2DP"
            android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth SCO"
            android.media.AudioDeviceInfo.TYPE_BLE_HEADSET -> "BLE Headset"
            android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired Headset"
            android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired Headphones"
            android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Builtin Speaker"
            android.media.AudioDeviceInfo.TYPE_BLE_SPEAKER -> "BLE Speaker"
            else -> "Other ($type)"
        }
    }

    private val playerErrorListener = object : Player.Listener {
        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            val songId = player.currentMediaItem?.mediaId ?: "unknown"
            playbackLogManager.log("PLAYBACK", "ERROR", "ExoPlayer error for song ID $songId: code=${error.errorCodeName}, msg=${error.message}")
        }
        override fun onPlaybackStateChanged(playbackState: Int) {
            val stateStr = when (playbackState) {
                Player.STATE_IDLE -> "IDLE"
                Player.STATE_BUFFERING -> "BUFFERING"
                Player.STATE_READY -> "READY"
                Player.STATE_ENDED -> "ENDED"
                else -> "UNKNOWN"
            }
            playbackLogManager.log("PLAYBACK", "INFO", "Background playback state: $stateStr (PlayWhenReady=${player.playWhenReady})")
        }
    }

    private val playbackEndListener = PlaybackEndListener {
        // Natural track completion is not an Android exemption for starting a
        // foreground service. The media-button path may show this temporary
        // notification because Android grants it a short start allowance.
        skipHandler.requestAutomaticNext()
    }
    private val favoriteStateListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            observeFavorite(mediaItem)
        }
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, null)

        queueSyncListener = QueueSyncListener(queueManager) { maybePrefetchNextTrack() }
        player.addListener(playbackEndListener)
        player.addListener(queueSyncListener)
        player.addListener(favoriteStateListener)
        player.addListener(playerErrorListener)
        playbackLogManager.log("BACKGROUND", "INFO", "XploitMediaService onCreate() - Foreground media service initialized")

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

        // Preserve real player capabilities and add only notification navigation.
        val forwardingPlayer = NotificationForwardingPlayer(
            player,
            skipHandler,
            ::handlePendingResumeRequest,
            ::handlePendingResumePauseRequest,
            ::cancelPendingResumeForNavigation
        )

        mediaSession = MediaLibrarySession.Builder(
            this,
            forwardingPlayer,
            MediaLibrarySessionCallback()
        )
            .setSessionActivity(pendingIntent)
            .build()

        observeFavorite(player.currentMediaItem)
        player.addListener(sessionSaveListener)
        restoreJob = serviceScope.launch { restoreSession() }
        serviceScope.launch {
            while (true) {
                delay(5_000L)
                saveSession()
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaSession
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == Intent.ACTION_MEDIA_BUTTON &&
            player.playbackState == Player.STATE_IDLE &&
            mediaButtonRequestsPlay(intent, player.playWhenReady)) {
            // Preserve a hardware Play request while the session and stream are restored.
            pendingPlayRequested = true
            android.util.Log.d("XploitDebug", "Media button Play: restoring saved track and preloading queue")
            showResumeNotification()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun showResumeNotification() {
        if (showingResumeNotification) return
        val channel = "playback_resumption"
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(channel, "Reanudación de música", NotificationManager.IMPORTANCE_LOW)
            )
        }
        startForeground(DefaultMediaNotificationProvider.DEFAULT_NOTIFICATION_ID,
            NotificationCompat.Builder(this, channel)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(getString(R.string.app_name))
                .setContentText("Cargando reproducción…")
                .setOngoing(true).setOnlyAlertOnce(true).build())
        showingResumeNotification = true
        resumeWatchdog = serviceScope.launch {
            delay(35_000L)
            resumeJob?.cancel()
            pendingResumeJob?.cancel()
            skipHandler.cancelPendingNavigation()
            finishFailedResumption()
        }
    }

    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        // Let Media3 replace the temporary startup notification as soon as restored
        // metadata is available, so the media controls remain visible during resolution.
        super.onUpdateNotification(session, startInForegroundRequired)
    }

    private fun finishFailedResumption() {
        resumeWatchdog?.cancel()
        if (showingResumeNotification) {
            showingResumeNotification = false
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
        if (player.mediaItemCount == 0 && pendingResumption == null) stopSelf()
    }

    /** Refresh an expired stream only when playback is actually requested. */
    private fun handlePendingResumeRequest(playWhenReady: Boolean): Boolean {
        val saved = pendingResumption
        if (saved == null) {
            if (!restoreCompleted && player.mediaItemCount == 0) {
                pendingPlayRequested = pendingPlayRequested || playWhenReady
                return true
            }
            return false
        }
        val song = saved.songs.getOrNull(saved.index) ?: return false
        if (player.currentMediaItem?.mediaId != song.id) {
            pendingResumption = null
            pendingResumeShouldPlay = false
            return false
        }
        pendingResumeShouldPlay = pendingResumeShouldPlay || playWhenReady || pendingPlayRequested
        pendingPlayRequested = false
        if (pendingResumeJob?.isActive == true) return true

        pendingResumeJob = serviceScope.launch {
            try {
                val resolved = resumptionResolver.resolve(saved)
                    ?: throw IllegalStateException("No se pudo renovar el audio de ${song.title}")
                if (player.currentMediaItem?.mediaId != song.id) return@launch
                player.playWhenReady = false
                player.setMediaItem(resolved.item, saved.positionMs)
                player.prepare()
                // Begin resolving the next item before this track can finish.
                // The current track remains paused unless Play was actually requested.
                if (!skipHandler.hasActiveDelegate()) {
                    backgroundPlaybackNavigator.prefetchNextTrack()
                }
                if (pendingResumeShouldPlay) player.play() else player.pause()
                pendingResumption = null
                pendingResumeShouldPlay = false
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                android.util.Log.w("XploitDebug", "Saved playback remains available for retry", error)
                if (player.currentMediaItem?.mediaId == song.id) {
                    player.pause()
                    finishFailedResumption()
                }
            }
        }
        return true
    }

    private fun handlePendingResumePauseRequest(): Boolean {
        val savedSong = pendingResumption?.songs?.getOrNull(pendingResumption?.index ?: -1)
        if (savedSong?.id != player.currentMediaItem?.mediaId) {
            if (pendingResumeJob?.isActive == true) cancelPendingResumeForNavigation()
            return false
        }
        pendingPlayRequested = false
        pendingResumeShouldPlay = false
        pendingResumeJob?.cancel()
        pendingResumeJob = null
        return true
    }

    private fun cancelPendingResumeForNavigation() {
        pendingPlayRequested = false
        pendingResumeShouldPlay = false
        pendingResumeJob?.cancel()
        pendingResumeJob = null
        pendingResumption = null
    }

    private fun resumePendingHardwarePlay() {
        if (!pendingPlayRequested && !player.playWhenReady) return
        serviceScope.launch {
            // Let Media3 apply its resumption result before replacing the placeholder item.
            kotlinx.coroutines.yield()
            if (pendingPlayRequested || player.playWhenReady) {
                handlePendingResumeRequest(playWhenReady = true)
            }
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        saveSession()
        val sessionPlayer = mediaSession?.player
        if (sessionPlayer == null || sessionPlayer.mediaItemCount == 0) {
            stopSelf()
            return
        }

        // Keep the paused media session discoverable for headset/car controls.
        // MediaSessionService's default task-removed handling stops an idle service.
        if (!sessionPlayer.playWhenReady) return
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        try {
            audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
        } catch (_: Exception) {}
        playbackLogManager.log("BACKGROUND", "INFO", "XploitMediaService onDestroy()")

        resumeFuture?.cancel(false)
        saveSession()
        player.removeListener(sessionSaveListener)
        player.removeListener(playbackEndListener)
        player.removeListener(queueSyncListener)
        player.removeListener(favoriteStateListener)
        player.removeListener(playerErrorListener)
        favoriteStateJob?.cancel()
        serviceScope.cancel()
        mediaSession?.run {
            // player.release() // Fix SRE Performance: El player es Singleton (Hilt), no debe matarse.
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    private fun observeFavorite(mediaItem: MediaItem?) {
        favoriteStateJob?.cancel()
        currentFavorite = false
        updateFavoriteLayout()
        val mediaId = mediaItem?.mediaId?.takeIf { it.isNotBlank() } ?: return
        favoriteStateJob = serviceScope.launch {
            favoritesRepository.isFavorite(mediaId)
                .distinctUntilChanged()
                .collect { isFavorite ->
                    currentFavorite = isFavorite
                    updateFavoriteLayout()
                }
        }
    }

    private fun maybePrefetchNextTrack() {
        if (player.isPlaying && !skipHandler.hasActiveDelegate()) {
            backgroundPlaybackNavigator.prefetchNextTrack()
        }
    }

    private fun updateFavoriteLayout() {
        mediaSession?.setCustomLayout(listOf(favoriteCommandButton(currentFavorite)))
    }

    private inner class MediaLibrarySessionCallback : MediaLibrarySession.Callback {
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            resumeFuture?.takeUnless { it.isDone }?.let { return it }
            // Avoid racing the passive restoration initiated when the service was created.
            restoreJob?.cancel()
            val result = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            resumeFuture = result
            resumeJob = serviceScope.launch {
                try {
                    val saved = resumptionResolver.loadSavedSession()
                        ?: throw IllegalStateException("No se pudo recuperar la última canción")
                    // A new user selection during lookup wins over the saved session.
                    if (player.mediaItemCount > 0 && player.currentMediaItem?.mediaId != saved.songs[saved.index].id) {
                        result.set(MediaSession.MediaItemsWithStartPosition(
                            (0 until player.mediaItemCount).map(player::getMediaItemAt),
                            player.currentMediaItemIndex, player.currentPosition))
                    } else {
                        if (player.mediaItemCount == 0) queueManager.setQueue(saved.songs, saved.index)
                        pendingResumption = saved
                        result.set(MediaSession.MediaItemsWithStartPosition(
                            listOf(resumptionPlaceholderItem(saved)), 0, saved.positionMs))
                        resumePendingHardwarePlay()
                    }
                } catch (error: Exception) {
                    result.setException(error)
                    finishFailedResumption()
                    if (error is CancellationException) throw error
                    android.util.Log.w("XploitDebug", "Bluetooth resumption failed", error)
                }
            }
            return result
        }

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailablePlayerCommands(session.player.availableCommands)
                .setAvailableSessionCommands(
                    notificationSessionCommands(MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS)
                )
                .setCustomLayout(ImmutableList.of(favoriteCommandButton(currentFavorite)))
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction != ACTION_TOGGLE_FAVORITE) {
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
            }

            val song = session.player.currentMediaItem?.toSongOrNull()
                ?: return Futures.immediateFuture(
                    SessionResult(SessionResult.RESULT_ERROR_INVALID_STATE)
                )
            val result = SettableFuture.create<SessionResult>()
            serviceScope.launch {
                runCatching { favoritesRepository.toggleFavorite(song) }
                    .onSuccess { result.set(SessionResult(SessionResult.RESULT_SUCCESS)) }
                    .onFailure { result.set(SessionResult(SessionResult.RESULT_ERROR_UNKNOWN)) }
            }
            return result
        }
    }
}
