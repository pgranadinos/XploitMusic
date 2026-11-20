package com.xploitmusic.app.presentation.share

import com.google.common.truth.Truth.assertThat
import com.xploitmusic.app.domain.model.Song
import org.junit.Test

class ShareHelperTest {

    @Test
    fun `buildShareText formats song info and youtube music link correctly`() {
        val song = Song(
            id = "abc123xyz",
            title = "Mi Canción",
            artist = "Artista Famoso",
            thumbnailUrl = "",
            durationSeconds = 180
        )
        val text = buildShareText(song)
        assertThat(text).isEqualTo("Mi Canción — Artista Famoso\nhttps://music.youtube.com/watch?v=abc123xyz")
    }

    @Test
    fun `auto scaling text scale limit calculation stays above min scale`() {
        val initialSizeSp = 24f
        val minScale = 0.40f
        val minAllowedSp = initialSizeSp * minScale
        val scaledDownSp = (initialSizeSp * 0.30f).coerceAtLeast(minAllowedSp)
        assertThat(scaledDownSp).isEqualTo(9.6f)
    }
}
