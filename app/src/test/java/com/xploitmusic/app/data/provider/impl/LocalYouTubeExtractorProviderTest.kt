package com.xploitmusic.app.data.provider.impl

import com.google.common.truth.Truth.assertThat
import com.xploitmusic.app.data.provider.local.NewPipeExtractorClient
import com.xploitmusic.app.domain.provider.ProviderError
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class LocalYouTubeExtractorProviderTest {

    private lateinit var extractor: NewPipeExtractorClient
    private lateinit var provider: LocalYouTubeExtractorProvider

    @Before
    fun setup() {
        extractor = mockk()
        provider = LocalYouTubeExtractorProvider(extractor)
    }

    @Test
    fun `getStream times out at 12 seconds when extractor exceeds timeout`() = runTest {
        coEvery { extractor.getStream("slow_video_id") } coAnswers {
            delay(15_000L) // Takes 15s, exceeds 12s limit
            "https://slow/audio.m4a"
        }

        val result = provider.getStream("slow_video_id")

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(ProviderError.NetworkIssue::class.java)
    }

    @Test
    fun `getStream succeeds within 12 seconds`() = runTest {
        coEvery { extractor.getStream("fast_video_id") } returns "https://fast/audio.m4a"

        val result = provider.getStream("fast_video_id")

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isEqualTo("https://fast/audio.m4a")
    }
}
