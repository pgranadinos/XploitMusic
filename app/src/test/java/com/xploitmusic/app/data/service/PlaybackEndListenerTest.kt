package com.xploitmusic.app.data.service

import androidx.media3.common.Player
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlaybackEndListenerTest {

    @Test
    fun `notifies only when playback reaches ended state`() {
        var notifications = 0
        val listener = PlaybackEndListener { notifications++ }

        listener.onPlaybackStateChanged(Player.STATE_BUFFERING)
        listener.onPlaybackStateChanged(Player.STATE_READY)
        assertThat(notifications).isEqualTo(0)

        listener.onPlaybackStateChanged(Player.STATE_ENDED)
        assertThat(notifications).isEqualTo(1)
    }
}
