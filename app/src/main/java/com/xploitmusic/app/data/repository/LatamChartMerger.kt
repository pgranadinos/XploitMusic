package com.xploitmusic.app.data.repository

import com.xploitmusic.app.domain.model.*
import java.text.Normalizer
import java.util.Locale

/** A selection, not an official LATAM chart. Each country contributes at most one position per song. */
internal fun mergeLatamCharts(charts: List<CountryChart>): List<LatamTrack> {
    val groups = linkedMapOf<String, MutableList<Pair<CountryChart, ChartEntry>>>()
    val knownIds = mutableMapOf<String, String>()
    charts.sortedBy { it.country.ordinal }.forEach { chart ->
        chart.entries.sortedBy { it.rank }.forEach { entry ->
            val song = entry.song
            val fingerprint = if (song.artist.isBlank() || song.artist == "YouTube Music") {
                "id:${song.id}"
            } else {
                "${normalizeChartText(song.title)}|${normalizeChartText(song.artist)}"
            }
            val key = knownIds.getOrPut(song.id) { fingerprint }
            groups.getOrPut(key) { mutableListOf() }.add(chart to entry)
        }
    }
    return groups.values.map { appearances ->
        val positions = appearances.groupBy { it.first.country }.map { (country, values) ->
            CountryPosition(country, values.minOf { it.second.rank })
        }.sortedWith(compareBy<CountryPosition> { it.rank }.thenBy { it.country.ordinal })
        LatamTrack(appearances.minBy { it.second.rank }.second.song, positions)
    }.sortedWith(
        compareByDescending<LatamTrack> { it.positions.size }
            .thenBy { it.positions.map(CountryPosition::rank).average() }
            .thenBy { it.positions.minOf(CountryPosition::rank) }
            .thenBy { it.song.id }
    )
}

private fun normalizeChartText(value: String): String =
    Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase(Locale.ROOT)
        // Keep remix/live/version labels: they may identify distinct recordings.
        .replace(Regex("""(?:\(|\[)\s*(?:official\s+(?:music\s+)?(?:video|audio)|video\s+oficial|audio\s+oficial|lyrics?)\s*(?:\)|\])"""), "")
        .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
        .trim()
