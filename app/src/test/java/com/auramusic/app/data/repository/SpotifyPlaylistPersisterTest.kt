package com.auramusic.app.data.repository

import androidx.room.Room
import com.auramusic.app.data.local.AuraDatabase
import com.auramusic.app.data.remote.SpotifyPlaylistDto
import com.auramusic.app.data.remote.SpotifyTracksSummaryDto
import com.auramusic.app.domain.model.Song
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class SpotifyPlaylistPersisterTest {

    private lateinit var database: AuraDatabase
    private lateinit var persister: SpotifyPlaylistPersister

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AuraDatabase::class.java
        ).allowMainThreadQueries().build()
        persister = SpotifyPlaylistPersister(
            database = database,
            playlistDao = database.playlistDao(),
            songDao = database.songDao()
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `reimport updates the same playlist atomically`() = runTest {
        val playlist = SpotifyPlaylistDto(
            id = "spotify-playlist-1",
            name = "My Mix",
            description = null,
            images = null,
            itemsSummary = SpotifyTracksSummaryDto(total = 1)
        )
        val firstSong = Song("youtube-1", "First", "Artist", "", 180)
        val replacement = Song("youtube-2", "Replacement", "Artist", "", 200)

        val firstId = persister.persist(playlist, listOf(firstSong))
        val secondId = persister.persist(playlist.copy(name = "My Updated Mix"), listOf(replacement))

        assertThat(secondId).isEqualTo(firstId)
        assertThat(database.playlistDao().getAllPlaylists().first()).hasSize(1)
        assertThat(database.playlistDao().getPlaylistSongsOrdered(firstId).first().map { it.id })
            .containsExactly("youtube-2")
    }
}
