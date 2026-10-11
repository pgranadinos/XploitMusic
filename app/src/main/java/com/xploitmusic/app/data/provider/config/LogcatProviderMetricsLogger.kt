package com.xploitmusic.app.data.provider.config

import android.util.Log
import com.xploitmusic.app.domain.provider.ProviderError
import com.xploitmusic.app.domain.provider.config.ProviderMetricsLogger
import javax.inject.Inject

class LogcatProviderMetricsLogger @Inject constructor() : ProviderMetricsLogger {
    companion object {
        private const val TAG = "ProviderMetrics"
    }

    override fun logSuccess(providerId: String, operation: String, latencyMs: Long) {
        Log.d(TAG, "SUCCESS | Provider: $providerId | Op: $operation | Latency: ${latencyMs}ms")
    }

    override fun logError(providerId: String, operation: String, error: ProviderError) {
        Log.e(TAG, "ERROR | Provider: $providerId | Op: $operation | Type: ${error.javaClass.simpleName} | Msg: ${error.message}")
    }
}
