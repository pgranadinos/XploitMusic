package com.xploitmusic.app.data.provider.impl

import com.xploitmusic.app.domain.model.Song
import com.xploitmusic.app.domain.provider.OperationType
import com.xploitmusic.app.domain.provider.ProviderError
import com.xploitmusic.app.domain.provider.config.ProviderConfigManager
import com.xploitmusic.app.domain.provider.config.ProviderMetricsLogger
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class ProviderGatewayImplTest {

    private lateinit var youTubeMusicProvider: YouTubeMusicProvider
    private lateinit var localYouTubeProvider: LocalYouTubeExtractorProvider
    private lateinit var bffProvider: BffProvider
    private lateinit var pipedProvider: PipedProvider
    private lateinit var invidiousProvider: InvidiousProvider
    private lateinit var configManager: ProviderConfigManager
    private lateinit var metricsLogger: ProviderMetricsLogger
    private lateinit var gateway: ProviderGatewayImpl

    @Before
    fun setUp() {
        youTubeMusicProvider = mockk()
        localYouTubeProvider = mockk()
        bffProvider = mockk()
        pipedProvider = mockk()
        invidiousProvider = mockk()
        configManager = mockk()
        metricsLogger = mockk(relaxed = true)

        every { youTubeMusicProvider.providerId } returns "YOUTUBE_MUSIC"
        every { localYouTubeProvider.providerId } returns "LOCAL_YOUTUBE"
        every { bffProvider.providerId } returns "XPLOIT_BFF"
        every { pipedProvider.providerId } returns "PIPED"
        every { invidiousProvider.providerId } returns "INVIDIOUS"

        gateway = ProviderGatewayImpl(
            youTubeMusicProvider = youTubeMusicProvider,
            localYouTubeProvider = localYouTubeProvider,
            bffProvider = bffProvider,
            pipedProvider = pipedProvider,
            invidiousProvider = invidiousProvider,
            configManager = configManager,
            metricsLogger = metricsLogger
        )
    }

    @Test
    fun `local extractor is used before remote stream providers`() = runTest {
        every { configManager.getFallbackOrder(OperationType.GET_STREAM) } returns
            listOf("LOCAL_YOUTUBE", "PIPED", "XPLOIT_BFF")
        coEvery { localYouTubeProvider.getStream("track") } returns
            Result.success("https://googlevideo.example/audio")

        val result = gateway.getStream("track")

        assertThat(result.getOrNull()).isEqualTo("https://googlevideo.example/audio")
        coVerify(exactly = 0) { pipedProvider.getStream(any()) }
        coVerify(exactly = 0) { bffProvider.getStream(any()) }
    }

    @Test
    fun `remote provider remains fallback when local extraction fails`() = runTest {
        every { configManager.getFallbackOrder(OperationType.GET_STREAM) } returns
            listOf("LOCAL_YOUTUBE", "PIPED")
        coEvery { localYouTubeProvider.getStream("track") } returns
            Result.failure(ProviderError.NetworkIssue("LOCAL_YOUTUBE"))
        coEvery { pipedProvider.getStream("track") } returns
            Result.success("https://fallback.example/audio")

        val result = gateway.getStream("track")

        assertThat(result.getOrNull()).isEqualTo("https://fallback.example/audio")
        coVerify(exactly = 1) { pipedProvider.getStream("track") }
    }

    @Test
    fun `search falls back after provider failure`() = runTest {
        val expected = listOf(song("piped-result"))
        every { configManager.getFallbackOrder(OperationType.SEARCH) } returns
            listOf("XPLOIT_BFF", "PIPED", "INVIDIOUS")
        coEvery { bffProvider.search("rock") } returns
            Result.failure(ProviderError.RateLimited("XPLOIT_BFF"))
        coEvery { pipedProvider.search("rock") } returns Result.success(expected)

        val result = gateway.search("rock")

        assertThat(result.getOrNull()).isEqualTo(expected)
        coVerify(exactly = 1) { bffProvider.search("rock") }
        coVerify(exactly = 1) { pipedProvider.search("rock") }
        coVerify(exactly = 0) { invidiousProvider.search(any()) }
        verify { metricsLogger.logError("XPLOIT_BFF", "SEARCH", any()) }
        verify { metricsLogger.logSuccess("PIPED", "SEARCH", any()) }
    }

    @Test
    fun `duplicate provider ids are executed once`() = runTest {
        every { configManager.getFallbackOrder(OperationType.SEARCH) } returns
            listOf("PIPED", "PIPED", "XPLOIT_BFF")
        coEvery { pipedProvider.search("pop") } returns Result.failure(Exception("down"))
        coEvery { bffProvider.search("pop") } returns Result.success(listOf(song("bff-result")))

        val result = gateway.search("pop")

        assertThat(result.isSuccess).isTrue()
        coVerify(exactly = 1) { pipedProvider.search("pop") }
        coVerify(exactly = 1) { bffProvider.search("pop") }
    }

    @Test
    fun `empty related result continues fallback`() = runTest {
        val expected = listOf(song("related"))
        every { configManager.getFallbackOrder(OperationType.GET_RELATED) } returns
            listOf("XPLOIT_BFF", "PIPED")
        coEvery { bffProvider.getRelated("seed") } returns Result.success(emptyList())
        coEvery { pipedProvider.getRelated("seed") } returns Result.success(expected)

        val result = gateway.getRelated("seed")

        assertThat(result.getOrNull()).isEqualTo(expected)
        verify { metricsLogger.logError("XPLOIT_BFF", "GET_RELATED", any<ProviderError.NotFound>()) }
        coVerify(exactly = 1) { pipedProvider.getRelated("seed") }
    }

    @Test
    fun `blank stream url continues fallback`() = runTest {
        every { configManager.getFallbackOrder(OperationType.GET_STREAM) } returns
            listOf("XPLOIT_BFF", "PIPED")
        coEvery { bffProvider.getStream("track") } returns Result.success("   ")
        coEvery { pipedProvider.getStream("track") } returns Result.success("https://audio.example/track")

        val result = gateway.getStream("track")

        assertThat(result.getOrNull()).isEqualTo("https://audio.example/track")
        coVerify(exactly = 1) { pipedProvider.getStream("track") }
    }

    @Test
    fun `cancellation is rethrown without trying next provider`() = runTest {
        every { configManager.getFallbackOrder(OperationType.GET_STREAM) } returns
            listOf("XPLOIT_BFF", "PIPED")
        coEvery { bffProvider.getStream("track") } returns
            Result.failure(CancellationException("cancelled"))

        var thrown: CancellationException? = null
        try {
            gateway.getStream("track")
        } catch (error: CancellationException) {
            thrown = error
        }

        assertThat(thrown).isNotNull()
        coVerify(exactly = 0) { pipedProvider.getStream(any()) }
    }

    private fun song(id: String) = Song(
        id = id,
        title = "Song $id",
        artist = "Artist",
        thumbnailUrl = "",
        durationSeconds = 180L
    )
}
