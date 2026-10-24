package com.xploitmusic.app.presentation.player

import com.xploitmusic.app.domain.provider.ProviderError
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

object NetworkErrorClassifier {
    fun isNetworkError(throwable: Throwable?, textFallback: String? = null): Boolean {
        if (throwable != null) {
            if (throwable is ProviderError.NetworkIssue ||
                throwable is UnknownHostException ||
                throwable is SocketTimeoutException ||
                throwable is IOException) {
                return true
            }
        }
        if (!textFallback.isNullOrBlank()) {
            val lower = textFallback.lowercase()
            return lower.contains("no se pudo resolver") ||
                   lower.contains("unable to resolve host") ||
                   lower.contains("network or timeout") ||
                   lower.contains("sin conexión")
        }
        return false
    }
}
