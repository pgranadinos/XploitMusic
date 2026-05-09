package com.auramusic.app.presentation.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auramusic.app.core.util.Resource
import com.auramusic.app.data.local.dao.SearchHistoryDao
import com.auramusic.app.data.local.entity.SearchHistoryEntity
import com.auramusic.app.domain.model.Song
import com.auramusic.app.domain.repository.FavoritesRepository
import com.auramusic.app.domain.repository.StreamRepository
import com.auramusic.app.domain.usecase.SearchSongsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.auramusic.app.data.local.dao.PlayHistoryDao
import kotlinx.coroutines.flow.update
@OptIn(FlowPreview::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val searchSongsUseCase: SearchSongsUseCase,
    private val favoritesRepository: FavoritesRepository,
    private val streamRepository: StreamRepository,
    private val searchHistoryDao: SearchHistoryDao,
    private val playHistoryDao: PlayHistoryDao
) : ViewModel() {

    companion object {
        private const val TAG = "XploitDebug"
    }

    // Active search job — cancelled on each new query to avoid stale results
    private var searchJob: Job? = null
    private var suggestionsJob: Job? = null

    // Search query
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    // Single search UI state
    private val _searchUiState = MutableStateFlow<SearchState>(SearchState.Idle)
    val searchUiState = _searchUiState.asStateFlow()

    // Retry and explicit events
    private val retryTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val manualSearchTrigger = MutableSharedFlow<String>(extraBufferCapacity = 1)

    // Search suggestions (shown in dropdown before user submits)
    private val _suggestions = MutableStateFlow<List<SearchSuggestion>>(emptyList())
    val suggestions = _suggestions.asStateFlow()

    // Recent searches (for initial dropdown when search bar is focused)
    val recentSearches = searchHistoryDao.getRecentSearches()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Sections
    val favorites = favoritesRepository.getFavorites()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _homeUiState = MutableStateFlow(HomeUiState())
    val homeUiState = _homeUiState.asStateFlow()

    init {
        loadHomeSections()
        setupSearchPipeline()

        // Wire live Room analytics sections so they auto-update as songs are played/liked
        viewModelScope.launch {
            favoritesRepository.getMostPlayed().collect { songs ->
                _homeUiState.update { state ->
                    state.copy(
                        mostPlayed = if (songs.isEmpty()) SectionState.Empty
                                     else SectionState.Success(songs)
                    )
                }
            }
        }
        viewModelScope.launch {
            favoritesRepository.getRecents().collect { songs ->
                _homeUiState.update { state ->
                    state.copy(
                        recents = if (songs.isEmpty()) SectionState.Empty
                                  else SectionState.Success(songs)
                    )
                }
            }
        }
        // Faster debounce for suggestions (autocomplete dropdown)
        viewModelScope.launch {
            _searchQuery
                .debounce(100)
                .distinctUntilChanged()
                .collect { query ->
                    if (query.length >= 2) {
                        loadSuggestions(query)
                    } else {
                        _suggestions.value = emptyList()
                    }
                }
        }

        // Cleanup old search history (> 30 days) on init
        viewModelScope.launch {
            try {
                val thirtyDaysAgo = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
                searchHistoryDao.deleteOlderThan(thirtyDaysAgo)
            } catch (e: Exception) {
                Log.w(TAG, "HomeVM: History cleanup failed: ${e.message}")
            }
        }
    }

    fun processAction(action: SearchAction) {
        when (action) {
            is SearchAction.UpdateQuery -> {
                _searchQuery.value = action.query
                if (action.query.isEmpty()) {
                    _searchUiState.value = SearchState.Idle
                    _suggestions.value = emptyList()
                }
            }
            is SearchAction.SelectSuggestion -> {
                _searchQuery.value = action.query
                _suggestions.value = emptyList()
                manualSearchTrigger.tryEmit(action.query)
            }
            is SearchAction.Retry -> {
                retryTrigger.tryEmit(Unit)
            }
            is SearchAction.DeleteHistory -> {
                viewModelScope.launch {
                    try {
                        searchHistoryDao.delete(action.query)
                    } catch (e: Exception) {
                        Log.w(TAG, "HomeVM: Delete history failed: ${e.message}")
                    }
                }
            }
            is SearchAction.ClearHistory -> {
                viewModelScope.launch {
                    try {
                        searchHistoryDao.clearAll()
                    } catch (e: Exception) {
                        Log.w(TAG, "HomeVM: Clear history failed: ${e.message}")
                    }
                }
            }
        }
    }

    // Deprecated helpers for retro-compatibility with HomeScreen
    fun onSearchQueryChange(query: String) = processAction(SearchAction.UpdateQuery(query))
    fun onSuggestionSelected(text: String) = processAction(SearchAction.SelectSuggestion(text))

    /**
     * Load suggestions from 2 sources:
     * 1. Local history matches (instant, max 3)
     * 2. Piped /suggestions API (remote, max 5)
     */
    private fun loadSuggestions(query: String) {
        suggestionsJob?.cancel()
        suggestionsJob = viewModelScope.launch {
            val merged = mutableListOf<SearchSuggestion>()

            // Source 1: Local history (instant)
            try {
                val historyMatches = searchHistoryDao.getMatchingHistory(query)
                merged.addAll(
                    historyMatches.take(3).map { SearchSuggestion.HistoryItem(it) }
                )
            } catch (e: Exception) {
                Log.w(TAG, "HomeVM: History suggestions failed: ${e.message}")
            }

            // Source 2: BFF autocomplete (remote)
            // Desactivado temporalmente hasta que se implemente GET /search/suggestions en el BFF
            /*
            try {
                val apiSuggestions = pipedApi.getSuggestions(query)
                // Deduplicate: exclude suggestions that match history queries
                val historyQueries = merged
                    .filterIsInstance<SearchSuggestion.HistoryItem>()
                    .map { it.entry.query }
                    .toSet()
                val filtered = apiSuggestions
                    .filter { it.lowercase() !in historyQueries }
                    .take(5)
                merged.addAll(filtered.map { SearchSuggestion.AutocompleteItem(it) })
            } catch (e: Exception) {
                Log.w(TAG, "HomeVM: Piped suggestions failed: ${e.message}")
            }
            */

            _suggestions.value = merged
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun setupSearchPipeline() {
        viewModelScope.launch {
            val debouncedQuery = _searchQuery
                .debounce { query ->
                    when {
                        query.isEmpty() -> 0L
                        query.length <= 2 -> 400L
                        query.length == 3 -> 300L
                        else -> 150L
                    }
                }
                .distinctUntilChanged()

            // Merge normal typing, manual selection, and retries.
            // Uso de un Pair(Query, IsRetry) para proteger el pipeline de la cancelación agresiva:
            // Si tocamos una sugerencia, emite por manual (false). 150ms después, el debouncer
            // emite la misma sugerencia (false). El distinctUntilChanged las considera "iguales" y 
            // descarta la segunda (la del debouncer), evitando que cancele el fetch vigente.
            // Si oprimimos Retry, IsRetry es true, por ende salta el bloqueo y reintenta.
            merge(
                debouncedQuery.map { it to false },
                manualSearchTrigger.map { it to false },
                retryTrigger.map { _searchQuery.value to true }
            )
            .distinctUntilChanged { old, new -> 
                old.first == new.first && !new.second
            }
            .flatMapLatest { (query, _) ->
                executeSearchFlow(query)
            }.collect { state ->
                _searchUiState.value = state
            }
        }
    }

    private fun executeSearchFlow(query: String) = flow {
        if (query.isBlank()) {
            emit(SearchState.Idle)
            return@flow
        }
        
        emit(SearchState.Loading)
        _suggestions.value = emptyList()

        val mixedResult = streamRepository.searchMixed(query)

        val state = when (mixedResult) {
            is Resource.Success -> {
                val data = mixedResult.data
                if (data != null && (data.songs.isNotEmpty() || data.channels.isNotEmpty() || data.playlists.isNotEmpty())) {
                    val failed = data.failedCategories.mapNotNull { cat ->
                        when (cat) {
                            "SONGS" -> SearchCategory.SONGS
                            "CHANNELS" -> SearchCategory.CHANNELS
                            "PLAYLISTS" -> SearchCategory.PLAYLISTS
                            else -> null
                        }
                    }.toSet()

                    if (failed.isNotEmpty()) {
                        SearchState.PartialSuccess(
                            songs = data.songs,
                            channels = data.channels,
                            playlists = data.playlists,
                            failedCategories = failed
                        )
                    } else {
                        SearchState.Success(
                            songs = data.songs,
                            channels = data.channels,
                            playlists = data.playlists
                        )
                    }
                } else {
                    SearchState.Empty
                }
            }
            is Resource.Error -> {
                SearchState.Error(mixedResult.message ?: "Error de búsqueda")
            }
            else -> SearchState.Loading
        }

        emit(state)
        
        if (state is SearchState.Success || state is SearchState.PartialSuccess) {
            recordSearchHistory(query)
        }
    }.catch { e ->
        if (e !is CancellationException) {
            Log.e(TAG, "HomeVM: Search exception: ${e.message}", e)
            emit(SearchState.Error("Error inesperado: ${e.localizedMessage}"))
        }
    }

    /** Persist search query to Room for history/suggestions */
    private suspend fun recordSearchHistory(query: String) {
        try {
            searchHistoryDao.upsert(
                SearchHistoryEntity(
                    query = query.lowercase().trim(),
                    displayQuery = query.trim(),
                    searchedAt = System.currentTimeMillis()
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "HomeVM: Record search history failed: ${e.message}")
        }
    }

    /** Called by HomeScreen when user clicks a search result — enriches history */
    fun onSearchResultClicked(resultId: String, resultTitle: String, resultType: String, thumbnail: String?) {
        val query = _searchQuery.value
        if (query.isBlank()) return
        viewModelScope.launch {
            try {
                searchHistoryDao.upsert(
                    SearchHistoryEntity(
                        query = query.lowercase().trim(),
                        displayQuery = query.trim(),
                        resultType = resultType,
                        resultId = resultId,
                        resultTitle = resultTitle,
                        resultThumbnail = thumbnail,
                        searchedAt = System.currentTimeMillis(),
                        clickedResult = true
                    )
                )
            } catch (e: Exception) {
                Log.w(TAG, "HomeVM: Record click failed: ${e.message}")
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // HOME PREMIUM DISCOVERY (Phase 2 implementation)
    // ═══════════════════════════════════════════════════════════════════

    fun processHomeAction(action: HomeAction) {
        when (action) {
            is HomeAction.RefreshAll -> loadHomeSections()
            is HomeAction.RetryQuickPicks -> loadQuickPicks()
            is HomeAction.RetryJumpBackIn -> loadJumpBackIn()
            is HomeAction.RetryArtistMixes -> loadArtistMixes()
            is HomeAction.RetryLocalTrends -> loadLocalTrends()
            is HomeAction.RetryRecommendations -> loadRecommendationsV2()
        }
    }

    private fun loadHomeSections() {
        loadQuickPicks()
        loadJumpBackIn()
        loadArtistMixes()
        loadLocalTrends()
        loadRecommendationsV2()
    }

    private fun loadQuickPicks() {
        viewModelScope.launch {
            _homeUiState.update { it.copy(quickPicks = SectionState.Loading) }
            try {
                // Get favorites as source for Quick Picks
                val favs = favoritesRepository.getFavorites().first()
                val sourceSongs = if (favs.isNotEmpty()) {
                    favs.take(6)
                } else {
                    // FALLBACK: Use Trending if no favorites yet (New Install)
                    val trending = streamRepository.getTrending()
                    if (trending is Resource.Success) {
                        trending.data?.take(6) ?: emptyList()
                    } else {
                        emptyList()
                    }
                }

                if (sourceSongs.isNotEmpty()) {
                    val picks = sourceSongs.map { song ->
                        QuickPickItem(
                            id = song.id,
                            title = song.title,
                            subtitle = song.artist,
                            imageUrl = song.thumbnailUrl,
                            song = song
                        )
                    }
                    _homeUiState.update { it.copy(quickPicks = SectionState.Success(picks)) }
                } else {
                    _homeUiState.update { it.copy(quickPicks = SectionState.Empty) }
                }
            } catch (e: Exception) {
                _homeUiState.update { it.copy(quickPicks = SectionState.Error("Error cargando Quick Picks")) }
            }
        }
    }

    private fun loadJumpBackIn() {
        viewModelScope.launch {
            _homeUiState.update { it.copy(jumpBackIn = SectionState.Loading) }
            try {
                val history = playHistoryDao.getRecentPlayHistory(5).first()
                if (history.isNotEmpty()) {
                    val items = history.map { entity ->
                        HistoryContext(
                            id = entity.contextId,
                            title = entity.title,
                            description = entity.description,
                            imageUrl = entity.imageUrl,
                            playedAt = entity.playedAtTimestamp,
                            type = entity.type
                        )
                    }
                    _homeUiState.update { it.copy(jumpBackIn = SectionState.Success(items)) }
                } else {
                    _homeUiState.update { it.copy(jumpBackIn = SectionState.Empty) }
                }
            } catch (e: Exception) {
                _homeUiState.update { it.copy(jumpBackIn = SectionState.Error("Error al cargar historial")) }
            }
        }
    }

    private fun loadArtistMixes() {
        viewModelScope.launch {
            _homeUiState.update { it.copy(artistMixes = SectionState.Loading) }
            try {
                val artists = favoritesRepository.getDistinctFavoriteArtists().first()
                if (artists.isNotEmpty()) {
                    val randomArtists = artists.shuffled().take(3)
                    val mixes = randomArtists.map { artist ->
                        UiPlaylist(
                            id = "mix_$artist",
                            title = "$artist Mix",
                            description = "Tus canciones favoritas y más",
                            imageUrl = "", // Generará fallback en la UI
                            seedArtist = artist
                        )
                    }
                    _homeUiState.update { it.copy(artistMixes = SectionState.Success(mixes)) }
                } else {
                    val defaultMixes = listOf(
                        UiPlaylist("mix_pop", "Pop Mix", "Lo mejor del pop actual", "", "Pop"),
                        UiPlaylist("mix_rock", "Rock Mix", "Clásicos y nuevos éxitos", "", "Rock")
                    )
                    _homeUiState.update { it.copy(artistMixes = SectionState.Success(defaultMixes)) }
                }
            } catch (e: Exception) {
                _homeUiState.update { it.copy(artistMixes = SectionState.Error("Error al cargar mixes")) }
            }
        }
    }

    private fun loadLocalTrends() {
        viewModelScope.launch {
            _homeUiState.update { it.copy(localTrends = SectionState.Loading) }
            try {
                val result = streamRepository.getTrending()
                when (result) {
                    is Resource.Success -> {
                        if (result.data.isNullOrEmpty()) {
                            _homeUiState.update { it.copy(localTrends = SectionState.Empty) }
                        } else {
                            val taking = result.data.take(15)
                            _homeUiState.update { it.copy(localTrends = SectionState.Success(taking)) }
                        }
                    }
                    is Resource.Error -> {
                        _homeUiState.update { it.copy(localTrends = SectionState.Error(result.message ?: "Error de red")) }
                    }
                    else -> {}
                }
            } catch (e: Exception) {
                _homeUiState.update { it.copy(localTrends = SectionState.Error("Error inesperado")) }
            }
        }
    }

    private fun loadRecommendationsV2() {
        viewModelScope.launch {
            _homeUiState.update { it.copy(recommendations = SectionState.Loading) }
            try {
                val favs = favoritesRepository.getFavorites().first()
                if (favs.isNotEmpty()) {
                    val seed = favs.random()
                    val result = streamRepository.getRelatedSongs(seed.id)
                    when (result) {
                        is Resource.Success -> {
                            if (result.data.isNullOrEmpty()) {
                                _homeUiState.update { it.copy(recommendations = SectionState.Empty) }
                            } else {
                                val favIds = favs.map { it.id }.toSet()
                                val filtered = result.data.filter { !favIds.contains(it.id) }.take(15)
                                _homeUiState.update { it.copy(recommendations = SectionState.Success(filtered)) }
                            }
                        }
                        is Resource.Error -> _homeUiState.update { it.copy(recommendations = SectionState.Error("Error al recomendar")) }
                        else -> {}
                    }
                } else {
                    val result = streamRepository.getDiscovery()
                    if (result is Resource.Success && !result.data.isNullOrEmpty()) {
                        _homeUiState.update { it.copy(recommendations = SectionState.Success(result.data.take(15))) }
                    } else {
                        _homeUiState.update { it.copy(recommendations = SectionState.Empty) }
                    }
                }
            } catch (e: Exception) {
                _homeUiState.update { it.copy(recommendations = SectionState.Error("Error inesperado")) }
            }
        }
    }
}
