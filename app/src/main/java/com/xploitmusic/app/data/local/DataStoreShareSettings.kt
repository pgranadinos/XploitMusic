package com.xploitmusic.app.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.xploitmusic.app.domain.repository.ShareMode
import com.xploitmusic.app.domain.repository.ShareSettings
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
class DataStoreShareSettings @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : ShareSettings {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val keyShare = stringPreferencesKey("share_mode")

    override val shareMode: StateFlow<ShareMode> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val rawValue = preferences[keyShare] ?: ShareMode.COVER.value
            try {
                ShareMode.values().find { it.value == rawValue } ?: ShareMode.COVER
            } catch (_: Exception) {
                ShareMode.COVER
            }
        }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = ShareMode.COVER
        )

    override fun setShareMode(mode: ShareMode) {
        scope.launch {
            dataStore.edit { preferences ->
                preferences[keyShare] = mode.value
            }
        }
    }
}
