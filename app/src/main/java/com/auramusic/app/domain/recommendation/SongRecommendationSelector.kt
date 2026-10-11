package com.auramusic.app.domain.recommendation

import com.auramusic.app.domain.model.Song
import java.util.Locale

/** Shared filtering and artist balancing for home and autoplay recommendations. */
internal object SongRecommendationSelector {
    private val collectionTitle = Regex(
        """(?i)^(?:mix\b|playlist\b|full album\b|album completo\b|top\s*\d+\b|best of\b|lo mejor de\b|karaoke\b|reaction\b)|\b(?:mega\s*mix|nonstop|full album|album completo|playlist|karaoke)\b"""
    )
    private val metadata = Regex(
        """(?i)\s*(?:\[(?:official\s+(?:audio|video)|audio oficial|video oficial|lyrics?|letra)\]|\((?:official\s+(?:audio|video)|audio oficial|video oficial|lyrics?|letra)\))\s*"""
    )
    private val nonAlphaNumeric = Regex("[^\\p{L}\\p{N}]+")

    fun select(
        candidates: List<Song>,
        seedArtist: String? = null,
        preferredArtists: Collection<String> = emptyList(),
        excludedIds: Set<String> = emptySet(),
        limit: Int = 15
    ): List<Song> {
        if (limit <= 0) return emptyList()
        val seed = normalize(seedArtist.orEmpty())
        val preferred = preferredArtists.map(::normalize).filter(String::isNotBlank).toSet()
        val seenIds = mutableSetOf<String>()
        val seenRecordings = mutableSetOf<String>()
        val valid = candidates.filter { song ->
            val title = song.title.trim()
            val id = song.id.trim()
            val recording = "${normalize(metadata.replace(title, ""))}|${normalize(song.artist)}"
            id.isNotEmpty() && id !in excludedIds && title.length in 2..120 &&
                !collectionTitle.containsMatchIn(title) &&
                seenIds.add(id) && seenRecordings.add(recording)
        }

        val groups = valid.withIndex().groupBy { normalize(it.value.artist) }.values
            .sortedWith(
                compareBy<List<IndexedValue<Song>>> {
                    val artist = it.first().let { entry -> normalize(entry.value.artist) }
                    when {
                        artist.isNotBlank() && artist != seed && artist in preferred -> 0
                        artist.isNotBlank() && artist != seed -> 1
                        else -> 2
                    }
                }.thenBy { it.first().index }
            )
        val selected = ArrayList<Song>(minOf(limit, valid.size))
        var round = 0
        while (selected.size < limit) {
            var added = false
            for (group in groups) {
                val candidate = group.getOrNull(round)
                if (candidate != null) {
                    selected.add(candidate.value)
                    added = true
                    if (selected.size == limit) break
                }
            }
            if (!added) break
            round++
        }
        return selected
    }

    private fun normalize(value: String): String = value
        .lowercase(Locale.ROOT)
        .replace(metadata, " ")
        .replace(nonAlphaNumeric, " ")
        .trim()
}
