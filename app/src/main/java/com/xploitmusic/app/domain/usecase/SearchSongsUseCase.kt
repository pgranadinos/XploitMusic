package com.xploitmusic.app.domain.usecase

import com.xploitmusic.app.core.util.Resource
import com.xploitmusic.app.domain.model.Song
import com.xploitmusic.app.domain.repository.StreamRepository
import javax.inject.Inject

class SearchSongsUseCase @Inject constructor(
    private val repository: StreamRepository
) {
    suspend operator fun invoke(query: String): Resource<List<Song>> {
        if (query.isBlank()) {
            return Resource.Error("Query cannot be empty")
        }
        return repository.searchSongs(query)
    }
}
