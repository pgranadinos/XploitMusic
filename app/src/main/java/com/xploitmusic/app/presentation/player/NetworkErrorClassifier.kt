package com.xploitmusic.app.presentation.player

import androidx.media3.common.PlaybackException
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
            if (throwable is PlaybackException &&
                throwable.errorCode in setOf(
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
                    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
                )
            ) {
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
