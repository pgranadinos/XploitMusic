package com.auramusic.app.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auramusic.app.data.local.AuraDatabase
import com.auramusic.app.data.local.entity.DownloadedTrackEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DownloadDaoTest {

    private lateinit var db: AuraDatabase
    private lateinit var downloadDao: DownloadDao

    @Before
    fun createDb() {
        // Use an in-memory database so information is not stored permanently.
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AuraDatabase::class.java
        ).allowMainThreadQueries().build()
        downloadDao = db.downloadDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun enqueueDownload_insertsWhenNotExists() = runBlocking {
        val entity = DownloadedTrackEntity(
            songId = "s1",
            title = "Test Song",
            artist = "Artist",
            thumbnailUrl = "url",
            durationSeconds = 120,
            status = "QUEUED",
            quality = "NORMAL"
        )
        val result = downloadDao.enqueueDownload(entity)
        assertTrue(result)
        
        val dbEntity = downloadDao.getBySongId("s1")
        assertNotNull(dbEntity)
        assertEquals("QUEUED", dbEntity?.status)
    }

    @Test
    fun enqueueDownload_ignoresWhenAlreadyCompleted() = runBlocking {
        val existing = DownloadedTrackEntity(
            songId = "s1",
            title = "Test Song",
            artist = "Artist",
            thumbnailUrl = "url",
            durationSeconds = 120,
            status = "COMPLETED",
            quality = "NORMAL"
        )
        downloadDao.insertRaw(existing)
        
        val newRequest = existing.copy(status = "QUEUED")
        val result = downloadDao.enqueueDownload(newRequest)
        
        assertFalse("Should return false since it's already downloaded", result)
        val dbEntity = downloadDao.getBySongId("s1")
        assertEquals("status should still be COMPLETED", "COMPLETED", dbEntity?.status)
    }

    @Test
    fun enqueueDownload_ignoresWhenDownloading() = runBlocking {
        val existing = DownloadedTrackEntity(
            songId = "s1",
            title = "Test Song",
            artist = "Artist",
            thumbnailUrl = "url",
            durationSeconds = 120,
            status = "DOWNLOADING",
            quality = "NORMAL"
        )
        downloadDao.insertRaw(existing)
        
        val newRequest = existing.copy(status = "QUEUED")
        val result = downloadDao.enqueueDownload(newRequest)
        
        assertFalse(result)
        val dbEntity = downloadDao.getBySongId("s1")
        assertEquals("DOWNLOADING", dbEntity?.status)
    }

    @Test
    fun getOldestCompletedDownloads_returnsOrderedByLastAccessedAt() = runBlocking {
        val oldDownload = DownloadedTrackEntity(
            songId = "s1", title = "S1", artist = "A", thumbnailUrl = "U", 
            durationSeconds = 120, status = "COMPLETED", quality = "N",
            lastAccessedAt = 1000L
        )
        val newestDownload = DownloadedTrackEntity(
            songId = "s2", title = "S2", artist = "A", thumbnailUrl = "U", 
            durationSeconds = 120, status = "COMPLETED", quality = "N",
            lastAccessedAt = 3000L
        )
        val midDownload = DownloadedTrackEntity(
            songId = "s3", title = "S3", artist = "A", thumbnailUrl = "U", 
            durationSeconds = 120, status = "COMPLETED", quality = "N",
            lastAccessedAt = 2000L
        )

        downloadDao.insertRaw(oldDownload)
        downloadDao.insertRaw(newestDownload)
        downloadDao.insertRaw(midDownload)

        val oldest = downloadDao.getOldestCompletedDownloads()
        assertEquals(3, oldest.size)
        assertEquals("s1", oldest[0].songId) // 1000L
        assertEquals("s3", oldest[1].songId) // 2000L
        assertEquals("s2", oldest[2].songId) // 3000L
    }
}
