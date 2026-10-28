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
    private val mediaItemTransitionFlow = MutableSharedFlow<String?>()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        mockPlayerRepo = mockk(relaxed = true) {
            every { isPlaying } returns flowOf(true)
            every { currentPosition } returns MutableStateFlow(5000L)
            every { duration } returns flowOf(100000L)
            every { trackEnded } returns MutableSharedFlow<Unit>()
            every { mediaItemTransition } returns mediaItemTransitionFlow
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
    fun `A - reproduccion normal transicion a la siguiente cancion actualiza currentSong`() = runTest(testDispatcher) {
        val song1 = Song("song_1", "Song 1", "Artist", "", 100L, "http://audio/1")
        val song2 = Song("song_2", "Song 2", "Artist", "", 100L, "http://audio/2")
        coEvery { mockStreamRepo.getStreamInfo("song_1") } returns Resource.Success("http://audio/1_fresh")
        coEvery { mockStreamRepo.getStreamInfo("song_2") } returns Resource.Success("http://audio/2_fresh")

        viewModel.playSong(song1, listOf(song1, song2))
        advanceUntilIdle()

        assertThat(viewModel.currentSong.value?.id).isEqualTo("song_1")

        // ExoPlayer transitions to song_2
        coEvery { mockPlayerRepo.currentMediaId() } returns "song_2"
        mediaItemTransitionFlow.emit("song_2")
        advanceUntilIdle()

        assertThat(viewModel.currentSong.value?.id).isEqualTo("song_2")
        verify { mockPlaybackLogManager.log("TRANSICION_VM", "INFO", match { it.contains("mediaId='song_2'") && it.contains("encontrada=si") }) }
    }

    @Test
    fun `B - tras reconexion una transicion a la cancion siguiente precargada actualiza currentSong`() = runTest(testDispatcher) {
        val song1 = Song("song_1", "Song 1", "Artist", "", 100L, "http://audio/1")
        val song2 = Song("song_2", "Song 2", "Artist", "", 100L, "http://audio/2")
        coEvery { mockStreamRepo.getStreamInfo("song_1") } returns Resource.Success("http://audio/1_fresh")
        coEvery { mockStreamRepo.getStreamInfo("song_2") } returns Resource.Success("http://audio/2_fresh")

        // Play queue so song2 gets pre-fetched into songsInPlayer
        viewModel.playSong(song1, listOf(song1, song2))
        advanceUntilIdle()

        // Simulate reconnection / re-fetch on song1 where _prefetchedNextSong would be overwritten in old logic
        coEvery { mockPlayerRepo.currentMediaId() } returns "song_1"
        mediaItemTransitionFlow.emit("song_1")
        advanceUntilIdle()

        assertThat(viewModel.currentSong.value?.id).isEqualTo("song_1")

        // Now ExoPlayer transitions to song2 which was recorded in songsInPlayer
        coEvery { mockPlayerRepo.currentMediaId() } returns "song_2"
        mediaItemTransitionFlow.emit("song_2")
        advanceUntilIdle()

        assertThat(viewModel.currentSong.value?.id).isEqualTo("song_2")
        verify { mockPlaybackLogManager.log("TRANSICION_VM", "INFO", match { it.contains("mediaId='song_2'") && it.contains("encontrada=si") }) }
    }

    @Test
    fun `C - transicion a un mediaId desconocido NO cambia currentSong y registra WARN DESCONOCIDA`() = runTest(testDispatcher) {
        val song1 = Song("song_1", "Song 1", "Artist", "", 100L, "http://audio/1")
        coEvery { mockStreamRepo.getStreamInfo("song_1") } returns Resource.Success("http://audio/1_fresh")

        viewModel.playSingleSong(song1)
        advanceUntilIdle()

        assertThat(viewModel.currentSong.value?.id).isEqualTo("song_1")

        // ExoPlayer emits an unknown mediaId
        coEvery { mockPlayerRepo.currentMediaId() } returns "unknown_999"
        mediaItemTransitionFlow.emit("unknown_999")
        advanceUntilIdle()

        // currentSong remains song_1
        assertThat(viewModel.currentSong.value?.id).isEqualTo("song_1")
        verify { mockPlaybackLogManager.log("TRANSICION_VM", "WARN", "DESCONOCIDA: mediaId='unknown_999'") }
    }
}
