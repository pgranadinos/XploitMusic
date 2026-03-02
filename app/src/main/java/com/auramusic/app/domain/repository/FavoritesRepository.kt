package com.auramusic.app.domain.repository

import com.auramusic.app.domain.model.Song
import kotlinx.coroutines.flow.Flow

interface FavoritesRepository {

    /** Observe all favorited songs, ordered by most recently liked */
    fun getFavorites(): Flow<List<Song>>

    /** Observe whether a specific song is favorited */
    fun isFavorite(songId: String): Flow<Boolean>

    /** Toggle the favorite status of a song. Returns the new isFavorite state. */
    suspend fun toggleFavorite(song: Song): Boolean

    /** Get distinct artist names from favorites (for recommendation engine) */
    fun getDistinctFavoriteArtists(): Flow<List<String>>

    /** Get total count of favorited songs */
    fun getFavoriteCount(): Flow<Int>

    /** Record that a song was played */
    suspend fun recordPlay(song: Song)
}
