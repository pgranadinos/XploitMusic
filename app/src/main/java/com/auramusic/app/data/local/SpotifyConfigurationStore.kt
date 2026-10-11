package com.auramusic.app.data.local

import android.content.Context
import com.auramusic.app.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** El Client ID es público; se guarda localmente para no exigir recompilar la app. */
@Singleton
class SpotifyConfigurationStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val _clientId = MutableStateFlow(
        preferences.getString(KEY_CLIENT_ID, null)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: BuildConfig.SPOTIFY_CLIENT_ID.trim()
    )

    val clientId: StateFlow<String> = _clientId

    fun saveClientId(value: String): Result<Unit> {
        val normalized = value.trim()
        if (!CLIENT_ID_PATTERN.matches(normalized)) {
            return Result.failure(
                IllegalArgumentException("El Client ID de Spotify no tiene un formato válido")
            )
        }
        preferences.edit().putString(KEY_CLIENT_ID, normalized).apply()
        _clientId.value = normalized
        return Result.success(Unit)
    }

    fun requireClientId(): String = clientId.value.takeIf { it.isNotBlank() }
        ?: throw IllegalStateException("Introduce el Client ID de Spotify")

    companion object {
        /** Se registra una vez en Spotify Dashboard; el puerto se asigna dinámicamente. */
        const val REGISTERED_REDIRECT_URI = "http://127.0.0.1/spotify/callback"

        private const val PREFERENCES = "spotify_configuration"
        private const val KEY_CLIENT_ID = "client_id"
        private val CLIENT_ID_PATTERN = Regex("^[A-Za-z0-9]{16,128}$")
    }
}
