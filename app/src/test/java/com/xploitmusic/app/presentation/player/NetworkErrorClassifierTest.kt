package com.xploitmusic.app.presentation.player

import com.google.common.truth.Truth.assertThat
import com.xploitmusic.app.domain.provider.ProviderError
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class NetworkErrorClassifierTest {

    @Test
    fun `UnknownHostException is classified as network error`() {
        val exc = UnknownHostException("Unable to resolve host")
        assertThat(NetworkErrorClassifier.isNetworkError(exc)).isTrue()
    }

    @Test
    fun `SocketTimeoutException is classified as network error`() {
        val exc = SocketTimeoutException("Read timed out")
        assertThat(NetworkErrorClassifier.isNetworkError(exc)).isTrue()
    }

    @Test
    fun `IOException is classified as network error`() {
        val exc = IOException("Broken pipe")
        assertThat(NetworkErrorClassifier.isNetworkError(exc)).isTrue()
    }

    @Test
    fun `ProviderError NetworkIssue is classified as network error`() {
        val exc = ProviderError.NetworkIssue("LOCAL_YOUTUBE", UnknownHostException("host"))
        assertThat(NetworkErrorClassifier.isNetworkError(exc)).isTrue()
    }

    @Test
    fun `plain text not matching network indicators is NOT classified as network error`() {
        assertThat(NetworkErrorClassifier.isNetworkError(null, "HTTP 404 - Not found")).isFalse()
        assertThat(NetworkErrorClassifier.isNetworkError(null, "Failed to parse JSON")).isFalse()
        assertThat(NetworkErrorClassifier.isNetworkError(null, "Content restricted")).isFalse()
    }
}
