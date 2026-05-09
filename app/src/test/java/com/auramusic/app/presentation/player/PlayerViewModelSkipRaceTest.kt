package com.auramusic.app.presentation.player

import com.auramusic.app.data.local.dao.PlaylistDao
import com.auramusic.app.data.local.dao.SongDao
import com.auramusic.app.data.service.SkipHandler
import com.auramusic.app.domain.model.Song
import com.auramusic.app.domain.model.PlaybackState
import com.auramusic.app.domain.repository.FavoritesRepository
import com.auramusic.app.domain.repository.PlayerRepository
import com.auramusic.app.domain.repository.QueueManager
import com.auramusic.app.domain.repository.StreamRepository
import com.auramusic.app.domain.usecase.GetAudioStreamUseCase
import com.auramusic.app.domain.usecase.GetLyricsUseCase
import com.auramusic.app.domain.usecase.ToggleFavoriteUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
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
    private lateinit var mockPlaylistDao: PlaylistDao
    private lateinit var mockSongDao: SongDao
    private lateinit var mockQueueManager: QueueManager

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
        mockSkipHandler = mockk(relaxed = true) {
            coEvery { skipRequests } returns kotlinx.coroutines.flow.MutableSharedFlow()
        }
        mockPlaylistDao = mockk(relaxed = true)
        mockSongDao = mockk(relaxed = true)
        mockQueueManager = mockk(relaxed = true)

        val mockOfflineManager: com.auramusic.app.data.repository.OfflineManager = mockk(relaxed = true)
        val mockAnalyticsLogger: com.auramusic.app.core.analytics.AuraAnalyticsLogger = mockk(relaxed = true)
        
        viewModel = PlayerViewModel(
            mockPlayerRepo, mockGetStream, mockGetLyrics, mockToggleFav,
            mockFavRepo, mockStreamRepo, mockSkipHandler, mockPlaylistDao,
            mockSongDao, mockQueueManager, mockOfflineManager, mockAnalyticsLogger
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `rapid skipNext calls debounce and fire only once`() = runTest(testDispatcher) {
        val song1 = Song("1", "Song 1", "Artist", "", 100L, "")
        val song2 = Song("2", "Song 2", "Artist", "", 100L, "")
        val song3 = Song("3", "Song 3", "Artist", "", 100L, "")
        val playlist = listOf(song1, song2, song3)

        // Setup playlist
        viewModel.playSong(song1, playlist)
        advanceTimeBy(100) // Let initial play run

        // Rapid fire skipNext 5 times in 50ms
        repeat(5) {
            viewModel.skipNext()
            advanceTimeBy(10)
        }

        // Wait for the 100ms debounce of the LAST skip to complete
        advanceTimeBy(150)

        // verify playerRepository.clearQueue and prepare are only called ONCE for the final rapid skip
        coVerify(exactly = 1) { mockPlayerRepo.clearQueue() }
    }
}
