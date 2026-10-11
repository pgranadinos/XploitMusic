package com.xploitmusic.app.data.service

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadWorkerTest {

    @Test
    fun `uses response content type instead of guessing from signed url`() {
        assertEquals(
            "webm",
            DownloadWorker.extensionForAudio(
                contentType = "audio/webm; codecs=\"opus\"",
                url = "https://example.test/videoplayback?mime=audio%2Fwebm"
            )
        )
        assertEquals(
            "m4a",
            DownloadWorker.extensionForAudio(
                contentType = "audio/mp4; codecs=\"mp4a.40.2\"",
                url = "https://example.test/videoplayback"
            )
        )
    }

    @Test
    fun `uses safe url extension fallback for missing content type`() {
        assertEquals(
            "mp3",
            DownloadWorker.extensionForAudio(null, "https://example.test/audio/song.mp3?token=abc")
        )
        assertEquals(
            "m4a",
            DownloadWorker.extensionForAudio(null, "https://example.test/videoplayback?token=abc")
        )
    }
}
