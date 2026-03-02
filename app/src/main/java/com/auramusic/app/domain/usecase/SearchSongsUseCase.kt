package com.auramusic.app.domain.usecase

import com.auramusic.app.core.util.Resource
import com.auramusic.app.domain.model.Song
import com.auramusic.app.domain.repository.StreamRepository
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
