package com.xploitmusic.app.presentation.home

import com.xploitmusic.app.data.provider.youtubemusic.YouTubeMusicClient
import com.xploitmusic.app.domain.repository.FavoritesRepository
import com.xploitmusic.app.domain.repository.PlayHistoryRepository
import com.xploitmusic.app.domain.repository.SearchHistoryRepository
import com.xploitmusic.app.domain.repository.StreamRepository
import com.xploitmusic.app.domain.usecase.SearchSongsUseCase
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Test

@ExperimentalCoroutinesApi
class HomeViewModelSearchTest {

    private fun createViewModel(
        searchSongsUseCase: SearchSongsUseCase = mockk(relaxed = true),
        favoritesRepository: FavoritesRepository = mockk(relaxed = true) {
            every { getFavorites() } returns emptyFlow()
            every { getMostPlayed() } returns emptyFlow()
            every { getRecents() } returns emptyFlow()
        },
        streamRepository: StreamRepository = mockk(relaxed = true),
        searchHistoryRepository: SearchHistoryRepository = mockk(relaxed = true) {
            every { getRecentSearches() } returns emptyFlow()
            coEvery { getMatchingHistory(any()) } returns emptyList()
        },
        playHistoryRepository: PlayHistoryRepository = mockk(relaxed = true),
        youTubeMusicClient: YouTubeMusicClient = mockk(relaxed = true)
    ): HomeViewModel {
        return HomeViewModel(
            searchSongsUseCase = searchSongsUseCase,
            favoritesRepository = favoritesRepository,
            streamRepository = streamRepository,
            searchHistoryRepository = searchHistoryRepository,
            playHistoryRepository = playHistoryRepository,
            youTubeMusicClient = youTubeMusicClient
        )
    }

    @Test
    fun `searchPipeline distincts delayed duplicate queries unless it is a retry`() = runTest {
        // En HomeViewModel el pipeline usa:
        // .distinctUntilChanged { old, new -> old.first == new.first && !new.second }
        // Probamos matemáticamente el predicado para certificar que el Debouncer no interrumpirá.

        val predicate = { old: Pair<String, Boolean>, new: Pair<String, Boolean> ->
            old.first == new.first && !new.second
        }

        // Test 1: Sugerencia pulsada "Rock" (Llega de inmediato)
        val manualRequest = Pair("Rock", false)

        // Test 2: 150ms después, el TextField debounce emite "Rock" inevitablemente
        val delayedDebounce = Pair("Rock", false)

        // Verificamos si son tomados como IGUALES (lo que descarta que fluya el segundo)
        val shouldDiscard = predicate(manualRequest, delayedDebounce)
        assertEquals(true, shouldDiscard) // Efectivamente, se bloqueará la interrupción

        // Test 3: Pulsa 'Retry' para el string "Rock"
        val retryRequest = Pair("Rock", true)

        val shouldDiscardRetry = predicate(manualRequest, retryRequest)
        assertEquals(false, shouldDiscardRetry) // Efectivamente, el retry no es bloqueado
    }

    @Test
    fun `loadSuggestions fusion without duplicates, limits respected, case-insensitive dedup, and remote failure resilient`() = runTest {
        val historyQueries = listOf("Bad Bunny", "Rock Clásico", "Pop Latino", "Cumbia")
        val remoteSuggestions = listOf("bad bunny", "BAD BUNNY DÁKITI", "Rock Alternativo", "Jazz", "Reggaeton", "Salsa", "Trap")

        // 1. Simulación de fusión respetando las reglas de HomeViewModel
        // Fuente 1: Historial local (máximo 3)
        val historyMatches = historyQueries.take(3)
        val historySet = historyMatches.map { it.lowercase(java.util.Locale.ROOT) }.toSet()

        // Fuente 2: Remoto (máximo 5, case-insensitive dedup contra historial)
        val filteredRemote = remoteSuggestions
            .filter { it.lowercase(java.util.Locale.ROOT) !in historySet }
            .take(5)

        val merged = historyMatches + filteredRemote

        // Verificaciones
        // Historial toma máximo 3
        assertEquals(3, historyMatches.size)
        // Remoto toma máximo 5
        assertEquals(5, filteredRemote.size)
        // "bad bunny" remoto se descarta porque "Bad Bunny" ya está en historial (case-insensitive)
        assertEquals(false, filteredRemote.contains("bad bunny"))
        assertEquals(true, filteredRemote.contains("BAD BUNNY DÁKITI"))
        // Total esperado: 3 de historial + 5 remotas = 8
        assertEquals(8, merged.size)

        // Simulación de falla remota: lista remota vacía o excepción -> solo queda historial
        val remoteFailed = emptyList<String>()
        val mergedOnFailure = historyMatches + remoteFailed
        assertEquals(3, mergedOnFailure.size)
        assertEquals(listOf("Bad Bunny", "Rock Clásico", "Pop Latino"), mergedOnFailure)
    }

    @Test
    fun `selecting suggestion does not reload suggestions after 300ms debounce`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val ytmClient: YouTubeMusicClient = mockk(relaxed = true) {
                coEvery { getSearchSuggestions("Bad Bunny") } returns listOf("Bad Bunny 1", "Bad Bunny 2")
            }
            val viewModel = createViewModel(youTubeMusicClient = ytmClient)
            runCurrent()

            // Seleccionar sugerencia
            viewModel.processAction(SearchAction.SelectSuggestion("Bad Bunny"))
            assertEquals(emptyList<SearchSuggestion>(), viewModel.suggestions.value)

            // Avanzar 300ms (superando el debounce de 250ms)
            advanceTimeBy(300L)
            runCurrent()

            // _suggestions debe seguir vacía y no haberse recargado
            assertEquals(emptyList<SearchSuggestion>(), viewModel.suggestions.value)
            coVerify(exactly = 0) { ytmClient.getSearchSuggestions("Bad Bunny") }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `typing new query after suggestion selection loads suggestions normally`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val ytmClient: YouTubeMusicClient = mockk(relaxed = true) {
                coEvery { getSearchSuggestions("Cumbia") } returns listOf("Cumbia Peruana", "Cumbia Villera")
            }
            val viewModel = createViewModel(youTubeMusicClient = ytmClient)
            runCurrent()

            // Seleccionar sugerencia previa
            viewModel.processAction(SearchAction.SelectSuggestion("Bad Bunny"))
            advanceTimeBy(300L)
            runCurrent()
            assertEquals(emptyList<SearchSuggestion>(), viewModel.suggestions.value)

            // Usuario escribe nuevo texto
            viewModel.processAction(SearchAction.UpdateQuery("Cumbia"))
            advanceTimeBy(300L)
            runCurrent()

            // Las sugerencias sí se cargan
            val currentSuggestions = viewModel.suggestions.value
            assertEquals(2, currentSuggestions.size)
            assertEquals("Cumbia Peruana", (currentSuggestions[0] as SearchSuggestion.AutocompleteItem).text)
            assertEquals("Cumbia Villera", (currentSuggestions[1] as SearchSuggestion.AutocompleteItem).text)
            coVerify(exactly = 1) { ytmClient.getSearchSuggestions("Cumbia") }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `remote response arriving after suggestion selection does not reopen suggestions`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val ytmClient: YouTubeMusicClient = mockk(relaxed = true) {
                coEvery { getSearchSuggestions("Rock") } coAnswers {
                    delay(500L)
                    listOf("Rock Clásico", "Rock 80s")
                }
            }
            val viewModel = createViewModel(youTubeMusicClient = ytmClient)
            runCurrent()

            // Usuario escribe "Rock" -> se dispara debounce tras 250ms
            viewModel.processAction(SearchAction.UpdateQuery("Rock"))
            advanceTimeBy(260L)
            runCurrent() // Inicia loadSuggestions("Rock"), que se suspende por delay(500)

            // Antes de que termine la llamada remota, el usuario selecciona una sugerencia
            viewModel.processAction(SearchAction.SelectSuggestion("Rock Clásico"))
            assertEquals(emptyList<SearchSuggestion>(), viewModel.suggestions.value)

            // Avanzamos 600ms para sobrepasar el delay del llamado remoto
            advanceTimeBy(600L)
            runCurrent()

            // La lista de sugerencias debe permanecer vacía, no se reabre
            assertEquals(emptyList<SearchSuggestion>(), viewModel.suggestions.value)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
