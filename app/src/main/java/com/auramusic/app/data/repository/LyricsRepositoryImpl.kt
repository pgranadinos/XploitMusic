package com.auramusic.app.data.repository

import com.auramusic.app.core.util.Resource
import com.auramusic.app.data.remote.LrcLibApi
import com.auramusic.app.domain.model.Lyrics
import javax.inject.Inject

class LyricsRepositoryImpl @Inject constructor(
    private val api: LrcLibApi
) {
    suspend fun getLyrics(trackName: String, artistName: String, duration: Double): Resource<Lyrics> {
        return try {
            val response = api.getLyrics(trackName, artistName, duration)
            val domainLyrics = Lyrics(
                id = response.id,
                trackName = response.trackName,
                artistName = response.artistName,
                albumName = response.albumName,
                duration = response.duration,
                instrumental = response.instrumental,
                plainLyrics = response.plainLyrics,
                syncedLyrics = response.syncedLyrics
            )
            Resource.Success(domainLyrics)
        } catch (e: Exception) {
            Resource.Error("Failed to fetch lyrics: ${e.message}")
        }
    }
}
