package com.auramusic.app.data.provider.util

import com.auramusic.app.domain.provider.ProviderError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import retrofit2.HttpException
import java.io.IOException

suspend fun <T> executeWithRetry(
    providerId: String,
    timeoutMs: Long = 8000L,
    maxRetries: Int = 1,
    block: suspend () -> T
): Result<T> {
    var currentAttempt = 0
    while (true) {
        try {
            val result = withTimeout(timeoutMs) {
                block()
            }
            return Result.success(result)
        } catch (e: Exception) {
            // A caller cancellation must stop the provider chain immediately.
            // TimeoutCancellationException is ours and is intentionally retried below.
            if (e is CancellationException && e !is TimeoutCancellationException) {
                throw e
            }
            val mappedError = mapToProviderError(providerId, e)
            currentAttempt++
            
            val shouldRetry = mappedError is ProviderError.NetworkIssue && currentAttempt <= maxRetries
            if (!shouldRetry) {
                return Result.failure(mappedError)
            }
            // Exponential backoff
            delay(1000L * currentAttempt)
        }
    }
}

private fun mapToProviderError(providerId: String, e: Exception): ProviderError {
    return when (e) {
        is ProviderError -> e
        is TimeoutCancellationException -> ProviderError.NetworkIssue(providerId, e)
        is IOException -> ProviderError.NetworkIssue(providerId, e)
        is HttpException -> {
            when (e.code()) {
                404 -> ProviderError.NotFound(providerId)
                429 -> ProviderError.RateLimited(providerId)
                else -> ProviderError.FatalError(providerId, "HTTP ${e.code()}: ${e.message()}")
            }
        }
        else -> ProviderError.FatalError(providerId, e.message ?: "Unknown error")
    }
}
