package com.xploitmusic.app.domain.repository

import kotlinx.coroutines.flow.StateFlow

interface AutoplaySettings {
    val enabled: StateFlow<Boolean>
    fun setEnabled(enabled: Boolean)
    fun toggle()
}
