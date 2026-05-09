package com.auramusic.app.data.local

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auramusic.app.data.local.dao.PlaylistDao
import com.auramusic.app.data.local.dao.SongDao
import com.auramusic.app.data.local.entity.PlaylistEntity
import com.auramusic.app.data.local.entity.PlaylistSongCrossRef
import com.auramusic.app.data.local.entity.SongEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class DatabaseConsistencyTest {

    private lateinit var db: AuraDatabase
    private lateinit var playlistDao: PlaylistDao
    private lateinit var songDao: SongDao

    @Before
    fun createDb() {
        // Enforce in-memory aisled structure
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
    fun `test C6 - getPlaylistSongsOrdered returns items correctly ordered ignoring id hash`() = runTest {
        // 1. Insert dummy songs
        val songA = SongEntity("id_A", "Song A", "Artist", "", 100)
        val songB = SongEntity("id_B", "Song B", "Artist", "", 100)
        songDao.insertSong(songA)
        songDao.insertSong(songB)

        // 2. Create Playlist
        val playlistId = playlistDao.createPlaylist(PlaylistEntity(name = "Test Mix"))

        // 3. Add to playlist in intentional reverse order (B first, A next)
        playlistDao.addSongToPlaylist(PlaylistSongCrossRef(playlistId, songB.id))
        playlistDao.addSongToPlaylist(PlaylistSongCrossRef(playlistId, songA.id))

        // 4. Retrieve via new strictly ordered Query
        val orderedSongs = playlistDao.getPlaylistSongsOrdered(playlistId).first()

        // 5. Evaluate consistency
        assertEquals(2, orderedSongs.size)
        assertEquals("Song B", orderedSongs[0].title) // Position 0
        assertEquals("Song A", orderedSongs[1].title) // Position 1
    }

    @Test
    fun `test C6 - playlist cross ref constraints ignore duplicates`() = runTest {
        val songA = SongEntity("id_A", "Song A", "Artist", "", 100)
        songDao.insertSong(songA)
        val playlistId = playlistDao.createPlaylist(PlaylistEntity(name = "Test Mix 2"))

        // Insert exactly the same cross ref twice
        playlistDao.addSongToPlaylist(PlaylistSongCrossRef(playlistId, songA.id))
        playlistDao.addSongToPlaylist(PlaylistSongCrossRef(playlistId, songA.id))

        // Cross refs are strictly protected by composite PRIMARY KEY (playlistId, songId)
        val crossRefs = playlistDao.getPlaylistCrossRefs(playlistId)
        assertEquals(1, crossRefs.size) // No duplication happened due to IGNORE constraint
    }
}
