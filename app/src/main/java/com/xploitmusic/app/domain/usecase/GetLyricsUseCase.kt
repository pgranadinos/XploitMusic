package com.xploitmusic.app.domain.usecase

import com.xploitmusic.app.core.util.Resource
import com.xploitmusic.app.data.repository.LyricsRepositoryImpl
import com.xploitmusic.app.domain.model.Lyrics
import javax.inject.Inject

class GetLyricsUseCase @Inject constructor(
    private val repository: LyricsRepositoryImpl
) {
    suspend operator fun invoke(trackName: String, artistName: String, duration: Double): Resource<Lyrics> {
        return repository.getLyrics(trackName, artistName, duration)
    }
}
