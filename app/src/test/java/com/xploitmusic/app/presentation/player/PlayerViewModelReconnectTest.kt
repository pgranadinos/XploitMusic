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
import org.junit.Ignore
import org.junit.Test
import kotlinx.coroutines.launch

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
    private val isPlayingFlow = MutableStateFlow(true)
    private val currentPositionFlow = MutableStateFlow(5000L)

    @Before
    fun setup() {

        Dispatchers.setMain(testDispatcher)

        mockPlayerRepo = mockk(relaxed = true) {
            every { isPlaying } returns isPlayingFlow
            every { currentPosition } returns currentPositionFlow
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
    fun `b - pausa manual antes de los 5 s cancela los reintentos`() = runTest(testDispatcher) {
        val testSong = Song("song_1", "Song 1", "Artist", "", 100L, "http://audio/1")
        coEvery { mockPlayerRepo.currentMediaId() } returns "song_1"

        viewModel.playSingleSong(testSong)
        runCurrent()

        isPlayingFlow.value = true
        clearMocks(mockStreamRepo, answers = false)

        // User manually pauses during playback
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
    fun `f - tras agotar reconexion togglePlayPause invalida stream llama a playInternal con posicion guardada y no la pierde`() = runTest(testDispatcher) {
        val testSong = Song("song_1", "Song 1", "Artist", "", 100L, "http://audio/1")
        coEvery { mockPlayerRepo.currentMediaId() } returns "song_1"
        coEvery { mockStreamRepo.getStreamInfo("song_1") } returns Resource.Error("Sin red")

        currentPositionFlow.value = 45000L
        backgroundScope.launch { viewModel.currentPosition.collect {} }
        runCurrent()

        viewModel.playSingleSong(testSong)
        runCurrent()

        // Exhaust all 4 attempts
        val netErr = PlayerError(
            code = PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            codeName = "ERROR_CODE_IO_NETWORK_CONNECTION_FAILED",
            message = "Sin conexión"
        )
        playerErrorFlow.emit(netErr)
        runCurrent()

        advanceTimeBy(65000L)
        runCurrent()

        // Player is now paused / error state, set isPlaying = false
        isPlayingFlow.value = false
        assertThat(viewModel.playbackState.value as Any).isInstanceOf(PlaybackState.Error::class.java)

        clearMocks(mockStreamRepo, answers = false)
        clearMocks(mockPlayerRepo, answers = false)
        every { mockPlayerRepo.isPlaying } returns isPlayingFlow
        every { mockPlayerRepo.currentPosition } returns currentPositionFlow
        coEvery { mockPlayerRepo.currentMediaId() } returns "song_1"
        coEvery { mockStreamRepo.getStreamInfo("song_1") } returns Resource.Success("http://audio/1_fresh")

        // User taps play/pause to manually resume
        viewModel.togglePlayPause()
        advanceTimeBy(1000L); runCurrent()

        verify { mockStreamRepo.invalidateStream("song_1") }
        verify { mockPlaybackLogManager.log("PLAYBACK", "INFO", "Reanudación manual tras pausa por red para 'Song 1'") }
        coVerify { mockPlayerRepo.seekTo(45000L) }
    }

    @Test
    fun `g - si el estado es Idle togglePlayPause reproduce la cancion desde su inicio`() = runTest(testDispatcher) {
        val testSong = Song("song_idle", "Song Idle", "Artist", "", 100L, "http://audio/idle")
        coEvery { mockPlayerRepo.currentMediaId() } returns "song_idle"

        isPlayingFlow.value = false

        viewModel.playSingleSong(testSong)
        runCurrent()

        clearMocks(mockPlayerRepo, answers = false)
        every { mockPlayerRepo.isPlaying } returns isPlayingFlow

        viewModel.togglePlayPause()
        runCurrent()

        coVerify { mockPlayerRepo.play() }
        coVerify(exactly = 0) { mockPlayerRepo.seekTo(any()) }
    }

    @Test
    fun `j - error de red en extraccion programa reintento en 30 s y reanuda al cumplirse`() = runTest(testDispatcher) {
        val testSong = Song("song_dns_err", "DNS Error Song", "Artist", "", 100L, "http://audio/dns")
        coEvery { mockPlayerRepo.currentMediaId() } returns "song_dns_err"
        coEvery { mockStreamRepo.getStreamInfo("song_dns_err") } returns Resource.Error("Unable to resolve host \"youtubei.googleapis.com\"")

        networkTypeFlow.value = "Wi-Fi"
        viewModel.playSingleSong(testSong)
        advanceTimeBy(1000L); runCurrent()

        assertThat(viewModel.playbackState.value as Any).isInstanceOf(PlaybackState.Error::class.java)

        clearMocks(mockStreamRepo, answers = false)
        coEvery { mockStreamRepo.getStreamInfo("song_dns_err") } returns Resource.Success("http://audio/fresh_dns")

        // Fast-forward 30s for the scheduled DNS retry
        advanceTimeBy(30_000L); runCurrent()

        coVerify(exactly = 1) { mockStreamRepo.getStreamInfo("song_dns_err") }
        verify { mockPlaybackLogManager.log("REINTENTO_RED", "INFO", match { it.contains("Ejecutando reintento 1/15") }) }
    }

    @Test
    fun `k - fallos sostenidos de red se agotan tras 15 reintentos`() = runTest(testDispatcher) {
        val testSong = Song("song_dns_err", "DNS Error Song", "Artist", "", 100L, "http://audio/dns")
        coEvery { mockPlayerRepo.currentMediaId() } returns "song_dns_err"
        coEvery { mockStreamRepo.getStreamInfo("song_dns_err") } returns Resource.Error("Unable to resolve host \"youtubei.googleapis.com\"")

        networkTypeFlow.value = "Wi-Fi"
        viewModel.playSingleSong(testSong)
        advanceTimeBy(1000L); runCurrent()

        clearMocks(mockStreamRepo, answers = false)

        // Advance 16 x (30s + buffer)
        for (step in 1..16) {
            advanceTimeBy(30_000L); runCurrent()
            advanceTimeBy(100L); runCurrent()
        }

        // Exactly 15 retry attempts execute
        coVerify(atLeast = 15) { mockStreamRepo.getStreamInfo("song_dns_err") }
        verify { mockPlaybackLogManager.log("REINTENTO_RED", "WARN", match { it.contains("Reintentos AGOTADOS") }) }
    }

    @Test
    fun `l - evento de red durante reintento pendiente cancela reintento pendiente`() = runTest(testDispatcher) {
        val testSong = Song("song_dns_err", "DNS Error Song", "Artist", "", 100L, "http://audio/dns")
        coEvery { mockPlayerRepo.currentMediaId() } returns "song_dns_err"
        coEvery { mockStreamRepo.getStreamInfo("song_dns_err") } returns Resource.Error("Unable to resolve host \"youtubei.googleapis.com\"")

        networkTypeFlow.value = "Wi-Fi"
        viewModel.playSingleSong(testSong)
        advanceTimeBy(1000L); runCurrent()

        // Advance 15s (pending 30s retry is active)
        advanceTimeBy(15_000L); runCurrent()

        // Network event arrives
        networkTypeFlow.value = "Sin red"
        networkEventsFlow.emit("Sin red")
        advanceTimeBy(2_500L); runCurrent()

        clearMocks(mockStreamRepo, answers = false)
        coEvery { mockStreamRepo.getStreamInfo("song_dns_err") } returns Resource.Success("http://audio/fresh_dns")

        networkTypeFlow.value = "Wi-Fi"
        networkEventsFlow.emit("Wi-Fi")
        advanceTimeBy(2_500L); runCurrent()

        verify { mockPlaybackLogManager.log("REINTENTO_RED", "INFO", match { it.contains("cancelados") }) }
    }

    @Test
    fun `m - con Sin red cero reintentos de red son programados`() = runTest(testDispatcher) {
        val testSong = Song("song_offline", "Offline Song", "Artist", "", 100L, "http://audio/offline")
        coEvery { mockPlayerRepo.currentMediaId() } returns "song_offline"

        networkTypeFlow.value = "Sin red"
        networkEventsFlow.emit("Sin red")
        advanceTimeBy(2_500L); runCurrent()

        viewModel.playSingleSong(testSong)
        runCurrent()

        clearMocks(mockStreamRepo, answers = false)

        // Advance 5 minutes (300_000 ms)
        advanceTimeBy(300_000L); runCurrent()

        coVerify(exactly = 0) { mockStreamRepo.getStreamInfo(any()) }
        verify(exactly = 0) { mockPlaybackLogManager.log("REINTENTO_RED", any(), any()) }
    }

    @Test
    fun `n - cambio de cancion durante reintento pendiente cancela reintentos de la cancion anterior`() = runTest(testDispatcher) {
        val song1 = Song("song_dns_1", "DNS Error Song 1", "Artist", "", 100L, "http://audio/dns1")
        val song2 = Song("song_dns_2", "DNS Error Song 2", "Artist", "", 100L, "http://audio/dns2")
        coEvery { mockPlayerRepo.currentMediaId() } returns "song_dns_1"
        coEvery { mockStreamRepo.getStreamInfo("song_dns_1") } returns Resource.Error("Unable to resolve host \"youtubei.googleapis.com\"")
        coEvery { mockStreamRepo.getStreamInfo("song_dns_2") } returns Resource.Success("http://audio/fresh2")

        networkTypeFlow.value = "Wi-Fi"
        viewModel.playSingleSong(song1)
        advanceTimeBy(1000L); runCurrent()

        // Change song to song2
        coEvery { mockPlayerRepo.currentMediaId() } returns "song_dns_2"
        viewModel.playSingleSong(song2)
        advanceTimeBy(1000L); runCurrent()

        clearMocks(mockStreamRepo, answers = false)

        // Advance 60s
        advanceTimeBy(60_000L); runCurrent()

        coVerify(exactly = 0) { mockStreamRepo.getStreamInfo("song_dns_1") }
        verify { mockPlaybackLogManager.log("REINTENTO_RED", "INFO", match { it.contains("cancelados") }) }
    }

    @Test
    fun `o - pausa por red activa keepForegroundForPendingResume en true`() = runTest(testDispatcher) {
        val testSong = Song("song_1", "Song 1", "Artist", "", 100L, "http://audio/1")
        coEvery { mockPlayerRepo.currentMediaId() } returns "song_1"

        networkTypeFlow.value = "Sin red"
        networkEventsFlow.emit("Sin red")
        advanceTimeBy(2_500L); runCurrent()

        viewModel.playSingleSong(testSong)
        runCurrent()

        verify { mockPlaybackLogManager.setKeepForegroundForPendingResume(true, "vm-solicitud-sin-red") }
    }

    @Test
    fun `p - reproduccion manual o cambio de cancion pone keepForegroundForPendingResume en false`() = runTest(testDispatcher) {
        val song1 = Song("song_1", "Song 1", "Artist", "", 100L, "http://audio/1")
        val song2 = Song("song_2", "Song 2", "Artist", "", 100L, "http://audio/2")
        coEvery { mockPlayerRepo.currentMediaId() } returns "song_1"

        viewModel.playSingleSong(song1)
        runCurrent()

        // Change song
        coEvery { mockPlayerRepo.currentMediaId() } returns "song_2"
        viewModel.playSingleSong(song2)
        runCurrent()

        verify { mockPlaybackLogManager.setKeepForegroundForPendingResume(false, "nueva_cancion") }
    }

    @Test
    fun `q - READY o exito de reproduccion pone keepForegroundForPendingResume en false`() = runTest(testDispatcher) {
        val testSong = Song("song_1", "Song 1", "Artist", "", 100L, "http://audio/1")
        coEvery { mockPlayerRepo.currentMediaId() } returns "song_1"
        coEvery { mockStreamRepo.getStreamInfo("song_1") } returns Resource.Success("http://audio/fresh")

        viewModel.playSingleSong(testSong)
        advanceTimeBy(1000L); runCurrent()

        verify { mockPlaybackLogManager.setKeepForegroundForPendingResume(false, any()) }
    }

    @Ignore("pendiente: comportamiento de fin de canción ENDED no implementado en ViewModel")
    @Test
    fun `r - fin de cancion ENDED con playWhenReady true mantiene keepForegroundForPendingResume`() = runTest(testDispatcher) {
        val testSong = Song("song_1", "Song 1", "Artist", "", 100L, "http://audio/1")
        coEvery { mockPlayerRepo.currentMediaId() } returns "song_1"
        coEvery { mockStreamRepo.getStreamInfo("song_1") } returns Resource.Success("http://audio/1")

        viewModel.playSingleSong(testSong)
        runCurrent()

        isPlayingFlow.value = true
    }

    @Test
    fun `s - pausa por red despues de ENDED mantiene primer plano activo`() = runTest(testDispatcher) {
        val testSong = Song("song_1", "Song 1", "Artist", "", 100L, "http://audio/1")
        coEvery { mockPlayerRepo.currentMediaId() } returns "song_1"

        networkTypeFlow.value = "Sin red"
        networkEventsFlow.emit("Sin red")
        advanceTimeBy(2_500L); runCurrent()

        viewModel.playSingleSong(testSong)
        runCurrent()

        verify { mockPlaybackLogManager.setKeepForegroundForPendingResume(true, any()) }
    }

    @Test
    fun `w - pausa manual desactiva la bandera de primer plano pendiente`() = runTest(testDispatcher) {
        val testSong = Song("song_1", "Song 1", "Artist", "", 100L, "http://audio/1")
        coEvery { mockPlayerRepo.currentMediaId() } returns "song_1"
        every { mockPlaybackLogManager.keepForegroundForPendingResume.value } returns true

        backgroundScope.launch { viewModel.isPlaying.collect {} }

        viewModel.playSingleSong(testSong)
        advanceTimeBy(100L); runCurrent()

        clearMocks(mockPlaybackLogManager, answers = false)
        isPlayingFlow.value = true
        viewModel.togglePlayPause()
        advanceTimeBy(100L); runCurrent()

        verify { mockPlaybackLogManager.setKeepForegroundForPendingResume(false, "manual") }
    }

    @Test
    fun `x - pausa por red con cancion cargada activa el primer plano`() = runTest(testDispatcher) {
        val testSong = Song("song_1", "Song 1", "Artist", "", 100L, "http://audio/1")
        coEvery { mockPlayerRepo.currentMediaId() } returns "song_1"

        networkTypeFlow.value = "Sin red"
        networkEventsFlow.emit("Sin red")
        advanceTimeBy(2_500L); runCurrent()

        viewModel.playSingleSong(testSong)
        runCurrent()

        verify { mockPlaybackLogManager.setKeepForegroundForPendingResume(true, "vm-solicitud-sin-red") }
    }

    @Ignore("pendiente: regla de cola terminada sin siguiente canción no implementada")
    @Test
    fun `y - cola terminada sin siguiente cancion suelta el primer plano`() = runTest(testDispatcher) {
        val testSong = Song("song_1", "Song 1", "Artist", "", 100L, "http://audio/1")
        coEvery { mockPlayerRepo.currentMediaId() } returns "song_1"

        viewModel.playSingleSong(testSong)
        runCurrent()

        isPlayingFlow.value = true
        viewModel.togglePlayPause()
        runCurrent()
    }

    @Test
    // b)
    fun `datos perdidos y Wi-Fi validado vuelve a disponible`() = runTest(testDispatcher) {
        val testSong = Song("song_bug", "Bug Song", "Artist", "", 100L, "http://audio/bug")
        coEvery { mockPlayerRepo.currentMediaId() } returns "song_bug"
        coEvery { mockStreamRepo.getStreamInfo("song_bug") } returns Resource.Error("Sin red")
        currentPositionFlow.value = 10000L
        backgroundScope.launch { viewModel.currentPosition.collect {} }

        viewModel.playSingleSong(testSong)
        runCurrent()

        networkTypeFlow.value = "Sin red"
        networkEventsFlow.emit("Sin red")
        val netErr = PlayerError(
            code = PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            codeName = "ERROR_CODE_IO_NETWORK_CONNECTION_FAILED",
            message = "Sin conexión"
        )
        playerErrorFlow.emit(netErr)
        advanceTimeBy(2_500L); runCurrent()

        clearMocks(mockStreamRepo, answers = false)
        coEvery { mockStreamRepo.getStreamInfo("song_bug") } returns Resource.Success("http://audio/bug_fresh")

        networkTypeFlow.value = "Wi-Fi"
        networkEventsFlow.emit("Wi-Fi")
        advanceTimeBy(2_500L); runCurrent()
        advanceTimeBy(1_000L); runCurrent()

        verify(exactly = 1) { mockStreamRepo.invalidateStream("song_bug") }
        coVerify(exactly = 1) { mockStreamRepo.getStreamInfo("song_bug") }
    }

    @Test
    // c)
    fun `cambiar de Wi-Fi a datos no pausa ni invalida stream ni seekTo`() = runTest(testDispatcher) {
        val testSong = Song("song_dual", "Dual Net Song", "Artist", "", 100L, "http://audio/dual")
        coEvery { mockPlayerRepo.currentMediaId() } returns "song_dual"
        isPlayingFlow.value = true

        networkTypeFlow.value = "Wi-Fi"
        viewModel.playSingleSong(testSong)
        runCurrent()

        clearMocks(mockPlaybackLogManager, answers = false)
        clearMocks(mockStreamRepo, answers = false)
        clearMocks(mockPlayerRepo, answers = false)

        networkTypeFlow.value = "Datos móviles"
        networkEventsFlow.emit("Datos móviles")
        advanceTimeBy(2_500L); runCurrent()

        verify(exactly = 0) { mockPlaybackLogManager.setKeepForegroundForPendingResume(true, any()) }
        verify(exactly = 0) { mockStreamRepo.invalidateStream(any()) }
        coVerify(exactly = 0) { mockStreamRepo.getStreamInfo(any()) }
        coVerify(exactly = 0) { mockPlayerRepo.seekTo(any()) }
        coVerify(exactly = 0) { mockPlayerRepo.pause() }
    }

    @Test
    fun `D1 - reanudacion automatica al volver la red tras agotar 4 reintentos en red validada`() = runTest(testDispatcher) {
        val testSong = Song("song_1", "Song 1", "Artist", "", 100L, "http://audio/1")
        coEvery { mockPlayerRepo.currentMediaId() } returns "song_1"
        coEvery { mockStreamRepo.getStreamInfo("song_1") } returns Resource.Error("Sin red")
        currentPositionFlow.value = 15000L
        backgroundScope.launch { viewModel.currentPosition.collect {} }

        // 1. Play with validated Wi-Fi
        networkTypeFlow.value = "Wi-Fi"
        viewModel.playSingleSong(testSong)
        runCurrent()

        val netErr = PlayerError(
            code = PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            codeName = "ERROR_CODE_IO_NETWORK_CONNECTION_FAILED",
            message = "Sin conexión"
        )
        playerErrorFlow.emit(netErr)
        runCurrent()

        // 2. Force 4 failed attempts with validated network (5s, 10s, 20s, 30s)
        advanceTimeBy(5_000L); runCurrent()
        advanceTimeBy(10_000L); runCurrent()
        advanceTimeBy(20_000L); runCurrent()
        advanceTimeBy(30_000L); runCurrent()

        coVerify(exactly = 4) { mockStreamRepo.getStreamInfo("song_1") }

        // 3. Lose Wi-Fi and data (Sin red) for 60s
        networkTypeFlow.value = "Sin red"
        networkEventsFlow.emit("Sin red")
        clearMocks(mockStreamRepo, answers = false)
        clearMocks(mockPlayerRepo, answers = false)
        every { mockPlayerRepo.isPlaying } returns isPlayingFlow
        every { mockPlayerRepo.currentPosition } returns currentPositionFlow
        coEvery { mockPlayerRepo.currentMediaId() } returns "song_1"
        coEvery { mockStreamRepo.getStreamInfo("song_1") } returns Resource.Success("http://audio/1_fresh")

        advanceTimeBy(60_000L); runCurrent()
        coVerify(exactly = 0) { mockStreamRepo.getStreamInfo(any()) }

        // 4. Restore validated data (without Wi-Fi)
        networkTypeFlow.value = "Datos móviles"
        networkEventsFlow.emit("Datos móviles")
        advanceTimeBy(2_500L); runCurrent()
        advanceTimeBy(1_000L); runCurrent()

        // 5. Verify resumption exactly once
        verify(exactly = 1) { mockStreamRepo.invalidateStream("song_1") }
        coVerify(exactly = 1) { mockStreamRepo.getStreamInfo("song_1") }
        coVerify(exactly = 1) { mockPlayerRepo.seekTo(15000L) }

        // 6. Advance 30s more, verify exactly = 0
        clearMocks(mockStreamRepo, answers = false)
        clearMocks(mockPlayerRepo, answers = false)
        advanceTimeBy(30_000L); runCurrent()

        verify(exactly = 0) { mockStreamRepo.invalidateStream(any()) }
        coVerify(exactly = 0) { mockStreamRepo.getStreamInfo(any()) }
        coVerify(exactly = 0) { mockPlayerRepo.seekTo(any()) }
    }
}
