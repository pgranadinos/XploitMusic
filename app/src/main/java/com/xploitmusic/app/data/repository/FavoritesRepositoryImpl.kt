package com.xploitmusic.app.data.repository

import com.xploitmusic.app.data.local.dao.SongDao
import com.xploitmusic.app.data.local.entity.SongEntity
import com.xploitmusic.app.domain.model.Song
import com.xploitmusic.app.domain.repository.FavoritesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class FavoritesRepositoryImpl @Inject constructor(
    private val songDao: SongDao
) : FavoritesRepository {

    override fun getFavorites(): Flow<List<Song>> {
        return songDao.getFavorites().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun isFavorite(songId: String): Flow<Boolean> {
        return songDao.isFavorite(songId).map { it == true }
    }

    override suspend fun toggleFavorite(song: Song): Boolean {
        val existing = songDao.getSongById(song.id)

        if (existing != null) {
            val newFavorite = !existing.isFavorite
            songDao.setFavorite(
                songId = song.id,
                isFavorite = newFavorite,
                likedAt = if (newFavorite) System.currentTimeMillis() else null
            )
            return newFavorite
        } else {
            val minPos = (songDao.getFavoritesList().minOfOrNull { it.favoritePosition } ?: 0) - 1
            songDao.insertSong(song.toEntity(isFavorite = true, favoritePosition = minPos))
            return true
        }
    }

    override suspend fun moveFavorite(fromPosition: Int, toPosition: Int) {
        songDao.moveFavorite(fromPosition, toPosition)
    }

    override fun getDistinctFavoriteArtists(): Flow<List<String>> {
        return songDao.getDistinctFavoriteArtists()
    }

    override fun getFavoriteCount(): Flow<Int> {
        return songDao.getFavoriteCount()
    }

    override suspend fun recordPlay(song: Song) {
        val existing = songDao.getSongById(song.id)
        if (existing == null) {
            songDao.insertSong(song.toEntity(isFavorite = false, favoritePosition = 0))
        }
        songDao.recordPlay(song.id, System.currentTimeMillis())
    }

    override fun getRecents(): Flow<List<Song>> {
        return songDao.getRecents().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getMostPlayed(): Flow<List<Song>> {
        return songDao.getMostPlayed().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    // --- Mappers ---

    private fun SongEntity.toDomain(): Song = Song(
        id = id,
        title = title,
        artist = artist,
        thumbnailUrl = thumbnailUrl,
        durationSeconds = durationSeconds,
        streamUrl = streamUrl,
        genre = genre
    )

    private fun Song.toEntity(isFavorite: Boolean, favoritePosition: Int = 0): SongEntity = SongEntity(
        id = id,
        title = title,
        artist = artist,
        thumbnailUrl = thumbnailUrl,
        durationSeconds = durationSeconds,
        streamUrl = streamUrl,
        genre = genre,
        isFavorite = isFavorite,
        likedAt = if (isFavorite) System.currentTimeMillis() else null,
        lastPlayedAt = null,
        favoritePosition = favoritePosition
    )
}
