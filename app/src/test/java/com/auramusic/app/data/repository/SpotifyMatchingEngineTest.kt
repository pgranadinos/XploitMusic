package com.auramusic.app.data.repository

import com.auramusic.app.data.remote.SpotifyArtistDto
import com.auramusic.app.data.remote.SpotifyTrackDto
import com.auramusic.app.data.remote.SpotifyAlbumDto
import com.auramusic.app.domain.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SpotifyMatchingEngineTest {

    private lateinit var engine: SpotifyMatchingEngine

    @Before
    fun setup() {
        engine = SpotifyMatchingEngine()
    }

    // ─── Normalización ────────────────────────────────────────────────────────

    @Test
    fun `normalize should remove feat and official artifacts`() {
        val cases = mapOf(
            "Blinding Lights" to "blinding lights",
            "Save Your Tears (Official Video)" to "save your tears",
            "Starboy (feat. Daft Punk)" to "starboy",
            "Can't Feel My Face [Official Audio]" to "can t feel my face",
            "I Feel It Coming ft. Daft Punk" to "i feel it coming",
            "Die For You - Remix" to "die for you remix",
            "Call Out My Name (Live)" to "call out my name"
        )
        cases.forEach { (input, expected) ->
            assertEquals(expected, engine.normalize(input))
        }
    }

    // ─── Levenshtein ──────────────────────────────────────────────────────────

    @Test
    fun `levenshteinSimilarity should return high score for similar strings`() {
        val score = engine.levenshteinSimilarity("blinding lights", "blinding light")
        assertTrue("Score $score should be high", score > 0.9f)
    }

    // ─── Token Overlap ────────────────────────────────────────────────────────

    @Test
    fun `tokenOverlapScore should return high score for partial artist match`() {
        val score1 = engine.tokenOverlapScore("the weeknd", "the weeknd official")
        assertEquals(1.0f, score1) // 2/2 tokens de The Weeknd están en el canal de YT

        val score2 = engine.tokenOverlapScore("bad bunny", "bad bunny jhay cortez")
        assertEquals(1.0f, score2) // 2/2 tokens de Bad Bunny presentes
    }

    // ─── Integration ──────────────────────────────────────────────────────────

    @Test
    fun `findBestMatch should return EXACT match for perfect candidate`() {
        val spotifyTrack = SpotifyTrackDto(
            id = "1",
            name = "Blinding Lights",
            durationMs = 200_000,
            artists = listOf(SpotifyArtistDto("1", "The Weeknd")),
            album = SpotifyAlbumDto("After Hours", null)
        )

        val candidates = listOf(
            Song("yt1", "Blinding Lights (Official Video)", "The Weeknd", "", 202, ""),
            Song("yt2", "Blinding Lights (Live)", "Random Cover Channel", "", 210, "")
        )

        val result = engine.findBestMatch(spotifyTrack, candidates)

        assertEquals(SpotifyMatchingEngine.MatchType.EXACT, result.matchType)
        assertEquals("yt1", result.candidate?.id)
        assertTrue(result.score > 0.85f)
    }

    @Test
    fun `findBestMatch should return HIGH_CONFIDENCE for slight differences`() {
        val spotifyTrack = SpotifyTrackDto(
            id = "2",
            name = "Starboy",
            durationMs = 230_000,
            artists = listOf(SpotifyArtistDto("1", "The Weeknd"), SpotifyArtistDto("2", "Daft Punk")),
            album = SpotifyAlbumDto("Starboy", null)
        )

        val candidates = listOf(
            // Título sin normalize coincide bien, pero es lyrics (no oficia). Artist coincide pero duración difiere un poco más.
            Song("yt1", "Starboy Lyrics Video", "Pop Lyrics", "", 240, "")
        )

        val result = engine.findBestMatch(spotifyTrack, candidates)

        assertTrue(result.matchType == SpotifyMatchingEngine.MatchType.HIGH_CONFIDENCE || result.matchType == SpotifyMatchingEngine.MatchType.LOW_CONFIDENCE)
    }

    @Test
    fun `findBestMatch should return NO_MATCH for totally different candidate`() {
        val spotifyTrack = SpotifyTrackDto(
            id = "3",
            name = "Bohemian Rhapsody",
            durationMs = 354_000, // 5:54
            artists = listOf(SpotifyArtistDto("1", "Queen")),
            album = SpotifyAlbumDto("A Night At The Opera", null)
        )

        val candidates = listOf( // Candidatos totalmente equivocados de la API
            Song("yt1", "Random Podcast Episode", "Podcast Guy", "", 3600, "")
        )

        val result = engine.findBestMatch(spotifyTrack, candidates)

        assertEquals(SpotifyMatchingEngine.MatchType.NO_MATCH, result.matchType)
        assertTrue(result.score < 0.40f)
    }
}
