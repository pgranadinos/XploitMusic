package com.auramusic.app.data.provider.local

import com.google.common.truth.Truth.assertThat
import org.schabi.newpipe.extractor.MediaFormat
import org.junit.Test

class NewPipeExtractorClientTest {

    @Test
    fun `prefers m4a before higher bitrate webm`() {
        val selected = selectBestAudio(
            listOf(
                AudioCandidate("https://audio/webm", MediaFormat.WEBMA_OPUS, 160_000),
                AudioCandidate("https://audio/m4a", MediaFormat.M4A, 128_000)
            )
        )

        assertThat(selected).isEqualTo("https://audio/m4a")
    }

    @Test
    fun `uses highest bitrate inside preferred format`() {
        val selected = selectBestAudio(
            listOf(
                AudioCandidate("https://audio/low", MediaFormat.M4A, 64_000),
                AudioCandidate("https://audio/high", MediaFormat.M4A, 128_000)
            )
        )

        assertThat(selected).isEqualTo("https://audio/high")
    }

    @Test
    fun `rejects cleartext and empty candidates`() {
        assertThat(
            selectBestAudio(listOf(AudioCandidate("http://audio/insecure", MediaFormat.M4A, 128_000)))
        ).isNull()
        assertThat(selectBestAudio(emptyList())).isNull()
    }
}
