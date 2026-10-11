package com.xploitmusic.app.data.repository

import com.xploitmusic.app.core.util.Resource
import com.xploitmusic.app.data.remote.*
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import com.xploitmusic.app.domain.provider.ProviderGateway
import com.xploitmusic.app.domain.model.Song
import com.xploitmusic.app.domain.model.ChartEntry
import com.xploitmusic.app.domain.model.CountryChart
import com.xploitmusic.app.domain.model.LatamCountry
import com.xploitmusic.app.data.provider.youtubemusic.YouTubeMusicClient
import kotlinx.coroutines.CancellationException

class StreamRepositoryImplTest {

    private lateinit var gateway: ProviderGateway
    private lateinit var repository: StreamRepositoryImpl
    private lateinit var youtubeMusicClient: YouTubeMusicClient

    @Before
    fun setUp() {
        gateway = mockk(relaxed = true)
        val analyticsLogger: com.xploitmusic.app.core.analytics.XploitAnalyticsLogger = mockk(relaxed = true)
        youtubeMusicClient = mockk()
        repository = StreamRepositoryImpl(gateway, analyticsLogger, youtubeMusicClient)
    }

    @Test
    fun `latam partial result preserves source positions and reports missing countries`() = runTest {
        val chart = CountryChart(LatamCountry.MX, "Top México", "PLchart", listOf(
            ChartEntry(1, Song("first", "First", "Artist", "", 180)),
            ChartEntry(3, Song("third", "Third", "Artist", "", 180)),
        ), 123L)
        coEvery { youtubeMusicClient.getCountryChart(any()) } throws java.io.IOException("Unavailable")
        coEvery { youtubeMusicClient.getCountryChart(LatamCountry.MX) } returns chart

        val result = repository.getLatamCharts().data!!
        assertThat(result.charts).containsExactly(chart)
        assertThat(result.entries.map { it.positions.single().rank }).containsExactly(1, 3).inOrder()
        assertThat(result.unavailableCountries).containsExactlyElementsIn(LatamCountry.values().filter { it != LatamCountry.MX })
        coVerify(exactly = 0) { gateway.search(any()) }
        coVerify(exactly = 0) { gateway.getTrending(any()) }
    }

    @Test
    fun `latam failure does not fabricate results from another source`() = runTest {
        coEvery { youtubeMusicClient.getCountryChart(any()) } throws java.io.IOException("Unavailable")

        assertThat(repository.getLatamCharts()).isInstanceOf(Resource.Error::class.java)
        coVerify(exactly = 0) { gateway.search(any()) }
        coVerify(exactly = 0) { gateway.getTrending(any()) }
    }

    @Test(expected = CancellationException::class)
    fun `latam propagates cancellation`() = runTest {
        coEvery { youtubeMusicClient.getCountryChart(any()) } throws CancellationException("Cancelled")
        repository.getLatamCharts()
    }

    @Test
    fun `latam caches successful results and refresh bypasses cache`() = runTest {
        coEvery { youtubeMusicClient.getCountryChart(any()) } answers {
            val country = firstArg<LatamCountry>()
            CountryChart(country, "Top ${country.label}", "PL${country.name}", listOf(
                ChartEntry(1, Song("shared", "Song", "Artist", "", 180))
            ), 123L)
        }
        val first = repository.getLatamCharts().data!!
        assertThat(repository.getLatamCharts().data).isEqualTo(first)
        coVerify(exactly = 17) { youtubeMusicClient.getCountryChart(any()) }
        assertThat(first.entries.single().positions).hasSize(17)
        assertThat(first.unavailableCountries).isEmpty()
        repository.getLatamCharts(forceRefresh = true)
        coVerify(exactly = 34) { youtubeMusicClient.getCountryChart(any()) }
    }

    @Test
    fun `latam limits concurrent country requests`() = runTest {
        var active = 0
        var maximum = 0
        coEvery { youtubeMusicClient.getCountryChart(any()) } coAnswers {
            active++
            maximum = maxOf(maximum, active)
            kotlinx.coroutines.delay(100)
            active--
            throw java.io.IOException("Unavailable")
        }
        repository.getLatamCharts()
        assertThat(maximum).isEqualTo(3)
    }

    // ═══════════════════════════════════════════════════════════════════
    // searchSongs
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `searchSongs returns gateway songs`() = runTest {
        coEvery { gateway.search("rock") } returns Result.success(
            listOf(
                Song("1", "Song A", "Artist A", "url1", 200L),
                Song("2", "Song B", "Artist B", "url2", 300L)
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
    fun `searchSongs returns error on exception`() = runTest {
        coEvery { gateway.search(any()) } returns Result.failure(RuntimeException("Network error"))

        val result = repository.searchSongs("test")

        assertThat(result).isInstanceOf(Resource.Error::class.java)
        assertThat((result as Resource.Error).message).contains("Network error")
    }

    // ═══════════════════════════════════════════════════════════════════
    // getStreamInfo
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `getStreamInfo returns streamUrl on success`() = runTest {
        coEvery { gateway.getStream("abc") } returns Result.success("https://cdn.example.com/audio.mp3")

        val result = repository.getStreamInfo("abc")

        assertThat(result).isInstanceOf(Resource.Success::class.java)
        assertThat((result as Resource.Success).data).isEqualTo("https://cdn.example.com/audio.mp3")
    }

    @Test
    fun `getStreamInfo returns error on exception`() = runTest {
        coEvery { gateway.getStream(any()) } returns Result.failure(RuntimeException("Server down"))

        val result = repository.getStreamInfo("abc")

        assertThat(result).isInstanceOf(Resource.Error::class.java)
    }

    // ═══════════════════════════════════════════════════════════════════
    // getRelatedSongs
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `getRelatedSongs returns gateway tracks when available`() = runTest {
        val relatedTracks = listOf(
            Song("r1", "Related 1", "Art", "url1", 180L),
            Song("r2", "Related 2", "Art", "url2", 200L),
            Song("r3", "Related 3", "Art", "url3", 220L),
            Song("r4", "Related 4", "Art", "url4", 240L),
            Song("r5", "Related 5", "Art", "url5", 260L)
        )
        coEvery { gateway.getRelated("seed") } returns Result.success(relatedTracks)

        val result = repository.getRelatedSongs("seed")

        assertThat(result).isInstanceOf(Resource.Success::class.java)
        val songs = (result as Resource.Success).data!!
        assertThat(songs).hasSize(5)
        val ids = songs.map { it.id }
        assertThat(ids).doesNotContain("seed") // seed is excluded
    }

    @Test
    fun `getRelatedSongs returns error when all strategies fail`() = runTest {
        coEvery { gateway.getRelated(any()) } returns Result.failure(RuntimeException("Providers down"))

        val result = repository.getRelatedSongs("seed")

        assertThat(result).isInstanceOf(Resource.Error::class.java)
    }

    @Test
    fun `getTrending uses regional music searches before generic trends`() = runTest {
        coEvery { gateway.search(any()) } answers {
            val query = firstArg<String>()
            Result.success(
                (1..8).map { index ->
                    Song(
                        id = "${query.hashCode()}-$index",
                        title = "Artista $index - Canción $index (Official Video)",
                        artist = "Artista $index - Topic",
                        thumbnailUrl = "https://i.ytimg.com/vi/$index/hqdefault.jpg",
                        durationSeconds = 180L + index
                    )
                }
            )
        }

        val result = repository.getTrending("PE")

        assertThat(result).isInstanceOf(Resource.Success::class.java)
        assertThat((result as Resource.Success).data).hasSize(8)
        coVerify(exactly = 4) { gateway.search(any()) }
        coVerify(exactly = 0) { gateway.getTrending(any()) }
    }

    @Test
    fun `getHomeFeed is composed locally without calling BFF`() = runTest {
        coEvery { gateway.search(any()) } answers {
            val query = firstArg<String>()
            Result.success(
                (1..8).map { index ->
                    Song(
                        id = "${query.hashCode()}-$index",
                        title = "$query - Canción $index (Official Video)",
                        artist = "Artista $index - Topic",
                        thumbnailUrl = "https://i.ytimg.com/vi/$index/hqdefault.jpg",
                        durationSeconds = 180L + index
                    )
                }
            )
        }

        val result = repository.getHomeFeed()

        assertThat(result).isInstanceOf(Resource.Success::class.java)
        val feed = (result as Resource.Success).data!!
        assertThat(feed.carousels.map { it.type }).containsExactly("trending", "genre", "genre").inOrder()
        assertThat(feed.carousels.first().items).isNotEmpty()
        coVerify(exactly = 6) { gateway.search(any()) }
    }

}
