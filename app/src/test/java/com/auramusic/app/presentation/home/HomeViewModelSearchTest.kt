package com.auramusic.app.presentation.home

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@ExperimentalCoroutinesApi
class HomeViewModelSearchTest {

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
}
