package com.xploitmusic.app.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.xploitmusic.app.core.theme.AppTheme
import com.xploitmusic.app.domain.repository.ThemeSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataStoreThemeSettings @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : ThemeSettings {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val keyTheme = stringPreferencesKey("theme_mode")

    override val themeMode: StateFlow<AppTheme> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val rawName = preferences[keyTheme] ?: AppTheme.NOCHE.name
            try {
                AppTheme.valueOf(rawName)
            } catch (_: Exception) {
                AppTheme.NOCHE
            }
        }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = AppTheme.NOCHE
        )

    override fun setThemeMode(theme: AppTheme) {
        scope.launch {
            dataStore.edit { preferences ->
                preferences[keyTheme] = theme.name
            }
        }
    }
}
