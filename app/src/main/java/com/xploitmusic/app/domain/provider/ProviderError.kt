package com.xploitmusic.app.domain.provider

sealed class ProviderError(open val providerId: String, message: String) : Exception(message) {
    class RateLimited(override val providerId: String) : ProviderError(providerId, "HTTP 429 - Rate limit exceeded on $providerId")
    class NetworkIssue(override val providerId: String, cause: Throwable? = null) : ProviderError(providerId, "Network or timeout issue on $providerId: ${cause?.message}") {
        init { cause?.let { initCause(it) } }
    }
    class NotFound(override val providerId: String) : ProviderError(providerId, "Content not found (HTTP 404) on $providerId")
    class ParseError(override val providerId: String) : ProviderError(providerId, "Failed to parse response from $providerId")
    class FatalError(override val providerId: String, message: String) : ProviderError(providerId, message)
}
