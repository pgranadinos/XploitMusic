package com.xploitmusic.app.data.local

import com.xploitmusic.app.domain.repository.ShareMode
import com.xploitmusic.app.domain.repository.ShareSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class InMemoryShareSettings(initialValue: ShareMode = ShareMode.COVER) : ShareSettings {
    private val _shareMode = MutableStateFlow(initialValue)
    override val shareMode: StateFlow<ShareMode> = _shareMode.asStateFlow()

    override fun setShareMode(mode: ShareMode) {
        _shareMode.value = mode
    }
}
