package com.xploitmusic.app.domain.recommendation

import com.google.common.truth.Truth.assertThat
import com.xploitmusic.app.domain.model.Song
import org.junit.Test

class AutoplaySeedsTest {

    private fun song(id: String): Song =
        Song(id = id, title = "Song $id", artist = "Artist", thumbnailUrl = "", durationSeconds = 180)

    @Test
    fun `n = 0 returns empty list`() {
        val list = emptyList<Song>()
        val result = AutoplaySeeds.select(list)
        assertThat(result).isEmpty()
    }

    @Test
    fun `n = 1 returns 1 seed at index 0 without duplicates`() {
        val list = listOf(song("0"))
        val result = AutoplaySeeds.select(list)
        assertThat(result.map { it.id }).containsExactly("0")
        assertThat(result.distinct()).hasSize(result.size)
    }

    @Test
    fun `n = 2 returns 2 seeds at indices 0 and 1 without duplicates`() {
        val list = (0 until 2).map { song("$it") }
        val result = AutoplaySeeds.select(list)
        assertThat(result.map { it.id }).containsExactly("0", "1").inOrder()
        assertThat(result.distinct()).hasSize(result.size)
    }

    @Test
    fun `n = 5 returns 5 seeds covering all elements without duplicates`() {
        val list = (0 until 5).map { song("$it") }
        val result = AutoplaySeeds.select(list)
        assertThat(result.map { it.id }).containsExactly("0", "1", "2", "3", "4").inOrder()
        assertThat(result.distinct()).hasSize(result.size)
    }

    @Test
    fun `n = 12 returns 5 seeds at positions 0, 3, 6, 9, 11 without duplicates`() {
        val list = (0 until 12).map { song("$it") }
        val result = AutoplaySeeds.select(list)
        assertThat(result.map { it.id }).containsExactly("0", "3", "6", "9", "11").inOrder()
        assertThat(result.distinct()).hasSize(result.size)
    }

    @Test
    fun `n = 100 returns 5 seeds at positions 0, 25, 50, 75, 99 without duplicates`() {
        val list = (0 until 100).map { song("$it") }
        val result = AutoplaySeeds.select(list)
        assertThat(result.map { it.id }).containsExactly("0", "25", "50", "75", "99").inOrder()
        assertThat(result.distinct()).hasSize(result.size)
    }
}
