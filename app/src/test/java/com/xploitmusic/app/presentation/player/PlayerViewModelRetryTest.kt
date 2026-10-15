package com.xploitmusic.app.presentation.player

import com.xploitmusic.app.core.util.PlaybackLogManager
import com.xploitmusic.app.core.util.Resource
import com.xploitmusic.app.data.repository.OfflineManager
import com.xploitmusic.app.data.repository.QueueManagerImpl
import com.xploitmusic.app.data.service.SkipHandler
import com.xploitmusic.app.domain.model.Song
import com.xploitmusic.app.domain.repository.FavoritesRepository
import com.xploitmusic.app.domain.repository.PlayerRepository
import com.xploitmusic.app.domain.repository.StreamRepository
import com.xploitmusic.app.domain.usecase.GetAudioStreamUseCase
import com.xploitmusic.app.domain.usecase.GetLyricsUseCase
import com.xploitmusic.app.domain.usecase.ToggleFavoriteUseCase
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelRetryTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var viewModel: PlayerViewModel
    private lateinit var mockPlayerRepo: PlayerRepository
    private lateinit var mockGetStream: GetAudioStreamUseCase
    private lateinit var mockGetLyrics: GetLyricsUseCase
    private lateinit var mockToggleFav: ToggleFavoriteUseCase
    private lateinit var mockFavRepo: FavoritesRepository
    private lateinit var mockStreamRepo: StreamRepository
    private lateinit var mockSkipHandler: SkipHandler
    private lateinit var mockOfflineManager: OfflineManager
    private lateinit var queueManager: QueueManagerImpl
    private lateinit var mockPlaybackLogManager: PlaybackLogManager

    private val playerErrorFlow = MutableSharedFlow<String?>()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        mockPlayerRepo = mockk(relaxed = true) {
            coEvery { isPlaying } returns flowOf(false)
            coEvery { currentPosition } returns MutableStateFlow(5000L)
            coEvery { duration } returns flowOf(100000L)
            coEvery { trackEnded } returns flowOf(Unit)
            coEvery { mediaItemTransition } returns flowOf(null)
            coEvery { playerError } returns playerErrorFlow
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
        mockPlaybackLogManager = mockk(relaxed = true)

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
    fun `http 403 error invalidates stream and retries up to 2 times`() = runTest(testDispatcher) {
        val testSong = Song("song_403", "Test 403", "Artist", "", 100L, "http://audio/403")
        coEvery { mockGetStream("song_403") } returns Resource.Success("http://audio/fresh")

        viewModel.playSingleSong(testSong)
        runCurrent()

        // Emit 1st HTTP 403 error
        playerErrorFlow.emit("Error del servidor de audio (HTTP 403)")
        runCurrent()

        verify(exactly = 1) { mockStreamRepo.invalidateStream("song_403") }

        // Emit 2nd HTTP 403 error
        playerErrorFlow.emit("Error del servidor de audio (HTTP 403)")
        runCurrent()

        verify(exactly = 2) { mockStreamRepo.invalidateStream("song_403") }

        // Emit 3rd HTTP 403 error (should NOT retry a 3rd time)
        playerErrorFlow.emit("Error del servidor de audio (HTTP 403)")
        runCurrent()

        verify(exactly = 2) { mockStreamRepo.invalidateStream("song_403") }
    }

    @Test
    fun `changing song after error does not apply saved position to new song`() = runTest(testDispatcher) {
        val song1 = Song("song_1", "Song 1", "Artist", "", 100L, "http://audio/1")
        val song2 = Song("song_2", "Song 2", "Artist", "", 100L, "http://audio/2")
        coEvery { mockGetStream("song_1") } returns Resource.Success("http://audio/fresh1")
        coEvery { mockGetStream("song_2") } returns Resource.Success("http://audio/fresh2")

        viewModel.playSingleSong(song1)
        runCurrent()

        // Emit error on song 1 to capture position
        playerErrorFlow.emit("Error del servidor de audio (HTTP 403)")
        runCurrent()

        // User changes song to song 2
        viewModel.playSingleSong(song2)
        runCurrent()

        // Verify seekTo(5000L) was never called for song_2
        coVerify(exactly = 0) { mockPlayerRepo.seekTo(5000L) }
    }
}
