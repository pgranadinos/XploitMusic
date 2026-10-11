package com.xploitmusic.app.data.service

import com.xploitmusic.app.core.util.Resource
import com.xploitmusic.app.data.repository.OfflineManager
import com.xploitmusic.app.data.repository.QueueManagerImpl
import com.xploitmusic.app.domain.model.Song
import com.xploitmusic.app.domain.repository.FavoritesRepository
import com.xploitmusic.app.domain.repository.PlayerRepository
import com.xploitmusic.app.domain.repository.StreamRepository
import com.xploitmusic.app.domain.usecase.GetAudioStreamUseCase
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.After
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.flow.flowOf
import io.mockk.every

@OptIn(ExperimentalCoroutinesApi::class)
class BackgroundPlaybackNavigatorTest {
    private val dispatcher = StandardTestDispatcher()
    @After fun teardown() { Dispatchers.resetMain() }

    private lateinit var playerRepository: PlayerRepository
    private lateinit var getAudioStream: GetAudioStreamUseCase
    private lateinit var favoritesRepository: FavoritesRepository
    private lateinit var streamRepository: StreamRepository
    private lateinit var offlineManager: OfflineManager
    private lateinit var queueManager: QueueManagerImpl
    private lateinit var navigator: BackgroundPlaybackNavigator

    private fun song(id: String) = Song(id, "Song $id", "Artist", "", 180L)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        playerRepository = mockk(relaxed = true)
        getAudioStream = mockk()
        favoritesRepository = mockk()
        every { favoritesRepository.getFavorites() } returns flowOf(emptyList())
        every { favoritesRepository.getMostPlayed() } returns flowOf(emptyList())
        streamRepository = mockk()
        offlineManager = mockk()
        queueManager = QueueManagerImpl()
        navigator = BackgroundPlaybackNavigator(
            playerRepository,
            queueManager,
            favoritesRepository,
            getAudioStream,
            streamRepository,
            offlineManager
        )
        coEvery { offlineManager.getOfflineFilePath(any()) } returns null
    }

    @Test
    fun `next plays from logical queue without a ViewModel`() = runTest {
        val first = song("1")
        val second = song("2")
        queueManager.setQueue(listOf(first, second), 0)
        coEvery { getAudioStream("2") } returns Resource.Success("https://audio/2")

        navigator.navigateNext()

        assertThat(queueManager.currentTrack.value).isEqualTo(second)
        coVerify(exactly = 1) { playerRepository.prepare("https://audio/2", second) }
        coVerify(exactly = 1) { playerRepository.play() }
    }

    @Test
    fun `next replenishes an exhausted logical queue`() = runTest {
        val current = song("1")
        val related = song("2")
        queueManager.setQueue(listOf(current), 0)
        coEvery { streamRepository.getRelatedSongs("1") } returns Resource.Success(listOf(related))
        coEvery { getAudioStream("2") } returns Resource.Success("https://audio/2")

        navigator.navigateNext()

        assertThat(queueManager.currentTrack.value).isEqualTo(related)
        coVerify(exactly = 1) { playerRepository.prepare("https://audio/2", related) }
    }

    @Test
    fun `resolved background prefetch is added to the native player queue`() = runTest {
        val first = song("1")
        val second = song("2")
        queueManager.setQueue(listOf(first, second), 0)
        coEvery { playerRepository.hasNextMediaItem() } returns false

        navigator.enqueuePrefetchedNextTrack(second, "https://audio/2")

        coVerify(exactly = 1) { playerRepository.addNextMediaItem("https://audio/2", second) }
        coVerify(exactly = 0) { playerRepository.prepare(any(), any()) }
    }

    @Test
    fun `background prefetch does not duplicate an existing native next item`() = runTest {
        val first = song("1")
        val second = song("2")
        queueManager.setQueue(listOf(first, second), 0)
        coEvery { playerRepository.hasNextMediaItem() } returns true

        navigator.enqueuePrefetchedNextTrack(second, "https://audio/2")

        coVerify(exactly = 0) { playerRepository.addNextMediaItem(any(), any()) }
    }

    @Test
    fun `background prefetch does not queue a song after logical queue changes`() = runTest {
        val first = song("1")
        val second = song("2")
        val third = song("3")
        queueManager.setQueue(listOf(first, second, third), 0)
        queueManager.skipToNext()
        coEvery { playerRepository.hasNextMediaItem() } returns false

        navigator.enqueuePrefetchedNextTrack(second, "https://audio/2")

        coVerify(exactly = 0) { playerRepository.addNextMediaItem(any(), any()) }
    }

    @Test
    fun `failed previous resolution restores logical position`() = runTest {
        val first = song("1")
        val second = song("2")
        queueManager.setQueue(listOf(first, second), 1)
        coEvery { getAudioStream("1") } returns Resource.Error("unavailable")

        navigator.navigatePrevious()

        assertThat(queueManager.currentTrack.value).isEqualTo(second)
        coVerify(exactly = 0) { playerRepository.prepare(any(), any()) }
    }

    @Test
    fun `automatic next plays queued songs even with recommendations disabled`() = runTest {
        queueManager.setQueue(listOf(song("1"), song("2")), 0)
        queueManager.toggleAutoplay()
        coEvery { getAudioStream("2") } returns Resource.Success("https://audio/2")

        navigator.navigateAutomatically()

        assertThat(queueManager.currentTrack.value?.id).isEqualTo("2")
        coVerify { playerRepository.awaitPlaybackStarted("2") }
    }

    @Test fun `duplicate automatic events do not enqueue extra skips and manual skip replaces pending load`() = runTest {
        queueManager.setQueue(listOf(song("1"), song("2"), song("3")), 0)
        var attempts = 0
        coEvery { getAudioStream("2") } coAnswers {
            if (++attempts == 1) awaitCancellation()
            Resource.Success("https://audio/2")
        }
        navigator.requestAutomaticNext()
        runCurrent()
        navigator.requestAutomaticNext()
        runCurrent()
        assertThat(attempts).isEqualTo(1)
        navigator.requestSkipNext()
        runCurrent()
        assertThat(attempts).isEqualTo(2)
        assertThat(queueManager.currentTrack.value?.id).isEqualTo("2")
        coVerify(exactly = 1) { playerRepository.prepare(any(), any()) }
        coVerify(exactly = 0) { getAudioStream("3") }
    }

    @Test fun `hung load times out without launching queued stale commands`() = runTest {
        queueManager.setQueue(listOf(song("1"), song("2")), 0)
        coEvery { getAudioStream("2") } coAnswers { awaitCancellation() }
        navigator.requestAutomaticNext()
        runCurrent()
        advanceTimeBy(40_001)
        runCurrent()
        assertThat(queueManager.currentTrack.value?.id).isEqualTo("1")
        coVerify { playerRepository.pause() }
        coVerify(exactly = 0) { playerRepository.prepare(any(), any()) }
    }

    @Test fun `buffering is not treated as successful playback`() = runTest {
        queueManager.setQueue(listOf(song("1"), song("2")), 0)
        coEvery { getAudioStream("2") } returns Resource.Success("https://audio/2")
        coEvery { playerRepository.awaitPlaybackStarted("2") } coAnswers { awaitCancellation() }
        navigator.requestAutomaticNext()
        runCurrent()
        advanceTimeBy(40_001)
        runCurrent()
        assertThat(queueManager.currentTrack.value?.id).isEqualTo("2")
        coVerify { playerRepository.pause() }
    }
}
