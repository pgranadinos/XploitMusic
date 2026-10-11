package com.auramusic.app.core.network

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.URL

@RunWith(RobolectricTestRunner::class)
class SpotifyOAuthLoopbackServerTest {
    private val server = SpotifyOAuthLoopbackServer()

    @After
    fun tearDown() {
        server.stop()
    }

    @Test
    fun `receives authorization callback on dynamic loopback port`() = runBlocking {
        val callback = async {
            withTimeout(5_000) { server.callbacks.first() }
        }
        val redirectUri = server.start().getOrThrow()

        val response = withContext(Dispatchers.IO) {
            URL("$redirectUri?code=authorization-code&state=expected-state").readText()
        }
        val result = callback.await()

        assertThat(redirectUri).startsWith("http://127.0.0.1:")
        assertThat(response).contains("Volver a XploitMusic")
        assertThat(result.code).isEqualTo("authorization-code")
        assertThat(result.state).isEqualTo("expected-state")
    }
}
