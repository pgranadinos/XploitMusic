package com.xploitmusic.app.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/** Conserva la sesión PKCE durante el salto al navegador y un posible reinicio del proceso. */
@Singleton
class SpotifyOAuthSessionStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    data class Session(val verifier: String, val redirectUri: String)

    fun save(verifier: String, state: String, redirectUri: String) {
        preferences.edit()
            .putString(KEY_VERIFIER, verifier)
            .putString(KEY_STATE, state)
            .putString(KEY_REDIRECT_URI, redirectUri)
            .apply()
    }

    fun consume(returnedState: String): Session? {
        val verifier = preferences.getString(KEY_VERIFIER, null)
        val expectedState = preferences.getString(KEY_STATE, null)
        val redirectUri = preferences.getString(KEY_REDIRECT_URI, null)
        clear()

        if (verifier == null || expectedState == null || redirectUri == null) return null
        val stateMatches = MessageDigest.isEqual(
            expectedState.toByteArray(Charsets.UTF_8),
            returnedState.toByteArray(Charsets.UTF_8)
        )
        return Session(verifier, redirectUri).takeIf { stateMatches }
    }

    fun clear() {
        preferences.edit()
            .remove(KEY_VERIFIER)
            .remove(KEY_STATE)
            .remove(KEY_REDIRECT_URI)
            .apply()
    }

    private companion object {
        const val PREFERENCES = "spotify_oauth_session"
        const val KEY_VERIFIER = "pkce_verifier"
        const val KEY_STATE = "oauth_state"
        const val KEY_REDIRECT_URI = "redirect_uri"
    }
}
