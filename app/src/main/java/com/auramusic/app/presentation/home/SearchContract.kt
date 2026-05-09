package com.auramusic.app.presentation.home

import com.auramusic.app.domain.model.SearchResultItem
import com.auramusic.app.domain.model.Song

// MVI State
data class SearchScreenState(
    val query: String = "",
    val suggestions: List<SearchSuggestion> = emptyList(),
    val searchState: SearchState = SearchState.Idle
)

sealed class SearchState {
    data object Idle : SearchState()
    data object Loading : SearchState()
    
    /** Completamente exitoso, todas las categorías trajeron resultados válidos o vacíos (pero HTTP OK) */
    data class Success(
        val songs: List<Song> = emptyList(),
        val channels: List<SearchResultItem.ChannelResult> = emptyList(),
        val playlists: List<SearchResultItem.PlaylistResult> = emptyList()
    ) : SearchState()
    
    /** Algunos resultados cargaron, pero otras categorías fallaron (ej. Piped fallback HTTP 500) */
    data class PartialSuccess(
        val songs: List<Song> = emptyList(),
        val channels: List<SearchResultItem.ChannelResult> = emptyList(),
        val playlists: List<SearchResultItem.PlaylistResult> = emptyList(),
        val failedCategories: Set<SearchCategory>
    ) : SearchState()
    
    /** Búsqueda finalizada pero con 0 resultados en las 3 categorías */
    data object Empty : SearchState()
    
    /** Falla catastrófica total (Network exception, etc) */
    data class Error(val message: String) : SearchState()
}

enum class SearchCategory { SONGS, CHANNELS, PLAYLISTS }

// MVI Intent (Action)
sealed class SearchAction {
    data class UpdateQuery(val query: String) : SearchAction()
    data class SelectSuggestion(val query: String) : SearchAction()
    data object Retry : SearchAction()
    data class DeleteHistory(val query: String) : SearchAction()
    data object ClearHistory : SearchAction()
}

// MVI Single Event
sealed class SearchEvent {
    data class ShowToast(val message: String) : SearchEvent()
}
