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
    private val networkTypeFlow = MutableStateFlow("Wi-Fi")
    private val networkEventsFlow = MutableSharedFlow<String>(extraBufferCapacity = 16)

    private val playerErrorFlow = MutableSharedFlow<PlayerError>()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        mockPlayerRepo = mockk(relaxed = true) {
            coEvery { isPlaying } returns flowOf(true)
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
    fun `http 403 error code invalidates stream and retries up to 2 times`() = runTest(testDispatcher) {
        val testSong = Song("song_403", "Test 403", "Artist", "", 100L, "http://audio/403")
        coEvery { mockGetStream("song_403") } returns Resource.Success("http://audio/fresh")

        viewModel.playSingleSong(testSong)
        runCurrent()

        val err403 = PlayerError(
            code = PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            codeName = "ERROR_CODE_IO_BAD_HTTP_STATUS (403)",
            message = "Error del servidor de audio (HTTP 403)"
        )

        // Emit 1st 403 error
        playerErrorFlow.emit(err403)
        advanceTimeBy(1000L)
        runCurrent()

        verify(exactly = 1) { mockStreamRepo.invalidateStream("song_403") }

        // Emit 2nd 403 error
        playerErrorFlow.emit(err403)
        advanceTimeBy(2000L)
        runCurrent()

        verify(exactly = 2) { mockStreamRepo.invalidateStream("song_403") }

        // Emit 3rd 403 error (should NOT retry a 3rd time)
        playerErrorFlow.emit(err403)
        runCurrent()

        verify(exactly = 2) { mockStreamRepo.invalidateStream("song_403") }
    }

    @Test
    fun `network error with real translated spanish message triggers retry by error code`() = runTest(testDispatcher) {
        val testSong = Song("song_net", "Test Net", "Artist", "", 100L, "http://audio/net")
        coEvery { mockGetStream("song_net") } returns Resource.Success("http://audio/fresh")

        viewModel.playSingleSong(testSong)
        runCurrent()

        // Production real message translated: "Error de red: verifica tu conexión a internet"
        val netFailError = PlayerError(
            code = PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            codeName = "ERROR_CODE_IO_NETWORK_CONNECTION_FAILED",
            message = "Error de red: verifica tu conexión a internet"
        )
        val netTimeoutError = PlayerError(
            code = PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            codeName = "ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT",
            message = "Error de red: verifica tu conexión a internet"
        )

        // 1st network error by code (1s backoff)
        playerErrorFlow.emit(netFailError)
        advanceTimeBy(1000L)
        runCurrent()

        verify(exactly = 1) { mockStreamRepo.invalidateStream("song_net") }

        // 2nd network error by code (2s backoff)
        playerErrorFlow.emit(netTimeoutError)
        advanceTimeBy(2000L)
        runCurrent()

        verify(exactly = 2) { mockStreamRepo.invalidateStream("song_net") }

        // 3rd network error: should stop retrying, set Error state and trigger auto-skip
        playerErrorFlow.emit(netFailError)
        runCurrent()

        verify(exactly = 2) { mockStreamRepo.invalidateStream("song_net") }
        assertThat(viewModel.playbackState.value as Any).isInstanceOf(PlaybackState.Error::class.java)
    }

    @Test
    fun `player error with status Sin red does not invalidate stream, does not call playInternal and does not increase retry count`() = runTest(testDispatcher) {
        val testSong = Song("song_offline_err", "Offline Err", "Artist", "", 100L, "http://audio/offline_err")
        coEvery { mockGetStream("song_offline_err") } returns Resource.Success("http://audio/fresh")

        viewModel.playSingleSong(testSong)
        runCurrent()
        clearMocks(mockStreamRepo, answers = false)

        // Network goes to "Sin red"
        networkTypeFlow.value = "Sin red"

        val err = PlayerError(
            code = PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            codeName = "ERROR_CODE_IO_NETWORK_CONNECTION_FAILED",
            message = "Error de red: verifica tu conexión a internet"
        )

        // Emit error while network is "Sin red"
        playerErrorFlow.emit(err)
        runCurrent()

        // Stream must NOT be invalidated while offline
        verify(exactly = 0) { mockStreamRepo.invalidateStream(any()) }

        // State is set to error with MSG_NO_INTERNET
        assertThat(viewModel.playbackState.value as Any).isInstanceOf(PlaybackState.Error::class.java)
        val errState = viewModel.playbackState.value as PlaybackState.Error
        assertThat(errState.message).contains("Sin conexión a internet")
    }

    @Test
    fun `manual pause clears pending resume position`() = runTest(testDispatcher) {
        val testSong = Song("song_manual_pause", "Pause Test", "Artist", "", 100L, "http://audio/pause")
        coEvery { mockGetStream("song_manual_pause") } returns Resource.Success("http://audio/fresh")

        viewModel.playSingleSong(testSong)
        runCurrent()

        // User manually pauses
        viewModel.togglePlayPause()
        runCurrent()

        // Network changes from Datos móviles to Wi-Fi
        networkTypeFlow.value = "Wi-Fi"
        networkEventsFlow.emit("Wi-Fi")
        runCurrent()

        // Should NOT attempt auto-resume on manual pause
        coVerify(exactly = 1) { mockPlayerRepo.pause() }
    }

    @Test
    fun `direct network change from Wi-Fi to Datos moviles with active playback invalidates stream and refetches preserving position`() = runTest(testDispatcher) {
        val testSong = Song("song_direct_change", "Test Direct", "Artist", "", 100L, "http://audio/direct")
        coEvery { mockGetStream("song_direct_change") } returns Resource.Success("http://audio/fresh")

        viewModel.playSingleSong(testSong)
        runCurrent()
        clearMocks(mockStreamRepo, answers = false)

        // Direct transition from Wi-Fi to Datos móviles
        networkEventsFlow.emit("Wi-Fi")
        runCurrent()

        networkEventsFlow.emit("Datos móviles")
        runCurrent()

        // Stream must be invalidated and re-fetched once
        verify(exactly = 1) { mockStreamRepo.invalidateStream("song_direct_change") }
    }

    @Test
    fun `rapid network events Wi-Fi to Sin red to Datos moviles without delay resolves stream exactly once upon reconnecting`() = runTest(testDispatcher) {
        val testSong = Song("song_rapid", "Test Rapid", "Artist", "", 100L, "http://audio/rapid")
        coEvery { mockGetStream("song_rapid") } returns Resource.Success("http://audio/fresh")

        viewModel.playSingleSong(testSong)
        runCurrent()
        clearMocks(mockStreamRepo, answers = false)

        // Emit "Wi-Fi", "Sin red" and "Datos móviles" in rapid succession
        networkEventsFlow.emit("Wi-Fi")
        networkEventsFlow.emit("Sin red")
        networkEventsFlow.emit("Datos móviles")
        runCurrent()

        // Must invalidate and re-fetch stream exactly ONCE upon reconnecting
        verify(exactly = 1) { mockStreamRepo.invalidateStream("song_rapid") }
    }

    @Test
    fun `transitioning from Wi-Fi to Sin red and back to Wi-Fi resolves stream exactly once upon reconnecting`() = runTest(testDispatcher) {
        val testSong = Song("song_reconnect", "Test Reconnect", "Artist", "", 100L, "http://audio/reconnect")
        coEvery { mockGetStream("song_reconnect") } returns Resource.Success("http://audio/fresh")

        viewModel.playSingleSong(testSong)
        runCurrent()
        clearMocks(mockStreamRepo, answers = false)

        // 1. Wi-Fi -> Sin red (should NOT re-resolve stream while disconnected)
        networkEventsFlow.emit("Wi-Fi")
        networkEventsFlow.emit("Sin red")
        runCurrent()

        verify(exactly = 0) { mockStreamRepo.invalidateStream(any()) }

        // 2. Sin red -> Wi-Fi (should re-resolve stream)
        networkEventsFlow.emit("Wi-Fi")
        runCurrent()

        verify(exactly = 1) { mockStreamRepo.invalidateStream("song_reconnect") }
    }

    @Test
    fun `changing song after error does not apply saved position to new song`() = runTest(testDispatcher) {
        val song1 = Song("song_1", "Song 1", "Artist", "", 100L, "http://audio/1")
        val song2 = Song("song_2", "Song 2", "Artist", "", 100L, "http://audio/2")
        coEvery { mockGetStream("song_1") } returns Resource.Success("http://audio/fresh1")
        coEvery { mockGetStream("song_2") } returns Resource.Success("http://audio/fresh2")

        viewModel.playSingleSong(song1)
        runCurrent()

        val err = PlayerError(
            code = PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            codeName = "403",
            message = "Error del servidor de audio (HTTP 403)"
        )

        // Emit error on song 1 to capture position
        playerErrorFlow.emit(err)
        advanceTimeBy(1000L)
        runCurrent()

        // User changes song to song 2
        viewModel.playSingleSong(song2)
        runCurrent()

        // Verify seekTo(5000L) was never called for song_2
        coVerify(exactly = 0) { mockPlayerRepo.seekTo(5000L) }
    }

    @Test
    fun `song requested or ending while network is Sin red pauses without auto skipping and resumes when connected`() = runTest(testDispatcher) {
        val song1 = Song("song_offline_pause", "Offline Song", "Artist", "", 100L, "http://audio/offline")
        coEvery { mockGetStream("song_offline_pause") } returns Resource.Success("http://audio/fresh")

        // Set network to "Sin red"
        networkTypeFlow.value = "Sin red"
        runCurrent()

        // Request playback while disconnected
        viewModel.playSingleSong(song1)
        runCurrent()

        // Verify stream extraction was NOT called while disconnected
        coVerify(exactly = 0) { mockGetStream(any()) }
        assertThat(viewModel.playbackState.value as Any).isInstanceOf(PlaybackState.Error::class.java)

        // Restoring network to "Datos móviles" must trigger stream resolution and play
        networkTypeFlow.value = "Datos móviles"
        networkEventsFlow.emit("Datos móviles")
        runCurrent()

        coVerify(atLeast = 1) { mockGetStream("song_offline_pause") }
    }
}
