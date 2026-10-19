package com.xploitmusic.app.data.repository

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.xploitmusic.app.data.local.SpotifyOAuthSessionStore
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import android.net.Uri
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class SpotifyCallbackTest {

    private lateinit var sessionStore: SpotifyOAuthSessionStore
    private lateinit var repo: SpotifyRepositoryImpl

    @Before
    fun setup() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("spotify_oauth_session", 0).edit().clear().commit()
        sessionStore = SpotifyOAuthSessionStore(context)
        repo = SpotifyRepositoryImpl(
            api = mockk(relaxed = true),
            authApi = mockk(relaxed = true),
            tokenDao = mockk(relaxed = true),
            oauthSessionStore = sessionStore,
            configurationStore = mockk(relaxed = true)
        )
    }

    @Test
    fun `accepts callback when state is correct`() = runTest {
        sessionStore.save("verifier", "correct_state", "xploitmusic://spotify-callback")

        val uri = Uri.parse("xploitmusic://spotify-callback?code=valid_code&state=correct_state")
        assertThat(repo.handleOAuthDeepLink(uri)).isTrue()

        repo.observeOAuthCallbacks().test {
            val callback = awaitItem()
            assertThat(callback.code).isEqualTo("valid_code")
            assertThat(callback.state).isEqualTo("correct_state")
        }
    }

    @Test
    fun `rejects callback when state is incorrect`() = runTest {
        sessionStore.save("verifier", "correct_state", "xploitmusic://spotify-callback")

        val uri = Uri.parse("xploitmusic://spotify-callback?code=valid_code&state=wrong_state")
        assertThat(repo.handleOAuthDeepLink(uri)).isFalse()

        repo.observeOAuthCallbacks().test {
            expectNoEvents()
        }
    }

    @Test
    fun `rejects callback when there is no pending login session`() = runTest {
        // No session saved
        val uri = Uri.parse("xploitmusic://spotify-callback?code=valid_code&state=random_state")
        assertThat(repo.handleOAuthDeepLink(uri)).isFalse()

        repo.observeOAuthCallbacks().test {
            expectNoEvents()
        }
    }

    @Test
    fun `rejects late callback after login session was cancelled or cleared`() = runTest {
        sessionStore.save("verifier", "my_state", "xploitmusic://spotify-callback")
        
        // Cancel login (clears session store)
        sessionStore.clear()

        val uri = Uri.parse("xploitmusic://spotify-callback?code=late_code&state=my_state")
        assertThat(repo.handleOAuthDeepLink(uri)).isFalse()

        repo.observeOAuthCallbacks().test {
            expectNoEvents()
        }
    }
}
