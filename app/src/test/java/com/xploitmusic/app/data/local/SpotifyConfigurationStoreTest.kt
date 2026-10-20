package com.xploitmusic.app.data.local

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class SpotifyConfigurationStoreTest {
    private lateinit var store: SpotifyConfigurationStore

    @Before
    fun setup() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("spotify_configuration", 0).edit().clear().commit()
        store = SpotifyConfigurationStore(context)
    }

    @Test
    fun `saves valid client id for subsequent sessions`() {
        val clientId = "1234567890abcdef1234567890abcdef"

        assertThat(store.saveClientId(clientId).isSuccess).isTrue()
        assertThat(store.requireClientId()).isEqualTo(clientId)
    }

    @Test
    fun `rejects client secret style or malformed values`() {
        val initialValue = store.clientId.value
        assertThat(store.saveClientId("short").isFailure).isTrue()
        assertThat(store.clientId.value).isEqualTo(initialValue)
    }
}
