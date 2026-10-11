package com.xploitmusic.app.domain.model

import com.xploitmusic.app.domain.model.Song

/**
 * Spotify-style smart shuffle: distributes songs so no two consecutive
 * songs are from the same artist, using round-robin group interleaving.
 */
object SmartShuffler {

    /**
     * Shuffles [songs] ensuring artist diversity.
     * Algorithm:
     * 1. Group by artist
     * 2. Shuffle within each group
     * 3. Round-robin pick from each group
     * 4. If impossible (single-artist list), fall back to standard shuffle
     */
    fun shuffle(songs: List<Song>): List<Song> {
        if (songs.size <= 1) return songs

        val grouped = songs.groupBy { it.artist.lowercase().trim() }

        // If only one artist, standard shuffle
        if (grouped.size <= 1) return songs.shuffled()

        // Shuffle within each artist group, then sort groups by size descending
        val buckets = grouped.values
            .map { it.shuffled().toMutableList() }
            .sortedByDescending { it.size }
            .toMutableList()

        val result = mutableListOf<Song>()
        var lastArtist: String? = null

        while (buckets.any { it.isNotEmpty() }) {
            // Find a bucket whose artist differs from lastArtist
            val candidateIdx = buckets.indexOfFirst { bucket ->
                bucket.isNotEmpty() &&
                    bucket.first().artist.lowercase().trim() != lastArtist
            }

            val idx = if (candidateIdx >= 0) candidateIdx
            else buckets.indexOfFirst { it.isNotEmpty() } // fallback if unavoidable

            if (idx < 0) break

            val song = buckets[idx].removeFirst()
            result.add(song)
            lastArtist = song.artist.lowercase().trim()

            // Remove empty buckets for efficiency
            buckets.removeAll { it.isEmpty() }
        }

        return result
    }
}
