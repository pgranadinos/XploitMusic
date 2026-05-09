package com.auramusic.app.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auramusic.app.data.local.AuraDatabase
import com.auramusic.app.data.local.entity.PlaylistEntity
import com.auramusic.app.data.local.entity.PlaylistSongCrossRef
import com.auramusic.app.data.local.entity.SongEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaylistDaoTest {

    private lateinit var db: AuraDatabase
    private lateinit var playlistDao: PlaylistDao
    private lateinit var songDao: SongDao

    @Before
    fun createDb() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AuraDatabase::class.java
        ).allowMainThreadQueries().build()
        playlistDao = db.playlistDao()
        songDao = db.songDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun moveSong_updatesPositionsCorrectly() = runBlocking {
        // Setup songs
        songDao.insertSong(SongEntity(id = "s1", title = "A", artist = "A", thumbnailUrl = "", durationSeconds = 1))
        songDao.insertSong(SongEntity(id = "s2", title = "B", artist = "B", thumbnailUrl = "", durationSeconds = 1))
        songDao.insertSong(SongEntity(id = "s3", title = "C", artist = "C", thumbnailUrl = "", durationSeconds = 1))

        // Create Playlist
        val pid = playlistDao.createPlaylist(PlaylistEntity(name = "My Pl"))

        // Add 3 songs. addSongToPlaylist auto-assigns positions 0, 1, 2
        playlistDao.addSongToPlaylist(PlaylistSongCrossRef(pid, "s1"))
        playlistDao.addSongToPlaylist(PlaylistSongCrossRef(pid, "s2"))
        playlistDao.addSongToPlaylist(PlaylistSongCrossRef(pid, "s3"))

        // Verify initial state
        var refs = playlistDao.getPlaylistCrossRefs(pid)
        assertEquals(3, refs.size)
        assertEquals("s1", refs[0].songId) // pos 0
        assertEquals("s2", refs[1].songId) // pos 1
        assertEquals("s3", refs[2].songId) // pos 2

        // Move "s3" (pos 2) to pos 0
        playlistDao.moveSong(pid, fromPosition = 2, toPosition = 0)

        refs = playlistDao.getPlaylistCrossRefs(pid)
        // Expected order: s3 (0), s1 (1), s2 (2)
        assertEquals("s3", refs[0].songId)
        assertEquals("s1", refs[1].songId)
        assertEquals("s2", refs[2].songId)
        
        // Assert their specific positions are updated
        assertEquals(0, refs[0].position)
        assertEquals(1, refs[1].position)
        assertEquals(2, refs[2].position)
    }
}
