package com.auramusic.app.domain.provider.config

import com.auramusic.app.domain.provider.OperationType
import com.auramusic.app.domain.provider.ProviderError

interface ProviderConfigManager {
    fun getFallbackOrder(operation: OperationType): List<String>
}

interface ProviderMetricsLogger {
    fun logSuccess(providerId: String, operation: String, latencyMs: Long)
    fun logError(providerId: String, operation: String, error: ProviderError)
}
