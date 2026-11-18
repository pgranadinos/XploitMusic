package com.xploitmusic.app.data.repository

import androidx.room.Room
import com.google.common.truth.Truth.assertThat
import com.xploitmusic.app.data.local.XploitDatabase
import com.xploitmusic.app.data.local.entity.PlaylistEntity
import com.xploitmusic.app.data.local.entity.PlaylistSongCrossRef
import com.xploitmusic.app.data.local.entity.SongEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class PlaylistReorderTest {

    @Test
    fun `moveSongInPlaylist updates getPlaylistSongsOrdered and combined getPlaylistWithSongs flow in position order`() = runTest {
        val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), XploitDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val dao = db.playlistDao()
            val repo = PlaylistRepositoryImpl(dao, db.songDao())

            val playlistId = dao.createPlaylist(PlaylistEntity(name = "My Playlist"))
            for (id in listOf("s1", "s2", "s3")) {
                db.songDao().upsertNetworkSong(SongEntity(id, "Song $id", "Artist", "", 180))
                dao.addSongToPlaylist(PlaylistSongCrossRef(playlistId = playlistId, songId = id))
            }

            // Verify initial order (s1, s2, s3)
            val initialSongs = dao.getPlaylistSongsOrdered(playlistId).first()
            assertThat(initialSongs.map { it.id }).containsExactly("s1", "s2", "s3").inOrder()

            // Move s3 (index 2) to index 0
            repo.moveSongInPlaylist(playlistId, fromPosition = 2, toPosition = 0)

            // Verify getPlaylistSongsOrdered order after move
            val orderedAfterMove = dao.getPlaylistSongsOrdered(playlistId).first()
            assertThat(orderedAfterMove.map { it.id }).containsExactly("s3", "s1", "s2").inOrder()

            // Verify combined getPlaylistWithSongs flow order after move
            val pwsAfterMove = repo.getPlaylistWithSongs(playlistId).first()
            assertThat(pwsAfterMove?.songs?.map { it.id }).containsExactly("s3", "s1", "s2").inOrder()
        } finally {
            db.close()
        }
    }
}
