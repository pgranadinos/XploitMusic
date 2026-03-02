package com.auramusic.app.domain.usecase

import com.auramusic.app.domain.model.Song
import com.auramusic.app.domain.repository.FavoritesRepository
import javax.inject.Inject

class ToggleFavoriteUseCase @Inject constructor(
    private val favoritesRepository: FavoritesRepository
) {
    /** Toggles favorite and returns the new isFavorite state */
    suspend operator fun invoke(song: Song): Boolean {
        return favoritesRepository.toggleFavorite(song)
    }
}
