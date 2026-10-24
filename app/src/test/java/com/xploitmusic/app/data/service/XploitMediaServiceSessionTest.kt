package com.xploitmusic.app.data.service

import androidx.media3.common.Player
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class XploitMediaServiceSessionTest {

    @Test
    fun `a) STATE_ENDED returns 0L even if positionMs is high`() {
        val result = posicionAGuardar(Player.STATE_ENDED, 150000L, 180000L)
        assertThat(result).isEqualTo(0L)
    }

    @Test
    fun `b) positionMs near end returns 0L`() {
        val result = posicionAGuardar(Player.STATE_READY, 179500L, 180000L)
        assertThat(result).isEqualTo(0L)
    }

    @Test
    fun `c) positionMs in middle returns positionMs`() {
        val result = posicionAGuardar(Player.STATE_READY, 60000L, 180000L)
        assertThat(result).isEqualTo(60000L)
    }

    @Test
    fun `d) durationMs equals 0 returns positionMs without changes`() {
        val result = posicionAGuardar(Player.STATE_READY, 60000L, 0L)
        assertThat(result).isEqualTo(60000L)
    }
}
