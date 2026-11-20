package com.xploitmusic.app.domain.repository

import kotlinx.coroutines.flow.StateFlow

enum class ShareMode(val value: String) {
    COVER("COVER"),
    AUDIO("AUDIO")
}

interface ShareSettings {
    val shareMode: StateFlow<ShareMode>
    fun setShareMode(mode: ShareMode)
}
