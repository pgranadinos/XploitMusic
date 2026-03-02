package com.auramusic.app.domain.usecase

import com.auramusic.app.core.util.Resource
import com.auramusic.app.domain.repository.StreamRepository
import javax.inject.Inject

class GetAudioStreamUseCase @Inject constructor(
    private val repository: StreamRepository
) {
    suspend operator fun invoke(songId: String): Resource<String> {
        return repository.getStreamInfo(songId)
    }
}
