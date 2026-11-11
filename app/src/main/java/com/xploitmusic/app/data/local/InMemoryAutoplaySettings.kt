package com.xploitmusic.app.data.local

import com.xploitmusic.app.domain.repository.AutoplaySettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class InMemoryAutoplaySettings(initialValue: Boolean = true) : AutoplaySettings {
    private val _enabled = MutableStateFlow(initialValue)
    override val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    override fun setEnabled(enabled: Boolean) {
        _enabled.value = enabled
    }

    override fun toggle() {
        _enabled.update { !it }
    }
}
