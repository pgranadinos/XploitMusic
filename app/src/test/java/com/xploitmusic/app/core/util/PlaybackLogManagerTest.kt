package com.xploitmusic.app.core.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlaybackLogManagerTest {

    @Test
    fun `sanitizeMessage strips tokens and query params from HTTP and HTTPS URLs`() {
        val message = "Failed to fetch stream from https://rr1---sn-x.googlevideo.com/videoplayback?expire=12345678&sparams=ip&sig=ABC123DEF456 with error 403"
        val sanitized = PlaybackLogManager.sanitizeMessage(message)

        assertThat(sanitized).doesNotContain("expire=12345678")
        assertThat(sanitized).doesNotContain("sig=ABC123DEF456")
        assertThat(sanitized).doesNotContain("/videoplayback")
        assertThat(sanitized).contains("rr1---sn-x.googlevideo.com")
    }

    @Test
    fun `sanitizeMessage handles plain text messages without URLs untouched`() {
        val message = "Audio output device connected: Bluetooth A2DP (Buds3 Pro)"
        val sanitized = PlaybackLogManager.sanitizeMessage(message)

        assertThat(sanitized).isEqualTo(message)
    }
}
