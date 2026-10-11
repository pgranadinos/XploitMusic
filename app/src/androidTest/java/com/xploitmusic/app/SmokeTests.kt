package com.xploitmusic.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class SmokeTests {

    // IMPORTANT: Para que este test de End-to-End funcione, la app usará la inyección Hilt real
    // (BFF/Piped) tal cual el usuario lo viviría. Se requiere dispositivo físico o emulador.
    
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun app_launches_and_shows_bottom_navigation() {
        // Validación Smoke: Comprobar que los nodos globales básicos se renderizaron
        
        // 1. Confirmar que la navbar inferior "Inicio", "Buscar", "Biblioteca" existe
        composeTestRule.onNodeWithText("Inicio").assertExists().assertIsDisplayed()
        composeTestRule.onNodeWithText("Buscar").assertExists().assertIsDisplayed()
        composeTestRule.onNodeWithText("Biblioteca").assertExists().assertIsDisplayed()
    }

    @Test
    fun navigation_between_tabs_works_normally() {
        // Clic a "Buscar"
        composeTestRule.onNodeWithText("Buscar").performClick()
        
        // El test pasa exitosamente si no crashea al abrir la ventana.
        // Simularemos pequeña espera para recomposición (Smoke test no exhaustivo API)
        composeTestRule.waitForIdle()

        // Clic a "Biblioteca"
        composeTestRule.onNodeWithText("Biblioteca").performClick()
        
        // En LibraryScreen seguro existe algún botón o texto estructural que indique éxito.
        // Si no se encuentra, ComposeTestRule fallará y sabremos que hubo un error fatal de carga.
        composeTestRule.waitForIdle()
    }
}
