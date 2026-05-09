package com.auramusic.app.data.repository

import com.auramusic.app.core.util.Resource
import com.auramusic.app.data.remote.*
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import com.auramusic.app.domain.provider.ProviderGateway

class StreamRepositoryImplTest {

    private lateinit var api: AuraBffApi
    private lateinit var gateway: ProviderGateway
    private lateinit var repository: StreamRepositoryImpl

    @Before
    fun setUp() {
        api = mockk(relaxed = true)
        gateway = mockk(relaxed = true)
        val analyticsLogger: com.auramusic.app.core.analytics.AuraAnalyticsLogger = mockk(relaxed = true)
        repository = StreamRepositoryImpl(api, gateway, analyticsLogger)
    }

    // ═══════════════════════════════════════════════════════════════════
    // searchSongs
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `searchSongs maps response to Song list`() = runTest {
        coEvery { api.search("rock", type = "tracks") } returns AuraSearchResponse(
            query = "rock",
            results = listOf(
                AuraSearchItem(id = "1", title = "Song A", type = "track", thumbnailUrl = "url1", artist = "Artist A", durationSeconds = 200),
                AuraSearchItem(id = "2", title = "Song B", type = "track", thumbnailUrl = "url2", artist = "Artist B", durationSeconds = 300)
            )
        )

        val result = repository.searchSongs("rock")

        assertThat(result).isInstanceOf(Resource.Success::class.java)
        val songs = (result as Resource.Success).data!!
        assertThat(songs).hasSize(2)
        assertThat(songs[0].id).isEqualTo("1")
        assertThat(songs[0].title).isEqualTo("Song A")
        assertThat(songs[0].artist).isEqualTo("Artist A")
        assertThat(songs[0].durationSeconds).isEqualTo(200L)
        assertThat(songs[1].id).isEqualTo("2")
    }

    @Test
    fun `searchSongs with null artist defaults to Unknown`() = runTest {
        coEvery { api.search("test", type = "tracks") } returns AuraSearchResponse(
            query = "test",
            results = listOf(
                AuraSearchItem(id = "1", title = "Song", type = "track", thumbnailUrl = "url", artist = null, durationSeconds = 100)
            )
        )

        val result = repository.searchSongs("test")

        assertThat(result).isInstanceOf(Resource.Success::class.java)
        assertThat((result as Resource.Success).data!![0].artist).isEqualTo("Unknown")
    }

    @Test
    fun `searchSongs returns error on exception`() = runTest {
        coEvery { api.search(any(), type = any()) } throws RuntimeException("Network error")

        val result = repository.searchSongs("test")

        assertThat(result).isInstanceOf(Resource.Error::class.java)
        assertThat((result as Resource.Error).message).contains("Network error")
    }

    // ═══════════════════════════════════════════════════════════════════
    // getStreamInfo
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `getStreamInfo returns streamUrl on success`() = runTest {
        coEvery { api.getStream("abc") } returns AuraStreamResponse(
            streamUrl = "https://cdn.example.com/audio.mp3",
            format = "mp3",
            expireAt = 9999999999L
        )

        val result = repository.getStreamInfo("abc")

        assertThat(result).isInstanceOf(Resource.Success::class.java)
        assertThat((result as Resource.Success).data).isEqualTo("https://cdn.example.com/audio.mp3")
    }

    @Test
    fun `getStreamInfo returns error on exception`() = runTest {
        coEvery { api.getStream(any()) } throws RuntimeException("Server down")

        val result = repository.getStreamInfo("abc")

        assertThat(result).isInstanceOf(Resource.Error::class.java)
    }

    // ═══════════════════════════════════════════════════════════════════
    // getRelatedSongs
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `getRelatedSongs returns BFF tracks when available`() = runTest {
        val relatedTracks = listOf(
            AuraTrack(id = "r1", title = "Related 1", artist = "Art", durationSeconds = 180, thumbnailUrl = "url1"),
            AuraTrack(id = "r2", title = "Related 2", artist = "Art", durationSeconds = 200, thumbnailUrl = "url2"),
            AuraTrack(id = "r3", title = "Related 3", artist = "Art", durationSeconds = 220, thumbnailUrl = "url3"),
            AuraTrack(id = "r4", title = "Related 4", artist = "Art", durationSeconds = 240, thumbnailUrl = "url4"),
            AuraTrack(id = "r5", title = "Related 5", artist = "Art", durationSeconds = 260, thumbnailUrl = "url5")
        )
        coEvery { api.getRelatedTracks("seed", limit = 20) } returns AuraRelatedResponse(
            trackId = "seed",
            items = relatedTracks
        )

        val result = repository.getRelatedSongs("seed")

        assertThat(result).isInstanceOf(Resource.Success::class.java)
        val songs = (result as Resource.Success).data!!
        assertThat(songs).hasSize(5)
        val ids = songs.map { it.id }
        assertThat(ids).doesNotContain("seed") // seed is excluded
    }

    @Test
    fun `getRelatedSongs falls back to search when BFF returns 404`() = runTest {
        coEvery { api.getRelatedTracks(any(), limit = any()) } throws RuntimeException("404")
        coEvery { api.search(any(), type = "tracks") } returns AuraSearchResponse(
            query = "test",
            results = listOf(
                AuraSearchItem(id = "s1", title = "Search Result", type = "track", thumbnailUrl = "url", artist = "Artist", durationSeconds = 180)
            )
        )
        // Discovery fallback (getHomeFeed) — not needed if search gives ≥ 5
        // We add enough search results by matching the "mix" search
        coEvery { api.search("seed mix", type = "tracks") } returns AuraSearchResponse(
            query = "seed mix",
            results = (1..6).map {
                AuraSearchItem(id = "s$it", title = "Song $it", type = "track", thumbnailUrl = "url$it", artist = "Art$it", durationSeconds = 180)
            }
        )

        val result = repository.getRelatedSongs("seed")

        assertThat(result).isInstanceOf(Resource.Success::class.java)
        assertThat((result as Resource.Success).data!!).isNotEmpty()
    }

    @Test
    fun `getRelatedSongs removes seed song from results`() = runTest {
        coEvery { api.getRelatedTracks("seed", limit = 20) } returns AuraRelatedResponse(
            trackId = "seed",
            items = listOf(
                AuraTrack(id = "seed", title = "The Seed", artist = "Art", durationSeconds = 180, thumbnailUrl = "url"), // should be removed
                AuraTrack(id = "other1", title = "Other 1", artist = "Art", durationSeconds = 200, thumbnailUrl = "url"),
                AuraTrack(id = "other2", title = "Other 2", artist = "Art", durationSeconds = 200, thumbnailUrl = "url"),
                AuraTrack(id = "other3", title = "Other 3", artist = "Art", durationSeconds = 200, thumbnailUrl = "url"),
                AuraTrack(id = "other4", title = "Other 4", artist = "Art", durationSeconds = 200, thumbnailUrl = "url"),
                AuraTrack(id = "other5", title = "Other 5", artist = "Art", durationSeconds = 200, thumbnailUrl = "url")
            )
        )

        val result = repository.getRelatedSongs("seed")

        assertThat(result).isInstanceOf(Resource.Success::class.java)
        val ids = (result as Resource.Success).data!!.map { it.id }
        assertThat(ids).doesNotContain("seed")
    }

    @Test
    fun `getRelatedSongs deduplicates results`() = runTest {
        coEvery { api.getRelatedTracks("seed", limit = 20) } returns AuraRelatedResponse(
            trackId = "seed",
            items = listOf(
                AuraTrack(id = "dup", title = "Dup Song", artist = "Art", durationSeconds = 180, thumbnailUrl = "url"),
                AuraTrack(id = "dup", title = "Dup Song", artist = "Art", durationSeconds = 180, thumbnailUrl = "url"),
                AuraTrack(id = "unique1", title = "Unique 1", artist = "Art", durationSeconds = 200, thumbnailUrl = "url"),
                AuraTrack(id = "unique2", title = "Unique 2", artist = "Art", durationSeconds = 200, thumbnailUrl = "url"),
                AuraTrack(id = "unique3", title = "Unique 3", artist = "Art", durationSeconds = 200, thumbnailUrl = "url"),
                AuraTrack(id = "unique4", title = "Unique 4", artist = "Art", durationSeconds = 200, thumbnailUrl = "url")
            )
        )

        val result = repository.getRelatedSongs("seed")

        assertThat(result).isInstanceOf(Resource.Success::class.java)
        val ids = (result as Resource.Success).data!!.map { it.id }
        assertThat(ids).containsNoDuplicates()
    }

    @Test
    fun `getRelatedSongs returns error when all strategies fail`() = runTest {
        coEvery { api.getRelatedTracks(any(), limit = any()) } throws RuntimeException("BFF down")
        coEvery { api.search(any(), type = any()) } throws RuntimeException("Search down")
        coEvery { api.getHomeFeed() } throws RuntimeException("Feed down")

        val result = repository.getRelatedSongs("seed")

        assertThat(result).isInstanceOf(Resource.Error::class.java)
    }

    // ═══════════════════════════════════════════════════════════════════
    // recordTelemetryPlay
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `recordTelemetryPlay calls API silently`() = runTest {
        coEvery { api.recordPlayEvent(any()) } returns Unit

        repository.recordTelemetryPlay("song123")

        coVerify { api.recordPlayEvent(AuraPlayEventRequest(trackId = "song123", source = "android_app")) }
    }

    @Test
    fun `recordTelemetryPlay swallows exceptions`() = runTest {
        coEvery { api.recordPlayEvent(any()) } throws RuntimeException("Timeout")

        // Should not throw
        repository.recordTelemetryPlay("song123")
    }
}
