package com.auramusic.app.core.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LrcParserTest {

    @Test
    fun `accepts millisecond and centisecond timestamps`() {
        val lines = LrcParser.parse("[0:01.5]One\n[00:02.050]Two")

        assertThat(lines.map { it.startTimeMs }).containsExactly(1_500L, 2_050L).inOrder()
    }

    @Test
    fun `turns plain lyrics into displayable timed lines`() {
        val lines = LrcParser.parse(
            syncedLyrics = null,
            plainLyrics = "First\n\nSecond\nThird",
            durationSeconds = 90.0
        )

        assertThat(lines.map { it.text }).containsExactly("First", "Second", "Third").inOrder()
        assertThat(lines.map { it.startTimeMs }).containsExactly(0L, 30_000L, 60_000L).inOrder()
    }
}
