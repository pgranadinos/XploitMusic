package com.auramusic.app.presentation.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auramusic.app.core.util.Resource
import com.auramusic.app.domain.model.SearchResults
import com.auramusic.app.domain.model.Song
import com.auramusic.app.domain.repository.FavoritesRepository
import com.auramusic.app.domain.repository.StreamRepository
import com.auramusic.app.domain.usecase.SearchSongsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val searchSongsUseCase: SearchSongsUseCase,
    private val favoritesRepository: FavoritesRepository,
    private val streamRepository: StreamRepository
) : ViewModel() {

    companion object {
        private const val TAG = "XploitDebug"
    }

    // Search
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<Resource<List<Song>>?>(null)
    val searchResults = _searchResults.asStateFlow()

    // Mixed search results (songs, channels, playlists)
    private val _mixedSearchResults = MutableStateFlow<Resource<SearchResults>?>(null)
    val mixedSearchResults = _mixedSearchResults.asStateFlow()

    // Sections
    val favorites = favoritesRepository.getFavorites()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _trending = MutableStateFlow<List<Song>>(emptyList())
    val trending = _trending.asStateFlow()

    private val _recommended = MutableStateFlow<List<Song>>(emptyList())
    val recommended = _recommended.asStateFlow()

    private val _recommendedLabel = MutableStateFlow("Basado en tus gustos")
    val recommendedLabel = _recommendedLabel.asStateFlow()

    init {
        loadTrending()
        loadRecommendations()

        // Search-as-you-type: debounce 500ms, then auto-search
        viewModelScope.launch {
            _searchQuery
                .debounce(500)
                .distinctUntilChanged()
                .filter { it.isNotEmpty() }
                .collect { query ->
                    search(query)
                }
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        if (query.isEmpty()) {
            _searchResults.value = null
            _mixedSearchResults.value = null
        }
    }

    private fun search(query: String) {
        viewModelScope.launch {
            _searchResults.value = Resource.Loading()
            _mixedSearchResults.value = Resource.Loading()

            // Fetch mixed results (songs, channels, playlists)
            val mixedResult = streamRepository.searchMixed(query)
            _mixedSearchResults.value = mixedResult

            // Also populate the flat song list for backward compat
            if (mixedResult is Resource.Success) {
                _searchResults.value = Resource.Success(mixedResult.data?.songs ?: emptyList())
            } else if (mixedResult is Resource.Error) {
                _searchResults.value = Resource.Error(mixedResult.message ?: "Search error")
            }
        }
    }

    private fun loadTrending() {
        viewModelScope.launch {
            try {
                val result = streamRepository.getTrending()
                if (result is Resource.Success) {
                    _trending.value = result.data?.take(15) ?: emptyList()
                    Log.d(TAG, "HomeVM: Loaded ${_trending.value.size} trending")
                }
            } catch (e: Exception) {
                Log.e(TAG, "HomeVM: Trending error: ${e.message}")
            }
        }
    }

    private fun loadRecommendations() {
        viewModelScope.launch {
            try {
                // Get distinct artists from favorites
                favoritesRepository.getDistinctFavoriteArtists().collect { artists ->
                    if (artists.isNotEmpty()) {
                        val picked = artists.shuffled().take(2)
                        _recommendedLabel.value = "Basado en: ${picked.joinToString(", ")}"

                        val allRecommended = mutableListOf<Song>()
                        for (artist in picked) {
                            val result = searchSongsUseCase("$artist mix")
                            if (result is Resource.Success) {
                                allRecommended.addAll(result.data?.take(5) ?: emptyList())
                            }
                        }
                        _recommended.value = allRecommended.distinctBy { it.id }.take(10)
                        Log.d(TAG, "HomeVM: Loaded ${_recommended.value.size} recommendations")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "HomeVM: Recommendations error: ${e.message}")
            }
        }
    }
}
