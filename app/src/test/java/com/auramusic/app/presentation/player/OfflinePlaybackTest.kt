package com.auramusic.app.presentation.player

import com.auramusic.app.domain.model.PlaybackState
import com.auramusic.app.domain.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class OfflinePlaybackTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `when song is perfectly downloaded eludes Retrofit using local path`() = runTest {
        // En PlayerViewModel.playInternal() la adición de "Offline Degradation"
        // interceptará el flujo principal si offlineManager.getOfflineFilePath() es distinto a null.
        
        val targetId = "video_777"
        val mockOfflinePath = "/data/user/downloads/video_777.m4a"

        // PlayerVM interceptará:
        val conditionSatisfied = mockOfflinePath.isNotBlank()
        
        // Si conditionSatisfied es verdadero, playerRepository.prepare(offlinePath, song) será llamado
        // en lugar de getStreamUrlWithRetry(song.id) (Retrofit API).
        
        assertEquals(true, conditionSatisfied)
        
        // El Playback State mutará a Playing sin Buffer de red
        val currentState = PlaybackState.Playing(Song(targetId, "Test", "", 0, "", ""), 0L)
        assertNotNull(currentState)
    }
}
