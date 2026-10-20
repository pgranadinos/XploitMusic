package com.xploitmusic.app.presentation.player

import com.xploitmusic.app.core.util.Resource
import com.xploitmusic.app.data.local.dao.PlaylistDao
import com.xploitmusic.app.data.local.dao.SongDao
import com.xploitmusic.app.data.repository.QueueManagerImpl
import com.xploitmusic.app.data.service.SkipHandler
import com.xploitmusic.app.domain.model.Song
import com.xploitmusic.app.domain.model.PlaybackState
import com.xploitmusic.app.domain.repository.FavoritesRepository
import com.xploitmusic.app.domain.repository.PlayerRepository
import com.xploitmusic.app.domain.repository.StreamRepository
import com.xploitmusic.app.domain.usecase.GetAudioStreamUseCase
import com.xploitmusic.app.domain.usecase.GetLyricsUseCase
import com.xploitmusic.app.domain.usecase.ToggleFavoriteUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelSkipRaceTest {

    // InstantTaskExecutorRule removed as it's not needed for StateFlow

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var viewModel: PlayerViewModel
    private lateinit var mockPlayerRepo: PlayerRepository
    private lateinit var mockGetStream: GetAudioStreamUseCase
    private lateinit var mockGetLyrics: GetLyricsUseCase
    private lateinit var mockToggleFav: ToggleFavoriteUseCase
    private lateinit var mockFavRepo: FavoritesRepository
    private lateinit var mockStreamRepo: StreamRepository
    private lateinit var mockSkipHandler: SkipHandler
    private lateinit var mockOfflineManager: com.xploitmusic.app.data.repository.OfflineManager
    private lateinit var queueManager: QueueManagerImpl

    private val fakeMediaTransitionFlow = MutableStateFlow<String?>(null)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        mockPlayerRepo = mockk(relaxed = true) {
            coEvery { isPlaying } returns flowOf(false)
            coEvery { currentPosition } returns flowOf(0L)
            coEvery { duration } returns flowOf(0L)
            coEvery { trackEnded } returns flowOf(Unit)
            coEvery { mediaItemTransition } returns fakeMediaTransitionFlow
            coEvery { playerError } returns flowOf(null)
            coEvery { hasNextMediaItem() } returns false
        }

        mockGetStream = mockk(relaxed = true)
        mockGetLyrics = mockk(relaxed = true)
        mockToggleFav = mockk(relaxed = true)
        mockFavRepo = mockk(relaxed = true)
        mockStreamRepo = mockk(relaxed = true)
        mockSkipHandler = mockk(relaxed = true)
        val mockPlaylistRepo: com.xploitmusic.app.domain.repository.PlaylistRepository = mockk(relaxed = true)
        queueManager = QueueManagerImpl()

        mockOfflineManager = mockk(relaxed = true)
        coEvery { mockOfflineManager.getOfflineFilePath(any()) } returns null
        val mockAnalyticsLogger: com.xploitmusic.app.core.analytics.XploitAnalyticsLogger = mockk(relaxed = true)
        val mockPlaybackLogManager: com.xploitmusic.app.core.util.PlaybackLogManager = mockk(relaxed = true) {
            coEvery { currentNetworkType } returns MutableStateFlow("Wi-Fi")
        }
        viewModel = PlayerViewModel(
            playerRepository = mockPlayerRepo,
            getAudioStreamUseCase = mockGetStream,
            getLyricsUseCase = mockGetLyrics,
            toggleFavoriteUseCase = mockToggleFav,
            favoritesRepository = mockFavRepo,
            streamRepository = mockStreamRepo,
            skipHandler = mockSkipHandler,
            playlistRepository = mockPlaylistRepo,
            queueManager = queueManager,
            offlineManager = mockOfflineManager,
            analyticsLogger = mockAnalyticsLogger,
            playbackLogManager = mockPlaybackLogManager
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `previous uses restored queue when history is empty`() = runTest(testDispatcher) {
        val first = Song("local_1", "First", "Artist", "", 100, "content://media/1")
        val second = Song("local_2", "Second", "Artist", "", 100, "content://media/2")
        queueManager.setQueue(listOf(first, second), 1)
        viewModel.skipPrevious()
        runCurrent()
        assertEquals(first.id, queueManager.currentTrack.value?.id)
        coVerify { mockPlayerRepo.prepare("content://media/1", first) }
        coVerify(exactly = 0) { mockPlayerRepo.seekTo(any()) }
    }

    @Test
    fun `previous restarts only when no earlier track exists`() = runTest(testDispatcher) {
        queueManager.setQueue(listOf(Song("local_1", "First", "Artist", "", 100)), 0)
        viewModel.skipPrevious()
        runCurrent()
        coVerify(exactly = 1) { mockPlayerRepo.seekTo(0L) }
    }

    @Test
    fun `rapid skipNext calls debounce and fire only once`() = runTest(testDispatcher) {
        val song1 = Song("1", "Song 1", "Artist", "", 100L, "")
        val song2 = Song("2", "Song 2", "Artist", "", 100L, "")
        val song3 = Song("3", "Song 3", "Artist", "", 100L, "")
        val playlist = listOf(song1, song2, song3)

        // Prepare the queue directly so the skip path can be exercised in isolation.
        queueManager.setQueue(playlist, startIndex = 0)

        // Rapid fire skipNext 5 times in 50ms
        repeat(5) {
            viewModel.skipNext()
            advanceTimeBy(10)
        }

        advanceTimeBy(200)
        runCurrent()

        // setMediaItem replaces the source atomically; skip must not clear first.
        coVerify(exactly = 0) { mockPlayerRepo.clearQueue() }
        assertEquals("2", queueManager.currentTrack.value?.id)
        assertEquals(listOf("3"), viewModel.upNext.value.map { it.id })
    }

    @Test
    fun `automatic end emitted while explicit song buffers is ignored`() = runTest(testDispatcher) {
        val song1 = Song("1", "Song 1", "Artist", "", 100L, "")
        val song2 = Song("2", "Song 2", "Artist", "", 100L, "")
        coEvery { mockGetStream("1") } coAnswers {
            delay(1_000)
            Resource.Success("https://audio/1")
        }

        viewModel.playSong(song1, listOf(song1, song2))
        runCurrent()
        assertTrue(viewModel.playbackState.value is PlaybackState.Buffering)

        viewModel.onAutomaticNext()
        runCurrent()

        assertEquals("1", queueManager.currentTrack.value?.id)
        coVerify(exactly = 0) { mockGetStream("2") }
    }

    @Test
    fun `repeat one restarts current item without advancing queue`() = runTest(testDispatcher) {
        val song1 = Song("1", "Song 1", "Artist", "", 100L, "")
        val song2 = Song("2", "Song 2", "Artist", "", 100L, "")
        queueManager.setQueue(listOf(song1, song2), 0)
        viewModel.toggleRepeat() // ALL
        viewModel.toggleRepeat() // ONE

        viewModel.onAutomaticNext()
        runCurrent()

        assertEquals("1", queueManager.currentTrack.value?.id)
        coVerify(exactly = 1) { mockPlayerRepo.seekTo(0L) }
        coVerify(exactly = 1) { mockPlayerRepo.play() }
    }
}
