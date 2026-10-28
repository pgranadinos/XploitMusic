package com.xploitmusic.app.presentation.player

import androidx.media3.common.PlaybackException
import com.xploitmusic.app.core.util.PlaybackLogManager
import com.xploitmusic.app.core.util.Resource
import com.xploitmusic.app.data.repository.OfflineManager
import com.xploitmusic.app.data.repository.QueueManagerImpl
import com.xploitmusic.app.data.service.SkipHandler
import com.xploitmusic.app.domain.model.PlayerError
import com.xploitmusic.app.domain.model.Song
import com.xploitmusic.app.domain.repository.FavoritesRepository
import com.xploitmusic.app.domain.repository.PlayerRepository
import com.xploitmusic.app.domain.repository.PlaylistRepository
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
class PlayerViewModelSongSyncTest {

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
    private val playerErrorFlow = MutableSharedFlow<PlayerError>()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        mockPlayerRepo = mockk(relaxed = true) {
            every { isPlaying } returns flowOf(true)
            every { currentPosition } returns MutableStateFlow(5000L)
            every { duration } returns flowOf(100000L)
            every { trackEnded } returns MutableSharedFlow<Unit>()
            every { mediaItemTransition } returns flowOf(null)
            every { playerError } returns playerErrorFlow
            coEvery { hasNextMediaItem() } returns false
        }

        mockStreamRepo = mockk(relaxed = true) {
            coEvery { getStreamInfo(any()) } returns Resource.Success("http://audio/fresh")
        }
        mockGetStream = GetAudioStreamUseCase(mockStreamRepo)
        mockGetLyrics = mockk(relaxed = true)
        mockToggleFav = mockk(relaxed = true)
        mockFavRepo = mockk(relaxed = true)
        mockSkipHandler = mockk(relaxed = true)
        val mockPlaylistRepo: PlaylistRepository = mockk(relaxed = true)
        queueManager = QueueManagerImpl()

        mockOfflineManager = mockk(relaxed = true)
        coEvery { mockOfflineManager.getOfflineFilePath(any()) } returns null

        mockPlaybackLogManager = mockk(relaxed = true) {
            coEvery { currentNetworkType } returns MutableStateFlow("Wi-Fi")
            coEvery { networkEvents } returns MutableSharedFlow()
        }

        val mockAnalyticsLogger: com.xploitmusic.app.core.analytics.XploitAnalyticsLogger = mockk(relaxed = true)

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
    fun `cuando reproductor reporta B y ViewModel tiene A, error de red invalida B y registra DESINCRONIZADO`() = runTest(testDispatcher) {
        val songA = Song("song_A", "Song A", "Artist", "", 100L, "http://audio/A")
        val songB = Song("song_B", "Song B", "Artist", "", 100L, "http://audio/B")

        viewModel.playSingleSong(songA)
        runCurrent()

        queueManager.setQueue(listOf(songA, songB), 0)

        // Player currentMediaId reports "song_B", desynced from ViewModel's song_A
        coEvery { mockPlayerRepo.currentMediaId() } returns "song_B"

        clearMocks(mockStreamRepo, answers = false)

        val netErr = PlayerError(
            code = PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            codeName = "ERROR_CODE_IO_NETWORK_CONNECTION_FAILED",
            message = "Error de red"
        )
        playerErrorFlow.emit(netErr)
        runCurrent()

        verify { mockPlaybackLogManager.log("DESINCRONIZADO", "WARN", "player=song_B vm=song_A") }
        verify(atLeast = 1) { mockStreamRepo.invalidateStream("song_B") }
        verify(exactly = 0) { mockStreamRepo.invalidateStream("song_A") }
    }
}
