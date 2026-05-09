package com.auramusic.app.data.provider.config

import com.auramusic.app.domain.provider.OperationType
import com.auramusic.app.domain.provider.config.ProviderConfigManager
import javax.inject.Inject

class DefaultProviderConfigManager @Inject constructor() : ProviderConfigManager {
    override fun getFallbackOrder(operation: OperationType): List<String> {
        return when(operation) {
            // Piped primero: funciona directo desde el APK sin servidor externo.
            // El BFF es el último recurso (solo disponible cuando el servidor está corriendo).
            OperationType.GET_STREAM -> listOf("PIPED", "INVIDIOUS", "AURA_BFF")
            OperationType.SEARCH -> listOf("PIPED", "INVIDIOUS", "AURA_BFF")
            OperationType.GET_RELATED -> listOf("PIPED", "INVIDIOUS", "AURA_BFF")
            OperationType.GET_TRENDING -> listOf("PIPED", "INVIDIOUS", "AURA_BFF")
            OperationType.GET_TRACK -> listOf("PIPED", "INVIDIOUS", "AURA_BFF")
        }
    }
}
