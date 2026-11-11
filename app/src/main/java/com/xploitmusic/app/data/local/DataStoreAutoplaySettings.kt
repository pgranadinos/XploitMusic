package com.xploitmusic.app.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.xploitmusic.app.domain.repository.AutoplaySettings
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
class DataStoreAutoplaySettings @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : AutoplaySettings {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val keyAutoplay = booleanPreferencesKey("isAutoplayEnabled")

    override val enabled: StateFlow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[keyAutoplay] ?: true
        }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = true
        )

    override fun setEnabled(enabled: Boolean) {
        scope.launch {
            dataStore.edit { preferences ->
                preferences[keyAutoplay] = enabled
            }
        }
    }

    override fun toggle() {
        scope.launch {
            dataStore.edit { preferences ->
                val current = preferences[keyAutoplay] ?: true
                preferences[keyAutoplay] = !current
            }
        }
    }
}
