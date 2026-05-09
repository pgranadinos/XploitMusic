package com.auramusic.app.domain.usecase

import app.cash.turbine.test
import com.auramusic.app.data.local.dao.PlaylistDao
import com.auramusic.app.data.local.dao.SongDao
import com.auramusic.app.data.remote.SpotifyArtistDto
import com.auramusic.app.data.remote.SpotifyPlaylistDto
import com.auramusic.app.data.remote.SpotifyTrackDto
import com.auramusic.app.data.repository.SpotifyMatchingEngine
import com.auramusic.app.domain.model.Song
import com.auramusic.app.domain.provider.ProviderGateway
import com.auramusic.app.domain.repository.SpotifyRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ImportPlaylistUseCaseTest {

    private lateinit var spotifyRepository: SpotifyRepository
    private lateinit var providerGateway: ProviderGateway
    private lateinit var matchingEngine: SpotifyMatchingEngine
    private lateinit var playlistDao: PlaylistDao
    private lateinit var songDao: SongDao

    private lateinit var importPlaylistUseCase: ImportPlaylistUseCase

    @Before
    fun setup() {
        spotifyRepository = mockk()
        providerGateway = mockk()
        matchingEngine = SpotifyMatchingEngine() // Usaremos el real para evaluar los scores reales
        playlistDao = mockk(relaxed = true)
        songDao = mockk(relaxed = true)

        importPlaylistUseCase = ImportPlaylistUseCase(
            spotifyRepository = spotifyRepository,
            gateway = providerGateway,
            matchingEngine = matchingEngine,
            playlistDao = playlistDao,
            songDao = songDao
        )
    }

    @Test
    fun `execute emits complete flow evaluating Exact Match and No Match tracking`() = runTest {
        // 1. Prepare Spotify Payload
        val playlistDto = SpotifyPlaylistDto(
            id = "pl_1",
            name = "Test Mix",
            images = null,
            tracks = com.auramusic.app.data.remote.SpotifyTracksDto(total = 2)
        )
        
        val track1 = SpotifyTrackDto(
            id = "t_1",
            name = "Blinding Lights",
            durationMs = 200_000,
            artists = listOf(SpotifyArtistDto("1", "The Weeknd")),
            album = null
        )
        val track2 = SpotifyTrackDto(
            id = "t_2",
            name = "Cancion Inexistente",
            durationMs = 150_000,
            artists = listOf(SpotifyArtistDto("2", "Ghost Artist")),
            album = null
        )

        coEvery { spotifyRepository.getAllPlaylistTracks(playlistDto.id) } returns Result.success(listOf(track1, track2))

        // 2. Prepare Provider Gateway Candidates response
        // Para "Blinding Lights The Weeknd", retornamos un candidato exacto
        val exactCandidate = Song("yt_1", "Blinding Lights (Official Audio)", "The Weeknd", "", 202, "")
        coEvery { providerGateway.search(match { it.contains("Blinding Lights") }) } returns Result.success(listOf(exactCandidate))

        // Para Cancion Inexistente, simulamos que el provider no encuentra nada util (vacío o basura fallida)
        coEvery { providerGateway.search(match { it.contains("Cancion Inexistente") }) } returns Result.success(emptyList())

        // 3. Mock Database Persistence ID
        coEvery { playlistDao.createPlaylist(any()) } returns 99L

        // 4. Assert with Turbine Test (Flow)
        importPlaylistUseCase.execute(playlistDto).test {
            
            // First emit: Track 1 (Matched Exact)
            val firstProgress = awaitItem()
            assertThat(firstProgress.processedTracks).isEqualTo(1)
            assertThat(firstProgress.isComplete).isFalse()
            assertThat(firstProgress.latestStatus).isInstanceOf(TrackImportStatus.Matched::class.java)
            val status1 = firstProgress.latestStatus as TrackImportStatus.Matched
            assertThat(status1.matchType).isEqualTo(SpotifyMatchingEngine.MatchType.EXACT)
            assertThat(status1.song.id).isEqualTo("yt_1")

            // Second emit: Track 2 (No Match)
            val secondProgress = awaitItem()
            assertThat(secondProgress.processedTracks).isEqualTo(2)
            assertThat(secondProgress.isComplete).isFalse()
            assertThat(secondProgress.latestStatus).isInstanceOf(TrackImportStatus.NoMatch::class.java)

            // Third emit: Complete Event
            val finalSignal = awaitItem()
            assertThat(finalSignal.processedTracks).isEqualTo(2)
            assertThat(finalSignal.isComplete).isTrue()
            assertThat(finalSignal.createdPlaylistId).isEqualTo(99L)

            awaitComplete()
        }

        // 5. Verify Persistence was requested for the ONLY matched track
        coVerify(exactly = 1) { playlistDao.createPlaylist(any()) }
        coVerify(exactly = 1) { songDao.upsertNetworkSong(any()) } // Track 1 saved
        coVerify(exactly = 1) { playlistDao.insertPlaylistSongCrossRef(any()) } // Track 1 mapped
    }

    @Test
    fun `execute exits gracefully on Spotify API failure`() = runTest {
        val playlistDto = SpotifyPlaylistDto(id = "pl_error", name = "Error Mix", tracks = com.auramusic.app.data.remote.SpotifyTracksDto(1))

        coEvery { spotifyRepository.getAllPlaylistTracks(any()) } returns Result.failure(Exception("Network error"))

        importPlaylistUseCase.execute(playlistDto).test {
            val shutdownProgress = awaitItem()
            assertThat(shutdownProgress.isComplete).isTrue()
            assertThat(shutdownProgress.processedTracks).isEqualTo(0)
            awaitComplete()
        }

        // Must not touch Room
        coVerify(exactly = 0) { playlistDao.createPlaylist(any()) }
    }
}
