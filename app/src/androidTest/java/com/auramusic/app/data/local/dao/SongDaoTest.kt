package com.auramusic.app.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auramusic.app.data.local.AuraDatabase
import com.auramusic.app.data.local.entity.SongEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SongDaoTest {

    private lateinit var db: AuraDatabase
    private lateinit var songDao: SongDao

    @Before
    fun createDb() {
        // Use an in-memory database so information is not stored permanently.
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AuraDatabase::class.java
        ).allowMainThreadQueries().build()
        songDao = db.songDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun upsertNetworkSong_preservesIsFavoriteAndIncrementsPlayCount() = runBlocking {
        // 1. Insert initial song as a favorite with 5 plays
        val initialSong = SongEntity(
            id = "s1",
            title = "Old Title",
            artist = "Artist",
            thumbnailUrl = "url",
            durationSeconds = 120,
            isFavorite = true,
            playCount = 5
        )
        songDao.insertSong(initialSong)

        // 2. Incoming network song has updated metadata but default favorite/playCount
        val networkSong = SongEntity(
            id = "s1",
            title = "New Title",
            artist = "Artist",
            thumbnailUrl = "new-url",
            durationSeconds = 125,
            isFavorite = false,
            playCount = 0
        )

        // Act: Upsert it
        songDao.upsertNetworkSong(networkSong)

        // Assert:
        // Metadata is updated, but local user-state (isFavorite, playCount) is preserved!
        val songInDb = songDao.getSongById("s1")
        assertNotNull(songInDb)
        assertEquals("New Title", songInDb!!.title)
        assertEquals("new-url", songInDb.thumbnailUrl)
        assertEquals(125, songInDb.durationSeconds)
        assertTrue("isFavorite should be preserved", songInDb.isFavorite)
        assertEquals(5, songInDb.playCount)
    }

    @Test
    fun recordPlay_incrementsPlayCountAndUpdatesTimestamp() = runBlocking {
        val initialSong = SongEntity(
            id = "s2",
            title = "Test",
            artist = "Artist",
            thumbnailUrl = "url",
            durationSeconds = 120,
            playCount = 0
        )
        songDao.insertSong(initialSong)

        // Act
        songDao.recordPlay("s2", 1000L)
        songDao.recordPlay("s2", 2000L)

        // Assert
        val songInDb = songDao.getSongById("s2")!!
        assertEquals(2, songInDb.playCount)
        assertEquals(2000L, songInDb.lastPlayedAt)
    }
}
