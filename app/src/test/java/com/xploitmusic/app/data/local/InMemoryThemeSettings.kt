package com.xploitmusic.app.data.local

import com.xploitmusic.app.core.theme.AppTheme
import com.xploitmusic.app.domain.repository.ThemeSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class InMemoryThemeSettings(initialValue: AppTheme = AppTheme.NOCHE) : ThemeSettings {
    private val _themeMode = MutableStateFlow(initialValue)
    override val themeMode: StateFlow<AppTheme> = _themeMode.asStateFlow()

    override fun setThemeMode(theme: AppTheme) {
        _themeMode.value = theme
    }
}
