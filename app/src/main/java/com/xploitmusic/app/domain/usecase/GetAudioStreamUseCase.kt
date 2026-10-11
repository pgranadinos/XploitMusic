package com.xploitmusic.app.domain.usecase

import com.xploitmusic.app.core.util.Resource
import com.xploitmusic.app.domain.repository.StreamRepository
import javax.inject.Inject

class GetAudioStreamUseCase @Inject constructor(
    private val repository: StreamRepository
) {
    suspend operator fun invoke(songId: String): Resource<String> {
        return repository.getStreamInfo(songId)
    }
}
