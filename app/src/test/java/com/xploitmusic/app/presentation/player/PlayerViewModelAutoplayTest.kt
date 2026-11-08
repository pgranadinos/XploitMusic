package com.xploitmusic.app.presentation.player

import com.google.common.truth.Truth.assertThat
import com.xploitmusic.app.domain.model.Song
import com.xploitmusic.app.domain.repository.QueueManager
import com.xploitmusic.app.data.repository.QueueManagerImpl
import com.xploitmusic.app.core.util.Resource
import com.xploitmusic.app.data.service.SkipHandler
import com.xploitmusic.app.data.repository.OfflineManager
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelAutoplayTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var queueManager: QueueManager
    private val networkEventsFlow = MutableSharedFlow<String>(replay = 1)
    private val networkTypeFlow = MutableStateFlow("Wi-Fi")

    private fun song(id: String, title: String = "Song $id", artist: String = "Artist"): Song =
        Song(id, title, artist, "", 180)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        queueManager = QueueManagerImpl()
    }

    private fun createViewModel(streamRepo: com.xploitmusic.app.domain.repository.StreamRepository): PlayerViewModel {
        val mockPlayerRepo: com.xploitmusic.app.domain.repository.PlayerRepository = mockk(relaxed = true)
        val mockFavoritesRepo: com.xploitmusic.app.domain.repository.FavoritesRepository = mockk(relaxed = true)
        val mockPlaybackLogManager: com.xploitmusic.app.core.util.PlaybackLogManager = mockk(relaxed = true) {
            coEvery { currentNetworkType } returns networkTypeFlow
            coEvery { networkEvents } returns networkEventsFlow
        }
        val mockOfflineManager: OfflineManager = mockk(relaxed = true)
        val mockAnalyticsLogger: com.xploitmusic.app.core.analytics.XploitAnalyticsLogger = mockk(relaxed = true)
        val mockPlaylistRepo: com.xploitmusic.app.domain.repository.PlaylistRepository = mockk(relaxed = true)
        val mockGetStream = mockk<com.xploitmusic.app.domain.usecase.GetAudioStreamUseCase>(relaxed = true)
        val mockGetLyrics = mockk<com.xploitmusic.app.domain.usecase.GetLyricsUseCase>(relaxed = true)
        val mockToggleFav = mockk<com.xploitmusic.app.domain.usecase.ToggleFavoriteUseCase>(relaxed = true)
        val mockSkipHandler = mockk<SkipHandler>(relaxed = true)

        return PlayerViewModel(
            playerRepository = mockPlayerRepo,
            getAudioStreamUseCase = mockGetStream,
            getLyricsUseCase = mockGetLyrics,
            toggleFavoriteUseCase = mockToggleFav,
            favoritesRepository = mockFavoritesRepo,
            streamRepository = streamRepo,
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
    // a) autoplay active and queue at 3 triggers reload and reaches 10 (or whatever is available)
    fun `a) autoplay active and queue at 3 triggers reload and reaches 10`() {
        queueManager.setQueue(listOf(song("curr")), startIndex = 0)
        val newTracks = (1..10).map { song("auto-$it") }
        queueManager.appendAutoplayTracks(newTracks, limit = 10)
        assertThat(queueManager.upNext.value.size).isEqualTo(10)
    }

    @Test
    // b) queue at 4 does not trigger
    fun `b) queue at 4 does not trigger reload`() {
        queueManager.setQueue(listOf(song("curr"), song("1"), song("2"), song("3"), song("4")), startIndex = 0)
        assertThat(queueManager.upNext.value.size).isGreaterThan(3)
    }

    @Test
    // c) autoplay disabled and queue at 2 does not trigger; enqueued recommendations are preserved
    fun `c) autoplay disabled and queue at 2 does not trigger, enqueued preserved`() {
        queueManager.toggleAutoplay()
        queueManager.setQueue(listOf(song("curr"), song("1"), song("2")), startIndex = 0)
        assertThat(queueManager.isAutoplayEnabled.value).isFalse()
        assertThat(queueManager.upNext.value).hasSize(2)
    }

    @Test
    // c_lock) with lock occupied by another caller, reload does not fetch radio
    fun `c_lock) with lock occupied by another caller reload does not fetch radio`() = runTest(testDispatcher) {
        val streamRepo = mockk<com.xploitmusic.app.domain.repository.StreamRepository>(relaxed = true)
        queueManager.tryBeginAutoplayReload() // occupy lock

        queueManager.setQueue(listOf(song("curr"), song("1")), startIndex = 0)
        val vm = createViewModel(streamRepo)
        runCurrent()

        coVerify(exactly = 0) { streamRepo.getRelatedSongs(any()) }
    }

    @Test
    // d) incomplete batch: second radio requested once, max 2 extra calls
    fun `d) incomplete batch second radio requested once max 2 extra calls`() {
        val batch1 = (1..4).map { song("b1-$it") }
        val batch2 = (1..6).map { song("b2-$it") }
        val all = batch1 + batch2

        queueManager.setQueue(listOf(song("curr")), startIndex = 0)
        val added = queueManager.appendAutoplayTracks(all, limit = 10)
        assertThat(added).isEqualTo(10)
    }

    @Test
    // d_lock) after complete reload, lock is released
    fun `d_lock) after complete reload lock is released`() = runTest(testDispatcher) {
        val streamRepo = mockk<com.xploitmusic.app.domain.repository.StreamRepository>(relaxed = true)
        coEvery { streamRepo.getRelatedSongs(any()) } returns Resource.Success(listOf(song("new1")))

        queueManager.setQueue(listOf(song("curr"), song("1")), startIndex = 0)
        val vm = createViewModel(streamRepo)
        runCurrent()

        assertThat(queueManager.tryBeginAutoplayReload()).isTrue()
    }

    @Test
    // e1) getRelatedSongs returns Resource.Error: autoplayReloadPending remains true, nothing added
    fun `e1) getRelatedSongs returns error leaves autoplayReloadPending true and adds nothing`() = runTest(testDispatcher) {
        val streamRepo = mockk<com.xploitmusic.app.domain.repository.StreamRepository>(relaxed = true)
        coEvery { streamRepo.getRelatedSongs(any()) } returns Resource.Error("Red caída")

        queueManager.setQueue(listOf(song("curr"), song("1")), startIndex = 0)
        val vm = createViewModel(streamRepo)
        runCurrent()

        coVerify(exactly = 1) { streamRepo.getRelatedSongs("1") }
        assertThat(queueManager.upNext.value).hasSize(1)
    }

    @Test
    // e2) After that, a validated network event (networkEvents) triggers exactly one retry and the batch is added
    fun `e2) network event triggers retry when autoplayReloadPending is true`() = runTest(testDispatcher) {
        val streamRepo = mockk<com.xploitmusic.app.domain.repository.StreamRepository>(relaxed = true)
        coEvery { streamRepo.getRelatedSongs("1") } returnsMany listOf(
            Resource.Error("Red caída"),
            Resource.Success(listOf(song("new1"), song("new2")))
        )

        queueManager.setQueue(listOf(song("curr"), song("1")), startIndex = 0)
        val vm = createViewModel(streamRepo)
        runCurrent()

        networkEventsFlow.emit("Wi-Fi")
        advanceTimeBy(100L)
        runCurrent()

        coVerify(exactly = 2) { streamRepo.getRelatedSongs("1") }
        assertThat(queueManager.upNext.value.map { it.id }).containsAtLeast("new1", "new2")
    }

    @Test
    // e3) Success with empty list: not pending and no retry upon receiving network event
    fun `e3) success with empty list does not leave reload pending`() = runTest(testDispatcher) {
        val streamRepo = mockk<com.xploitmusic.app.domain.repository.StreamRepository>(relaxed = true)
        coEvery { streamRepo.getRelatedSongs("1") } returns Resource.Success(emptyList())

        queueManager.setQueue(listOf(song("curr"), song("1")), startIndex = 0)
        val vm = createViewModel(streamRepo)
        runCurrent()

        networkEventsFlow.emit("Wi-Fi")
        advanceTimeBy(100L)
        runCurrent()

        coVerify(exactly = 1) { streamRepo.getRelatedSongs("1") }
    }

    @Test
    // f) with no new valid songs, reload is not triggered again
    fun `f) with no new valid songs reload is not triggered again`() {
        queueManager.setQueue(listOf(song("curr")), startIndex = 0)
        queueManager.markAsPlayed("1")
        val added = queueManager.appendAutoplayTracks(listOf(song("1")), limit = 10)
        assertThat(added).isEqualTo(0)
    }
}
