package com.xploitmusic.app.presentation.player

import androidx.media3.common.PlaybackException
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
    fun `PlaybackException network connection failed code is classified as network error`() {
        val exc = PlaybackException("Network connection failed", null, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)
        assertThat(NetworkErrorClassifier.isNetworkError(exc)).isTrue()
    }

    @Test
    fun `PlaybackException decoding failed code is NOT classified as network error`() {
        val exc = PlaybackException("Decoding failed", null, PlaybackException.ERROR_CODE_DECODING_FAILED)
        assertThat(NetworkErrorClassifier.isNetworkError(exc)).isFalse()
    }

    @Test
    fun `plain text not matching network indicators is NOT classified as network error`() {
        assertThat(NetworkErrorClassifier.isNetworkError(null, "HTTP 404 - Not found")).isFalse()
        assertThat(NetworkErrorClassifier.isNetworkError(null, "Failed to parse JSON")).isFalse()
        assertThat(NetworkErrorClassifier.isNetworkError(null, "Content restricted")).isFalse()
    }
}
