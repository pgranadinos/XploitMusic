package com.xploitmusic.app.domain.recommendation

import com.xploitmusic.app.domain.model.Song
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
        // Contrato 2.1 (aprobado): el historial reordena artistas; la diversidad solo evita
        // repetir el mismo artista seguido cuando hay alternativas. Reemplaza el round-robin de HEAD.
        // Cálculo de finalScore (peso historial 0.70, peso radio 0.30, N=4):
        // - fav-1 (bias 100, max 100 -> hist=1.0, prov=0.5 -> final=0.85)
        // - seed-1 (bias 50, max 100 -> hist=0.5, prov=1.0 -> final=0.65)
        // - seed-2 (bias 0, max 100 -> hist=0.0, prov=0.75 -> final=0.225)
        // - other (bias 0, max 100 -> hist=0.0, prov=0.25 -> final=0.075)
        // Orden ordenado por score: [fav-1, seed-1, seed-2, other]
        // Pasada de diversidad (evita seed-2 consecutivo a seed-1 al haber 'other' disponible): [fav-1, seed-1, other, seed-2]
        val result = SongRecommendationSelector.select(
            candidates = listOf(
                song("seed-1", "Seed one", "Seed Artist"),
                song("seed-2", "Seed two", "Seed Artist"),
                song("fav-1", "Favorite one", "Favorite Artist"),
                song("other", "Other one", "Other Artist")
            ),
            seedBias = mapOf("seed artist" to 50L, "favorite artist" to 100L)
        )

        assertThat(result.map { it.id }).containsExactly("fav-1", "seed-1", "other", "seed-2").inOrder()
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

    @Test
    fun `without history nor seedBias order matches radio position order`() {
        val candidates = listOf(
            song("r1", "Radio Song 1", "Artist 1"),
            song("r2", "Radio Song 2", "Artist 2"),
            song("r3", "Radio Song 3", "Artist 3")
        )

        val ranked = SongRecommendationSelector.select(
            candidates = candidates,
            songPlayCounts = emptyMap(),
            artistPlayCounts = emptyMap(),
            seedBias = emptyMap()
        )

        assertThat(ranked.map { it.id }).containsExactly("r1", "r2", "r3").inOrder()
    }

    @Test
    fun `seedBias does not count as history and applies independently`() {
        val candidates = listOf(
            song("r1", "Radio Song 1", "Artist 1"),
            song("r2", "Radio Song 2", "Artist 2")
        )

        val rankedWithoutBias = SongRecommendationSelector.select(
            candidates = candidates,
            seedBias = emptyMap()
        )
        assertThat(rankedWithoutBias.map { it.id }).containsExactly("r1", "r2").inOrder()

        val rankedWithBias = SongRecommendationSelector.select(
            candidates = candidates,
            seedBias = mapOf("r2" to 100L)
        )
        assertThat(rankedWithBias.map { it.id }).containsExactly("r2", "r1").inOrder()
    }

    @Test
    fun `two songs of same artist with high score are not adjacent if other artist available`() {
        val candidates = listOf(
            song("s1", "Song 1", "Artist A"),
            song("s2", "Song 2", "Artist A"),
            song("other", "Other", "Artist B")
        )

        val ranked = SongRecommendationSelector.select(
            candidates = candidates,
            seedBias = mapOf("artist a" to 100L)
        )

        assertThat(ranked.map { it.artist }).containsExactly("Artist A", "Artist B", "Artist A").inOrder()
    }

    @Test
    fun `if all songs are by same artist order is sorted by score without error`() {
        val candidates = listOf(
            song("s1", "Song 1", "Artist A"),
            song("s2", "Song 2", "Artist A"),
            song("s3", "Song 3", "Artist A")
        )

        val ranked = SongRecommendationSelector.select(
            candidates = candidates,
            seedBias = mapOf("s1" to 100L, "s2" to 50L)
        )

        assertThat(ranked.map { it.id }).containsExactly("s1", "s2", "s3").inOrder()
    }
}
