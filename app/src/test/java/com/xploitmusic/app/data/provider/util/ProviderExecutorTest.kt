package com.xploitmusic.app.data.provider.util

import com.google.common.truth.Truth.assertThat
import com.google.gson.stream.MalformedJsonException
import com.xploitmusic.app.domain.provider.ProviderError
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

@OptIn(ExperimentalCoroutinesApi::class)
class ProviderExecutorTest {

    @Test
    fun `direct MalformedJsonException is mapped to FatalError`() {
        val exc = MalformedJsonException("Use JsonReader.setLenient(true) to accept malformed JSON")
        val error = mapToProviderError("PIPED", exc)

        assertThat(error).isInstanceOf(ProviderError.FatalError::class.java)
        assertThat(error.message).contains("malformed JSON")
    }

    @Test
    fun `MalformedJsonException wrapped in RuntimeException is mapped to FatalError`() {
        val cause = MalformedJsonException("Unexpected token at line 1 column 1")
        val wrapped = RuntimeException("Failed to parse JSON response", cause)
        val error = mapToProviderError("INVIDIOUS", wrapped)

        assertThat(error).isInstanceOf(ProviderError.FatalError::class.java)
    }

    @Test
    fun `MalformedJsonException wrapped in IOException is mapped to FatalError`() {
        val cause = MalformedJsonException("Use JsonReader.setLenient(true) to accept malformed JSON")
        val wrapped = IOException("Corrupted response stream", cause)
        val error = mapToProviderError("PIPED", wrapped)

        assertThat(error).isInstanceOf(ProviderError.FatalError::class.java)
    }

    @Test
    fun `executeWithRetry fails immediately without retries when MalformedJsonException occurs`() = runTest {
        var calls = 0
        val result = executeWithRetry<String>("PIPED", maxRetries = 3) {
            calls++
            throw MalformedJsonException("Use JsonReader.setLenient(true)")
        }

        assertThat(calls).isEqualTo(1)
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(ProviderError.FatalError::class.java)
    }

    @Test
    fun `SocketTimeoutException is mapped to NetworkIssue`() {
        val exc = SocketTimeoutException("Read timed out")
        val error = mapToProviderError("PIPED", exc)

        assertThat(error).isInstanceOf(ProviderError.NetworkIssue::class.java)
    }

    @Test
    fun `UnknownHostException is mapped to NetworkIssue`() {
        val exc = UnknownHostException("Unable to resolve host piped.video")
        val error = mapToProviderError("PIPED", exc)

        assertThat(error).isInstanceOf(ProviderError.NetworkIssue::class.java)
    }
}
