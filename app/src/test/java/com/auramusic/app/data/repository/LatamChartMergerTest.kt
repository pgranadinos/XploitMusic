package com.auramusic.app.data.repository

import com.auramusic.app.domain.model.*
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LatamChartMergerTest {
    private fun track(id: String, rank: Int, title: String = id, artist: String = "Artist") =
        ChartEntry(rank, Song(id, title, artist, "", 180L))
    private fun chart(country: LatamCountry, vararg tracks: ChartEntry) =
        CountryChart(country, "Top diario ${country.label}", "PL${country.name}", tracks.toList(), 1L)

    @Test
    fun `more countries then better average rank determines selection order`() {
        val result = mergeLatamCharts(listOf(
            chart(LatamCountry.MX, track("single", 1), track("shared", 5), track("better", 3)),
            chart(LatamCountry.BR, track("shared", 7), track("better", 4))
        ))
        assertThat(result.map { it.song.id }).containsExactly("better", "shared", "single").inOrder()
        assertThat(result[0].positions.map { it.rank }).containsExactly(3, 4).inOrder()
    }

    @Test
    fun `equivalent recordings merge and each country contributes only once`() {
        val result = mergeLatamCharts(listOf(
            chart(LatamCountry.MX, track("audio", 3, "Canción (Official Audio)"), track("video", 8, "Canción (Official Video)")),
            chart(LatamCountry.BR, track("video", 2, "Canción (Official Video)"))
        ))
        assertThat(result).hasSize(1)
        assertThat(result.single().positions).containsExactly(
            CountryPosition(LatamCountry.BR, 2), CountryPosition(LatamCountry.MX, 3)
        ).inOrder()
    }

    @Test
    fun `same video merges even if metadata differs between countries`() {
        val result = mergeLatamCharts(listOf(
            chart(LatamCountry.MX, track("id", 4, "Song")),
            chart(LatamCountry.CO, track("id", 2, "Other localized title"))
        ))
        assertThat(result).hasSize(1)
        assertThat(result.single().positions).hasSize(2)
    }

    @Test
    fun `different artists live versions and remixes are not merged`() {
        val result = mergeLatamCharts(listOf(chart(LatamCountry.PE,
            track("original", 1, "Song"), track("remix", 2, "Song (Remix)"),
            track("live", 3, "Song (Live)"), track("cover", 4, "Song", "Another artist")
        )))
        assertThat(result).hasSize(4)
    }

    @Test
    fun `country response completion order does not affect selection`() {
        val charts = listOf(chart(LatamCountry.MX, track("a", 1)), chart(LatamCountry.BR, track("b", 1)))
        assertThat(mergeLatamCharts(charts)).isEqualTo(mergeLatamCharts(charts.reversed()))
        assertThat(mergeLatamCharts(emptyList())).isEmpty()
    }
}
