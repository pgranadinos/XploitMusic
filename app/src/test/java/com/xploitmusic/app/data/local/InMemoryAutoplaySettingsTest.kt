package com.xploitmusic.app.data.local

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class InMemoryAutoplaySettingsTest {

    @Test
    fun `in memory autoplay settings default value is true`() {
        val settings = InMemoryAutoplaySettings(initialValue = true)
        assertThat(settings.enabled.value).isTrue()
    }

    @Test
    fun `in memory autoplay settings setEnabled false updates value`() {
        val settings = InMemoryAutoplaySettings(initialValue = true)
        settings.setEnabled(false)
        assertThat(settings.enabled.value).isFalse()
    }

    @Test
    fun `in memory autoplay settings setEnabled true updates value`() {
        val settings = InMemoryAutoplaySettings(initialValue = false)
        settings.setEnabled(true)
        assertThat(settings.enabled.value).isTrue()
    }
}
