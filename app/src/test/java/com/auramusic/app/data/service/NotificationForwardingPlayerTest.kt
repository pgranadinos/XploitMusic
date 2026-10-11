package com.auramusic.app.data.service

import androidx.media3.common.Player
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaSession
import com.auramusic.app.data.repository.QueueManagerImpl
import com.auramusic.app.domain.model.Song
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NotificationForwardingPlayerTest {

    private lateinit var wrappedPlayer: Player
    private lateinit var skipHandler: SkipHandler
    private lateinit var forwardingPlayer: NotificationForwardingPlayer

    @Before
    fun setUp() {
        wrappedPlayer = mockk(relaxed = true)
        skipHandler = mockk(relaxed = true)
        forwardingPlayer = NotificationForwardingPlayer(wrappedPlayer, skipHandler, { false }, { false }, {})
    }

    @Test
    fun `available commands preserve wrapped capabilities and add playback controls`() {
        val baseCommands = Player.Commands.Builder()
            .add(Player.COMMAND_PREPARE)
            .build()
        val commands = notificationCommands(baseCommands)

        assertThat(commands.contains(Player.COMMAND_PREPARE)).isTrue()
        assertThat(commands.contains(Player.COMMAND_PLAY_PAUSE)).isTrue()
        assertThat(commands.contains(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)).isTrue()
        assertThat(commands.contains(Player.COMMAND_SEEK_TO_NEXT)).isTrue()
        assertThat(commands.contains(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)).isTrue()
        assertThat(commands.contains(Player.COMMAND_SEEK_TO_PREVIOUS)).isTrue()
        assertThat(commands.contains(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)).isTrue()
        assertThat(commands.contains(Player.COMMAND_SET_SPEED_AND_PITCH)).isFalse()
    }

    @Test
    fun `play and prepare remain available before media is loaded`() {
        every { wrappedPlayer.availableCommands } returns Player.Commands.Builder().build()

        assertThat(forwardingPlayer.isCommandAvailable(Player.COMMAND_PLAY_PAUSE)).isTrue()
        assertThat(forwardingPlayer.isCommandAvailable(Player.COMMAND_PREPARE)).isTrue()
        assertThat(forwardingPlayer.availableCommands.contains(Player.COMMAND_PLAY_PAUSE)).isTrue()
        assertThat(forwardingPlayer.availableCommands.contains(Player.COMMAND_PREPARE)).isTrue()
    }

    @Test
    fun `play prepares restored idle item before resuming`() {
        every { wrappedPlayer.playbackState } returns Player.STATE_IDLE
        every { wrappedPlayer.mediaItemCount } returns 1
        forwardingPlayer.play()
        io.mockk.verifyOrder {
            wrappedPlayer.prepare()
            wrappedPlayer.play()
        }
    }

    @Test
    fun `pending resume request handles play without using stale media item`() {
        every { wrappedPlayer.playbackState } returns Player.STATE_IDLE
        every { wrappedPlayer.mediaItemCount } returns 1
        forwardingPlayer = NotificationForwardingPlayer(wrappedPlayer, skipHandler, { true }, { false }, {})

        forwardingPlayer.play()

        io.mockk.verify(exactly = 0) { wrappedPlayer.prepare() }
        io.mockk.verify(exactly = 0) { wrappedPlayer.play() }
    }

    @Test
    fun `play does not prepare an already ready player`() {
        every { wrappedPlayer.playbackState } returns Player.STATE_READY
        forwardingPlayer.play()
        verify(exactly = 0) { wrappedPlayer.prepare() }
        verify(exactly = 1) { wrappedPlayer.play() }
    }

    @Test
    fun `seek remains available when session connected before media was loaded`() {
        every { wrappedPlayer.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM) } returns false

        assertThat(
            forwardingPlayer.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
        ).isTrue()
    }

    @Test
    fun `notification session exposes favorite command`() {
        val commands = notificationSessionCommands(
            MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
        )

        assertThat(commands.contains(TOGGLE_FAVORITE_COMMAND)).isTrue()
    }

    @Test
    fun `favorite button reflects current state`() {
        assertThat(favoriteCommandButton(false).iconResId)
            .isEqualTo(com.auramusic.app.R.drawable.ic_favorite_border)
        assertThat(favoriteCommandButton(true).iconResId)
            .isEqualTo(com.auramusic.app.R.drawable.ic_favorite)
    }

    @Test
    fun `next command uses real queued item when available`() {
        every { wrappedPlayer.hasNextMediaItem() } returns true

        forwardingPlayer.seekToNextMediaItem()

        verify(exactly = 1) { wrappedPlayer.seekToNextMediaItem() }
        verify(exactly = 0) { skipHandler.requestSkipNext() }
    }

    @Test
    fun `next command delegates to SkipHandler when native queue is empty`() {
        every { wrappedPlayer.hasNextMediaItem() } returns false

        forwardingPlayer.seekToNextMediaItem()

        verify(exactly = 1) { skipHandler.requestSkipNext() }
        verify(exactly = 0) { wrappedPlayer.seekToNextMediaItem() }
    }

    @Test
    fun `previous command delegates after five seconds instead of restarting`() {
        every { wrappedPlayer.currentPosition } returns 5_001L

        forwardingPlayer.seekToPrevious()

        verify(exactly = 0) { wrappedPlayer.seekTo(0) }
        verify(exactly = 1) { skipHandler.requestSkipPrevious() }
    }

    @Test
    fun `previous command delegates when no native previous item exists`() {
        every { wrappedPlayer.currentPosition } returns 1_000L
        every { wrappedPlayer.hasPreviousMediaItem() } returns false

        forwardingPlayer.seekToPrevious()

        verify(exactly = 1) { skipHandler.requestSkipPrevious() }
    }

    @Test
    fun `previous command uses native item when available`() {
        every { wrappedPlayer.currentPosition } returns 1_000L
        every { wrappedPlayer.hasPreviousMediaItem() } returns true

        forwardingPlayer.seekToPrevious()

        verify(exactly = 1) { wrappedPlayer.seekToPreviousMediaItem() }
        verify(exactly = 0) { skipHandler.requestSkipPrevious() }
    }

    @Test
    fun `media transition synchronizes logical queue by media id`() {
        val queueManager = QueueManagerImpl()
        val first = Song("1", "One", "Artist", "", 180L)
        val second = Song("2", "Two", "Artist", "", 180L)
        queueManager.setQueue(listOf(first, second), 0)
        val listener = QueueSyncListener(queueManager)

        listener.onMediaItemTransition(
            MediaItem.Builder().setMediaId("2").build(),
            Player.MEDIA_ITEM_TRANSITION_REASON_SEEK
        )

        assertThat(queueManager.currentIndex.value).isEqualTo(1)
        assertThat(queueManager.currentTrack.value?.id).isEqualTo("2")
    }
}
