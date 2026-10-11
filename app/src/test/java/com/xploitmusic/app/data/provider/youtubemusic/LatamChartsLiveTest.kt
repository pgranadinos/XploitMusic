package com.xploitmusic.app.data.provider.youtubemusic

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.TimeUnit
import com.xploitmusic.app.data.repository.StreamRepositoryImpl
import com.xploitmusic.app.domain.model.LatamCountry
import io.mockk.mockk

/** Optional public-catalog smoke check. Does not launch Android or request audio streams. */
class LatamChartsLiveTest {
    @Test
    fun `public latam charts return country positions including Brazil`() = runBlocking {
        assumeTrue(System.getenv("XPLOIT_VERIFY_LATAM_CHARTS") == "1")
        val client = YouTubeMusicClient(OkHttpClient.Builder()
            .callTimeout(20, TimeUnit.SECONDS).build())

        val repository = StreamRepositoryImpl(mockk(), mockk(), client)
        val result = repository.getLatamCharts(forceRefresh = true)
        assertThat(result.data).isNotNull()
        val selection = result.data!!
        assertThat(selection.charts.size).isAtLeast(2)
        assertThat(selection.charts.map { it.country }).contains(LatamCountry.BR)
        assertThat(selection.entries).isNotEmpty()
        assertThat(selection.entries.map { it.song.id }.distinct()).hasSize(selection.entries.size)
        selection.charts.forEach { chart ->
            assertThat(chart.entries.map { it.rank }).isInOrder()
            assertThat(chart.entries.all { it.rank > 0 && it.song.id.isNotBlank() }).isTrue()
            assertThat(chart.playlistId).startsWith("PL")
            println("${chart.country.name}: ${chart.sourceTitle}; ${chart.entries.size} tracks")
        }
        println("LATAM: ${selection.entries.size} unique tracks; unavailable: ${selection.unavailableCountries}")
    }
}
