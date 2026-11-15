package com.xploitmusic.app.domain.recommendation

import com.xploitmusic.app.domain.model.Song

object AutoplaySeeds {
    fun select(list: List<Song>): List<Song> {
        val n = list.size
        if (n <= 0) return emptyList()

        val indices = listOf(
            0,
            n / 4,
            n / 2,
            (3 * n) / 4,
            n - 1
        ).distinct().sorted()

        return indices.map { list[it] }
    }
}
