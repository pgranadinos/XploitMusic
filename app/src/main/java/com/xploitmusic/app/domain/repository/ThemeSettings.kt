package com.xploitmusic.app.domain.repository

import com.xploitmusic.app.core.theme.AppTheme
import kotlinx.coroutines.flow.StateFlow

interface ThemeSettings {
    val themeMode: StateFlow<AppTheme>
    fun setThemeMode(theme: AppTheme)
}
