package com.auramusic.app.domain.usecase

import app.cash.turbine.test
import com.auramusic.app.data.remote.SpotifyAlbumDto
import com.auramusic.app.data.remote.SpotifyArtistDto
import com.auramusic.app.data.remote.SpotifyPlaylistDto
import com.auramusic.app.data.remote.SpotifyTrackDto
import com.auramusic.app.data.remote.SpotifyTracksSummaryDto
import com.auramusic.app.data.repository.SpotifyMatchingEngine
import com.auramusic.app.data.repository.SpotifyPlaylistPersister
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
    private lateinit var persister: SpotifyPlaylistPersister

    private lateinit var importPlaylistUseCase: ImportPlaylistUseCase

    @Before
    fun setup() {
        spotifyRepository = mockk()
        providerGateway = mockk()
        matchingEngine = SpotifyMatchingEngine() // Usaremos el real para evaluar los scores reales
        persister = mockk(relaxed = true)

        importPlaylistUseCase = ImportPlaylistUseCase(
            spotifyRepository = spotifyRepository,
            gateway = providerGateway,
            matchingEngine = matchingEngine,
            persister = persister
        )
    }

    @Test
    fun `execute emits complete flow evaluating Exact Match and No Match tracking`() = runTest {
        // 1. Prepare Spotify Payload
        val playlistDto = SpotifyPlaylistDto(
            id = "pl_1",
            name = "Test Mix",
            description = null,
            images = null,
            tracks = SpotifyTracksSummaryDto(total = 2)
        )
        
        val track1 = SpotifyTrackDto(
            id = "t_1",
            name = "Blinding Lights",
            durationMs = 200_000,
            artists = listOf(SpotifyArtistDto("1", "The Weeknd")),
            album = SpotifyAlbumDto(name = "After Hours", images = null)
        )
        val track2 = SpotifyTrackDto(
            id = "t_2",
            name = "Cancion Inexistente",
            durationMs = 150_000,
            artists = listOf(SpotifyArtistDto("2", "Ghost Artist")),
            album = SpotifyAlbumDto(name = "Unknown", images = null)
        )

        coEvery { spotifyRepository.getAllPlaylistTracks(playlistDto.id) } returns Result.success(listOf(track1, track2))

        // 2. Prepare Provider Gateway Candidates response
        // Para "Blinding Lights The Weeknd", retornamos un candidato exacto
        val exactCandidate = Song("yt_1", "Blinding Lights (Official Audio)", "The Weeknd", "", 202, "")
        coEvery { providerGateway.search(match { it.contains("Blinding Lights") }) } returns Result.success(listOf(exactCandidate))

        // Para Cancion Inexistente, simulamos que el provider no encuentra nada util (vacío o basura fallida)
        coEvery { providerGateway.search(match { it.contains("Cancion Inexistente") }) } returns Result.success(emptyList())

        // 3. Mock Database Persistence ID
        coEvery { persister.persist(any(), any()) } returns 99L

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
        coVerify(exactly = 1) {
            persister.persist(playlistDto, match { songs -> songs.map { it.id } == listOf("yt_1") })
        }
    }

    @Test
    fun `execute exits gracefully on Spotify API failure`() = runTest {
        val playlistDto = SpotifyPlaylistDto(
            id = "pl_error",
            name = "Error Mix",
            description = null,
            images = null,
            tracks = SpotifyTracksSummaryDto(total = 1)
        )

        coEvery { spotifyRepository.getAllPlaylistTracks(any()) } returns Result.failure(Exception("Network error"))

        importPlaylistUseCase.execute(playlistDto).test {
            val shutdownProgress = awaitItem()
            assertThat(shutdownProgress.isComplete).isFalse()
            assertThat(shutdownProgress.processedTracks).isEqualTo(0)
            assertThat(shutdownProgress.errorMessage).contains("Network error")
            awaitComplete()
        }

        // Must not touch Room
        coVerify(exactly = 0) { persister.persist(any(), any()) }
    }

    @Test
    fun `execute reports provider failure instead of treating it as no match`() = runTest {
        val playlist = SpotifyPlaylistDto(
            id = "pl_provider_error",
            name = "Provider Error",
            description = null,
            images = null,
            tracks = SpotifyTracksSummaryDto(total = 1)
        )
        val track = SpotifyTrackDto(
            id = "t_error",
            name = "Unavailable Song",
            durationMs = 180_000,
            artists = listOf(SpotifyArtistDto("1", "Artist")),
            album = SpotifyAlbumDto("Album", null)
        )
        coEvery { spotifyRepository.getAllPlaylistTracks(playlist.id) } returns
            Result.success(listOf(track))
        coEvery { providerGateway.search(any()) } returns Result.failure(Exception("Provider offline"))

        importPlaylistUseCase.execute(playlist).test {
            val trackProgress = awaitItem()
            assertThat(trackProgress.latestStatus).isInstanceOf(TrackImportStatus.Failed::class.java)
            assertThat(trackProgress.failedTracks).isEqualTo(1)

            val terminalProgress = awaitItem()
            assertThat(terminalProgress.errorMessage).contains("Revisa la conexión")
            assertThat(terminalProgress.isComplete).isFalse()
            awaitComplete()
        }

        coVerify(exactly = 0) { persister.persist(any(), any()) }
    }

    @Test
    fun `execute does not persist low confidence candidate as a song`() = runTest {
        val playlist = SpotifyPlaylistDto(
            id = "pl_low",
            name = "Low Confidence",
            description = null,
            images = null,
            tracks = SpotifyTracksSummaryDto(total = 1)
        )
        val track = SpotifyTrackDto(
            id = "t_low",
            name = "Blinding Lights",
            durationMs = 200_000,
            artists = listOf(SpotifyArtistDto("1", "The Weeknd")),
            album = SpotifyAlbumDto("After Hours", null)
        )
        val weakCandidate = Song("yt_weak", "Blinding Night", "Random Channel", "", 200, "")
        coEvery { spotifyRepository.getAllPlaylistTracks(playlist.id) } returns
            Result.success(listOf(track))
        coEvery { providerGateway.search(any()) } returns Result.success(listOf(weakCandidate))
        coEvery { persister.persist(any(), any()) } returns 77L

        importPlaylistUseCase.execute(playlist).test {
            val trackProgress = awaitItem()
            assertThat(trackProgress.latestStatus).isInstanceOf(TrackImportStatus.LowConfidence::class.java)
            assertThat(trackProgress.skippedTracks).isEqualTo(1)
            val complete = awaitItem()
            assertThat(complete.isComplete).isTrue()
            awaitComplete()
        }

        coVerify(exactly = 1) { persister.persist(playlist, emptyList()) }
    }
}
