package com.auramusic.app.data.local

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class SpotifyOAuthSessionStoreTest {

    private lateinit var store: SpotifyOAuthSessionStore

    @Before
    fun setup() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("spotify_oauth_session", 0).edit().clear().commit()
        store = SpotifyOAuthSessionStore(context)
    }

    @Test
    fun `consumes verifier only once when state matches`() {
        store.save(
            verifier = "verifier",
            state = "expected-state",
            redirectUri = "http://127.0.0.1:12345/spotify/callback"
        )

        val session = store.consume("expected-state")
        assertThat(session?.verifier).isEqualTo("verifier")
        assertThat(session?.redirectUri).contains(":12345/")
        assertThat(store.consume("expected-state")).isNull()
    }

    @Test
    fun `rejects mismatched state and clears session`() {
        store.save(
            verifier = "verifier",
            state = "expected-state",
            redirectUri = "http://127.0.0.1:12345/spotify/callback"
        )

        assertThat(store.consume("attacker-state")).isNull()
        assertThat(store.consume("expected-state")).isNull()
    }
}
