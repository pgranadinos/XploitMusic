package com.auramusic.app.core.media

import androidx.media3.common.MediaItem
import com.auramusic.app.domain.model.Song
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MediaItemMetadataTest {

    @Test
    fun `media metadata keeps duration needed by seek control`() {
        val source = Song(
            id = "track-1",
            title = "Canción",
            artist = "Artista",
            thumbnailUrl = "https://example.com/art.jpg",
            durationSeconds = 245L,
            genre = "Pop"
        )
        val mediaItem = MediaItem.Builder()
            .setMediaId(source.id)
            .setMediaMetadata(source.toMediaMetadata())
            .build()

        val restored = mediaItem.toSongOrNull()

        assertThat(restored?.durationSeconds).isEqualTo(245L)
        assertThat(restored?.genre).isEqualTo("Pop")
    }
}
