package com.auramusic.app.domain.recommendation

import com.auramusic.app.domain.model.Song
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SongRecommendationSelectorTest {
    private fun song(id: String, title: String, artist: String) =
        Song(id, title, artist, "", 180)

    @Test
    fun `filters collections and duplicate recordings across video ids`() {
        val result = SongRecommendationSelector.select(listOf(
            song("a1", "Song A (Official Audio)", "Artist A"),
            song("a2", "song a", "artist a"),
            song("mix", "Mix: Best songs", "Channel"),
            song("album", "Full album 2024", "Channel"),
            song("b", "Song B", "Artist B")
        ))

        assertThat(result.map { it.id }).containsExactly("a1", "b").inOrder()
    }

    @Test
    fun `balances artists and places familiar different artist first`() {
        val result = SongRecommendationSelector.select(
            candidates = listOf(
                song("seed-1", "Seed one", "Seed Artist"),
                song("seed-2", "Seed two", "Seed Artist"),
                song("fav-1", "Favorite one", "Favorite Artist"),
                song("other", "Other one", "Other Artist")
            ),
            seedArtist = "Seed Artist",
            preferredArtists = listOf("Favorite Artist")
        )

        assertThat(result.map { it.id }).containsExactly("fav-1", "other", "seed-1", "seed-2").inOrder()
    }

    @Test
    fun `excludes played ids and respects limit`() {
        val result = SongRecommendationSelector.select(
            candidates = (1..5).map { song("$it", "Song $it", "Artist $it") },
            excludedIds = setOf("1", "3"),
            limit = 2
        )

        assertThat(result.map { it.id }).containsExactly("2", "4").inOrder()
    }
}
