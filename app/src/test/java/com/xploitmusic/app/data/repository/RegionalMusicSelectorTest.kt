package com.xploitmusic.app.data.repository

import com.xploitmusic.app.domain.model.Song
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RegionalMusicSelectorTest {

    @Test
    fun `rejects live news shorts and non song durations`() {
        assertThat(isRegionalMusicCandidate(song("1", "Concierto EN VIVO", 240))).isFalse()
        assertThat(isRegionalMusicCandidate(song("2", "Noticias del Perú", 180))).isFalse()
        assertThat(isRegionalMusicCandidate(song("3", "Nuevo baile #Shorts", 90))).isFalse()
        assertThat(isRegionalMusicCandidate(song("4", "Canción desconocida", 0))).isFalse()
        assertThat(isRegionalMusicCandidate(song("5", "Álbum completo", 3_600))).isFalse()
        assertThat(isRegionalMusicCandidate(song("6", "TOP 100 GLOBAL YouTube Music Charts", 240))).isFalse()
        assertThat(isRegionalMusicCandidate(song("7", "Playlist éxitos Perú 2026", 240))).isFalse()
        assertThat(isRegionalMusicCandidate(song("8", "Mi canción (Official Video)", 210))).isTrue()
    }

    @Test
    fun `prioritizes official music over alternate versions`() {
        val cover = song("cover", "Mi canción cover", 210)
        val official = song("official", "Mi canción (Official Video)", 210)

        val selected = selectRegionalMusic(listOf(listOf(cover, official)))

        assertThat(selected.first().id).isEqualTo("official")
    }

    @Test
    fun `deduplicates equivalent official and lyric uploads`() {
        val official = song("official", "Artista - Canción (Official Video)", 210)
        val lyrics = song("lyrics", "Artista - Canción [Lyrics]", 212)

        val selected = selectRegionalMusic(listOf(listOf(official, lyrics)))

        assertThat(selected).hasSize(1)
        assertThat(selected.first().id).isEqualTo("official")
    }

    @Test
    fun `builds Peru specific queries`() {
        val queries = regionalMusicQueries("PE", 2026)

        assertThat(queries).hasSize(4)
        assertThat(queries.all { it.contains("Perú") }).isTrue()
        assertThat(queries.all { it.contains("2026") }).isTrue()
    }

    private fun song(id: String, title: String, duration: Long) = Song(
        id = id,
        title = title,
        artist = "Artista - Topic",
        thumbnailUrl = "https://i.ytimg.com/vi/$id/hqdefault.jpg",
        durationSeconds = duration
    )
}
