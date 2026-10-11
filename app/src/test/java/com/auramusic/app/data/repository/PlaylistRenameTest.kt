package com.auramusic.app.data.repository

import androidx.room.Room
import com.auramusic.app.data.local.AuraDatabase
import com.auramusic.app.data.local.entity.*
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class PlaylistRenameTest {
    @Test
    fun `rename persists only name and preserves songs order and source metadata`() = runTest {
        val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AuraDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val dao = db.playlistDao()
            val original = PlaylistEntity(name = "Original", coverUrl = "cover", source = "SPOTIFY", sourceId = "remote", createdAt = 123)
            val id = dao.createPlaylist(original)
            for (songId in listOf("second", "first")) {
                db.songDao().upsertNetworkSong(SongEntity(songId, songId, "Artist", "", 180))
                dao.addSongToPlaylist(PlaylistSongCrossRef(playlistId = id, songId = songId))
            }
            val refs = dao.getPlaylistCrossRefs(id)
            assertThat(dao.renamePlaylist(id, "Mi selección")).isEqualTo(1)
            assertThat(dao.getAllPlaylists().first().single()).isEqualTo(original.copy(playlistId = id, name = "Mi selección"))
            assertThat(dao.getPlaylistCrossRefs(id)).isEqualTo(refs)
            assertThat(dao.getPlaylistSongsOrdered(id).first().map { it.id }).containsExactly("second", "first").inOrder()
            assertThat(dao.renamePlaylist(id + 100, "Inexistente")).isEqualTo(0)
            assertThat(dao.getAllPlaylists().first()).hasSize(1)
        } finally {
            db.close()
        }
    }
}
