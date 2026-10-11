package com.auramusic.app.data.provider.config

import com.auramusic.app.BuildConfig
import com.auramusic.app.domain.provider.OperationType
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DefaultProviderConfigManagerTest {

    @Test
    fun `does not contact BFF when localhost backend is disabled`() {
        assertThat(BuildConfig.ALLOW_LOCALHOST).isFalse()
        val manager = DefaultProviderConfigManager()

        OperationType.values().forEach { operation ->
            assertThat(manager.getFallbackOrder(operation)).doesNotContain("AURA_BFF")
        }
    }

    @Test
    fun `uses local extraction first for playback`() {
        val providers = DefaultProviderConfigManager()
            .getFallbackOrder(OperationType.GET_STREAM)

        assertThat(providers.first()).isEqualTo("LOCAL_YOUTUBE")
        assertThat(providers).doesNotContain("YOUTUBE_MUSIC")
    }

    @Test
    fun `uses YouTube Music first for catalogue operations`() {
        val manager = DefaultProviderConfigManager()

        assertThat(manager.getFallbackOrder(OperationType.SEARCH).first())
            .isEqualTo("YOUTUBE_MUSIC")
        assertThat(manager.getFallbackOrder(OperationType.GET_RELATED).first())
            .isEqualTo("YOUTUBE_MUSIC")
        assertThat(manager.getFallbackOrder(OperationType.GET_TRENDING).first())
            .isEqualTo("YOUTUBE_MUSIC")
        assertThat(manager.getFallbackOrder(OperationType.GET_TRACK).first())
            .isEqualTo("YOUTUBE_MUSIC")
    }
}
