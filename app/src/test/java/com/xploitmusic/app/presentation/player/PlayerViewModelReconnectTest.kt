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
import com.xploitmusic.app.domain.model.PlaybackState
import com.google.common.truth.Truth.assertThat
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
class PlayerViewModelReconnectTest {

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
    private val networkTypeFlow = MutableStateFlow("Wi-Fi")
    private val networkEventsFlow = MutableSharedFlow<String>(extraBufferCapacity = 16)
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
            coEvery { currentMediaId() } returns "song_1"
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
            coEvery { currentNetworkType } returns networkTypeFlow
            coEvery { networkEvents } returns networkEventsFlow
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
    fun `a - tras Sin red del reproductor avanza 5 s e invalida una vez y reanuda`() = runTest(testDispatcher) {
        val testSong = Song("song_1", "Song 1", "Artist", "", 100L, "http://audio/1")
        coEvery { mockPlayerRepo.currentMediaId() } returns "song_1"

        viewModel.playSingleSong(testSong)
        runCurrent()
        clearMocks(mockStreamRepo, answers = false)

        // Set status to "Sin red" and emit player error
        networkTypeFlow.value = "Sin red"
        val netErr = PlayerError(
            code = PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            codeName = "ERROR_CODE_IO_NETWORK_CONNECTION_FAILED",
            message = "Sin conexión"
        )
        playerErrorFlow.emit(netErr)
        runCurrent()

        verify { mockPlaybackLogManager.log("PAUSA", "WARN", "origen=vm-sin-red") }

        // Advance 5s for 1st reconnect attempt
        advanceTimeBy(5000L)
        runCurrent()

        verify(atLeast = 1) { mockStreamRepo.invalidateStream("song_1") }
        verify { mockPlaybackLogManager.log("RECONEXION", "INFO", "Intento 1/4 para 'Song 1'") }
    }

    @Test
    fun `b - pausa manual antes de los 5 s cancela los reintentos`() = runTest(testDispatcher) {
        val testSong = Song("song_1", "Song 1", "Artist", "", 100L, "http://audio/1")
        coEvery { mockPlayerRepo.currentMediaId() } returns "song_1"

        viewModel.playSingleSong(testSong)
        runCurrent()

        // Trigger network error (starts reconnect timer)
        networkTypeFlow.value = "Sin red"
        val netErr = PlayerError(
            code = PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            codeName = "ERROR_CODE_IO_NETWORK_CONNECTION_FAILED",
            message = "Sin conexión"
        )
        playerErrorFlow.emit(netErr)
        runCurrent()

        clearMocks(mockStreamRepo, answers = false)

        // User manually pauses
        viewModel.togglePlayPause()
        runCurrent()

        // Advance 30s
        advanceTimeBy(30000L)
        runCurrent()

        verify(exactly = 0) { mockStreamRepo.invalidateStream(any()) }
    }

    @Test
    fun `c - cambio de cancion antes de los 5 s cancela los reintentos de la anterior`() = runTest(testDispatcher) {
        val song1 = Song("song_1", "Song 1", "Artist", "", 100L, "http://audio/1")
        val song2 = Song("song_2", "Song 2", "Artist", "", 100L, "http://audio/2")
        coEvery { mockPlayerRepo.currentMediaId() } returns "song_1"

        viewModel.playSingleSong(song1)
        runCurrent()

        networkTypeFlow.value = "Sin red"
        val netErr = PlayerError(
            code = PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            codeName = "ERROR_CODE_IO_NETWORK_CONNECTION_FAILED",
            message = "Sin conexión"
        )
        playerErrorFlow.emit(netErr)
        runCurrent()

        clearMocks(mockStreamRepo, answers = false)

        // Change song to song2
        coEvery { mockPlayerRepo.currentMediaId() } returns "song_2"
        viewModel.playSingleSong(song2)
        runCurrent()

        // Advance 30s
        advanceTimeBy(30000L)
        runCurrent()

        verify(exactly = 0) { mockStreamRepo.invalidateStream("song_1") }
    }

    @Test
    fun `d - cuatro intentos fallidos registran error y no mas de 4 invalidaciones`() = runTest(testDispatcher) {
        val testSong = Song("song_1", "Song 1", "Artist", "", 100L, "http://audio/1")
        coEvery { mockPlayerRepo.currentMediaId() } returns "song_1"
        coEvery { mockStreamRepo.getStreamInfo("song_1") } returns Resource.Error("Sin red")

        viewModel.playSingleSong(testSong)
        runCurrent()

        networkTypeFlow.value = "Sin red"
        val netErr = PlayerError(
            code = PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            codeName = "ERROR_CODE_IO_NETWORK_CONNECTION_FAILED",
            message = "Sin conexión"
        )
        playerErrorFlow.emit(netErr)
        runCurrent()

        clearMocks(mockStreamRepo, answers = false)

        // Advance 65s (5s + 10s + 20s + 30s)
        advanceTimeBy(65000L)
        runCurrent()

        verify(exactly = 4) { mockStreamRepo.invalidateStream("song_1") }
        verify { mockPlaybackLogManager.log("RECONEXION", "ERROR", "Sin reconexión tras 4 intentos") }
    }

    @Test
    fun `e - reanudacion por networkEvents antes del primer intento cancela reconnectJob y realiza una sola invalidacion`() = runTest(testDispatcher) {
        val testSong = Song("song_1", "Song 1", "Artist", "", 100L, "http://audio/1")
        coEvery { mockPlayerRepo.currentMediaId() } returns "song_1"

        viewModel.playSingleSong(testSong)
        runCurrent()

        // Go offline
        networkTypeFlow.value = "Sin red"
        val netErr = PlayerError(
            code = PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            codeName = "ERROR_CODE_IO_NETWORK_CONNECTION_FAILED",
            message = "Sin conexión"
        )
        playerErrorFlow.emit(netErr)
        runCurrent()

        clearMocks(mockStreamRepo, answers = false)

        // At 2s (before 5s reconnect timer), network events restores Wi-Fi
        advanceTimeBy(2000L)
        networkTypeFlow.value = "Wi-Fi"
        networkEventsFlow.emit("Wi-Fi")
        advanceTimeBy(3000L)
        runCurrent()

        // Verify exactly 1 invalidation from networkEvents and zero from reconnectJob
        verify(exactly = 1) { mockStreamRepo.invalidateStream("song_1") }

        // Advance 30s more to confirm reconnectJob was cancelled
        clearMocks(mockStreamRepo, answers = false)
        advanceTimeBy(30000L)
        runCurrent()

        verify(exactly = 0) { mockStreamRepo.invalidateStream(any()) }
    }
}
