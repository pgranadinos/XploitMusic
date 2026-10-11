package com.xploitmusic.app.domain.provider.config

import com.xploitmusic.app.domain.provider.OperationType
import com.xploitmusic.app.domain.provider.ProviderError

interface ProviderConfigManager {
    fun getFallbackOrder(operation: OperationType): List<String>
}

interface ProviderMetricsLogger {
    fun logSuccess(providerId: String, operation: String, latencyMs: Long)
    fun logError(providerId: String, operation: String, error: ProviderError)
}
