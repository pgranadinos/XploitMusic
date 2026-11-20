package com.xploitmusic.app.presentation.player

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlaybackProgressTest {

    @Test
    fun `duration 0 returns 0f`() {
        val progress = calculateProgress(currentPositionMs = 5000L, durationMs = 0L, durationSeconds = 0L)
        assertThat(progress).isEqualTo(0f)
    }

    @Test
    fun `currentPosition greater than duration returns 1f`() {
        val progress = calculateProgress(currentPositionMs = 200000L, durationMs = 180000L, durationSeconds = 180L)
        assertThat(progress).isEqualTo(1f)
    }

    @Test
    fun `duration derived from durationSeconds computes correct progress`() {
        val progress = calculateProgress(currentPositionMs = 90000L, durationMs = 0L, durationSeconds = 180L)
        assertThat(progress).isEqualTo(0.5f)
    }
}
