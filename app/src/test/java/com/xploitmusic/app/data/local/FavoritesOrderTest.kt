package com.xploitmusic.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.xploitmusic.app.data.local.dao.SongDao
import com.xploitmusic.app.data.local.entity.SongEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FavoritesOrderTest {

    private lateinit var db: XploitDatabase
    private lateinit var songDao: SongDao

    private fun song(id: String, favoritePosition: Int = 0, likedAt: Long? = 1000L, isFavorite: Boolean = true): SongEntity =
        SongEntity(
            id = id,
            title = "Song $id",
            artist = "Artist",
            thumbnailUrl = "",
            durationSeconds = 180,
            isFavorite = isFavorite,
            likedAt = likedAt,
            favoritePosition = favoritePosition
        )

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, XploitDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        songDao = db.songDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `getFavorites respects favoritePosition ASC`() = runBlocking {
        songDao.insertSong(song("s1", favoritePosition = 10))
        songDao.insertSong(song("s2", favoritePosition = 2))
        songDao.insertSong(song("s3", favoritePosition = 5))

        val favorites = songDao.getFavorites().first()
        assertThat(favorites.map { it.id }).containsExactly("s2", "s3", "s1").inOrder()
    }

    @Test
    fun `setFavorite true puts song at top position`() = runBlocking {
        songDao.insertSong(song("s1", favoritePosition = 0))
        songDao.insertSong(song("s2", favoritePosition = 1))

        // Insert non-favorite s3 then setFavorite = true
        songDao.insertSong(song("s3", isFavorite = false))
        songDao.setFavorite("s3", isFavorite = true, likedAt = System.currentTimeMillis())

        val favorites = songDao.getFavorites().first()
        assertThat(favorites.first().id).isEqualTo("s3")
    }

    @Test
    fun `moveFavorite moves and rewrites positions 0 to n-1`() = runBlocking {
        songDao.insertSong(song("s1", favoritePosition = 0))
        songDao.insertSong(song("s2", favoritePosition = 1))
        songDao.insertSong(song("s3", favoritePosition = 2))

        // Move s3 from index 2 to index 0
        songDao.moveFavorite(fromPosition = 2, toPosition = 0)

        val favorites = songDao.getFavorites().first()
        assertThat(favorites.map { it.id }).containsExactly("s3", "s1", "s2").inOrder()
        assertThat(favorites.map { it.favoritePosition }).containsExactly(0, 1, 2).inOrder()
    }

    @Test
    fun `moveFavorite with out of bounds from or to does nothing`() = runBlocking {
        songDao.insertSong(song("s1", favoritePosition = 0))
        songDao.insertSong(song("s2", favoritePosition = 1))

        songDao.moveFavorite(fromPosition = -1, toPosition = 1)
        songDao.moveFavorite(fromPosition = 0, toPosition = 99)

        val favorites = songDao.getFavorites().first()
        assertThat(favorites.map { it.id }).containsExactly("s1", "s2").inOrder()
    }

    @Test
    fun `backfill favoritePosition with likedAt NULL generates unique 0 to n-1 positions`() = runBlocking {
        // Insert songs where likedAt is NULL or equal
        songDao.insertSong(song("s3", favoritePosition = 0, likedAt = null))
        songDao.insertSong(song("s1", favoritePosition = 0, likedAt = null))
        songDao.insertSong(song("s2", favoritePosition = 0, likedAt = 1000L))

        // Execute public constant BACKFILL_FAVORITE_POSITION_SQL
        db.openHelper.writableDatabase.execSQL(XploitDatabase.BACKFILL_FAVORITE_POSITION_SQL)

        val favorites = songDao.getFavorites().first()
        // s2 (likedAt 1000) > s1 (likedAt null, id s1) > s3 (likedAt null, id s3)
        assertThat(favorites.map { it.id }).containsExactly("s2", "s1", "s3").inOrder()
        assertThat(favorites.map { it.favoritePosition }).containsExactly(0, 1, 2).inOrder()
    }
}
