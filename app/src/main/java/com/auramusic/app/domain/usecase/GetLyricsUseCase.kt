package com.auramusic.app.domain.usecase

import com.auramusic.app.core.util.Resource
import com.auramusic.app.data.repository.LyricsRepositoryImpl
import com.auramusic.app.domain.model.Lyrics
import javax.inject.Inject

class GetLyricsUseCase @Inject constructor(
    private val repository: LyricsRepositoryImpl
) {
    suspend operator fun invoke(trackName: String, artistName: String, duration: Double): Resource<Lyrics> {
        return repository.getLyrics(trackName, artistName, duration)
    }
}
