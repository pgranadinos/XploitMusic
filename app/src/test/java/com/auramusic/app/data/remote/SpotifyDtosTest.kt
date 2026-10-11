package com.auramusic.app.data.remote

import com.google.common.truth.Truth.assertThat
import com.google.gson.Gson
import org.junit.Test

class SpotifyDtosTest {

    private val gson = Gson()

    @Test
    fun `parses current playlist items response`() {
        val json = """
            {
              "next": null,
              "items": [{
                "is_local": false,
                "item": {
                  "id": "spotify-track-1",
                  "name": "Song",
                  "duration_ms": 201000,
                  "artists": [{"id": "artist-1", "name": "Artist"}],
                  "album": {"name": "Album", "images": []}
                }
              }]
            }
        """.trimIndent()

        val page = gson.fromJson(json, SpotifyPagedTracksDto::class.java)
        val track = page.items.single().resolvedItem

        assertThat(track?.id).isEqualTo("spotify-track-1")
        assertThat(track?.artists?.single()?.name).isEqualTo("Artist")
        assertThat(page.items.single().isLocal).isFalse()
    }

    @Test
    fun `prefers items summary but accepts legacy tracks summary`() {
        val current = gson.fromJson(
            """{"id":"1","name":"Current","items":{"total":12}}""",
            SpotifyPlaylistDto::class.java
        )
        val legacy = gson.fromJson(
            """{"id":"2","name":"Legacy","tracks":{"total":8}}""",
            SpotifyPlaylistDto::class.java
        )

        assertThat(current.totalItems).isEqualTo(12)
        assertThat(legacy.totalItems).isEqualTo(8)
    }
}
