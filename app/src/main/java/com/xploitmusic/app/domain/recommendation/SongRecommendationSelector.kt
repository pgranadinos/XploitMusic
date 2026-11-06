package com.xploitmusic.app.domain.recommendation

import com.xploitmusic.app.domain.model.Song
import java.util.Locale

/** Shared filtering and hybrid ranking for radio recommendations. */
internal object SongRecommendationSelector {
    private val collectionTitle = Regex(
        """(?i)^(?:mix\b|playlist\b|full album\b|album completo\b|top\s*\d+\b|best of\b|lo mejor de\b|karaoke\b|reaction\b)|\b(?:mega\s*mix|nonstop|full album|album completo|playlist|karaoke)\b"""
    )
    private val metadata = Regex(
        """(?i)\s*(?:\[(?:official\s+(?:audio|video)|audio oficial|video oficial|lyrics?|letra)\]|\((?:official\s+(?:audio|video)|audio oficial|video oficial|lyrics?|letra)\))\s*"""
    )
    private val nonAlphaNumeric = Regex("[^\\p{L}\\p{N}]+")

    data class RecommendationScore(
        val song: Song,
        val radioPosition: Int,
        val historyRaw: Long,
        val historyScore: Double,
        val providerScore: Double,
        val finalScore: Double
    )

    fun scoreAndRank(
        candidates: List<Song>,
        songPlayCounts: Map<String, Long> = emptyMap(),
        artistPlayCounts: Map<String, Long> = emptyMap(),
        seedBias: Map<String, Long> = emptyMap(),
        excludedIds: Set<String> = emptySet(),
        limit: Int = 10
    ): List<RecommendationScore> {
        if (limit <= 0 || candidates.isEmpty()) return emptyList()

        // 1. Deduplication and invalid track filtering before scoring
        val seenIds = mutableSetOf<String>()
        val seenRecordings = mutableSetOf<String>()
        val filtered = candidates.filter { song ->
            val title = song.title.trim()
            val id = song.id.trim()
            val recording = "${normalize(metadata.replace(title, ""))}|${normalize(song.artist)}"
            id.isNotEmpty() && id !in excludedIds && title.length in 2..120 &&
                !collectionTitle.containsMatchIn(title) &&
                seenIds.add(id) && seenRecordings.add(recording)
        }

        if (filtered.isEmpty()) return emptyList()

        val n = filtered.size

        // 2. Calculate historyRaw for each candidate (No invented history)
        val rawScores = filtered.mapIndexed { index, song ->
            val songPlays = songPlayCounts[song.id] ?: 0L
            val normalizedArtist = normalize(song.artist)
            val artistPlays = artistPlayCounts[normalizedArtist] ?: artistPlayCounts[song.artist] ?: 0L
            val bias = seedBias[song.id] ?: seedBias[normalizedArtist] ?: seedBias[song.artist] ?: 0L
            val raw = songPlays + artistPlays + bias
            Triple(index, song, raw)
        }

        val maxRaw = rawScores.maxOf { it.third }

        // 3. Compute hybrid scores
        val scored = rawScores.map { (index, song, raw) ->
            val historyScore = if (maxRaw > 0L) raw.toDouble() / maxRaw.toDouble() else 0.0
            val providerScore = (n - index).toDouble() / n.toDouble()
            val finalScore = (0.70 * historyScore) + (0.30 * providerScore)

            RecommendationScore(
                song = song,
                radioPosition = index,
                historyRaw = raw,
                historyScore = historyScore,
                providerScore = providerScore,
                finalScore = finalScore
            )
        }

        // 4. Stable sort by finalScore descending (preserving original radio order on ties)
        val sorted = scored.sortedWith(
            compareByDescending<RecommendationScore> { it.finalScore }
                .thenBy { it.radioPosition }
        )

        // 5. Diversity pass: prevent consecutive songs by the same artist when alternatives exist
        val diversified = mutableListOf<RecommendationScore>()
        val remaining = sorted.toMutableList()

        while (remaining.isNotEmpty()) {
            val lastArtist = diversified.lastOrNull()?.let { normalize(it.song.artist) }
            val nextCandidateIndex = if (lastArtist != null) {
                // Find first candidate whose artist is different from lastArtist
                remaining.indexOfFirst { normalize(it.song.artist) != lastArtist }.takeIf { it != -1 } ?: 0
            } else {
                0
            }
            diversified.add(remaining.removeAt(nextCandidateIndex))
            if (diversified.size >= limit) break
        }

        return diversified
    }

    fun select(
        candidates: List<Song>,
        songPlayCounts: Map<String, Long> = emptyMap(),
        artistPlayCounts: Map<String, Long> = emptyMap(),
        seedBias: Map<String, Long> = emptyMap(),
        excludedIds: Set<String> = emptySet(),
        limit: Int = 10
    ): List<Song> {
        return scoreAndRank(candidates, songPlayCounts, artistPlayCounts, seedBias, excludedIds, limit)
            .map { it.song }
    }

    private fun normalize(value: String): String = value
        .lowercase(Locale.ROOT)
        .replace(metadata, " ")
        .replace(nonAlphaNumeric, " ")
        .trim()
}
