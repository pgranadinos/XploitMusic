package com.auramusic.app.domain.repository

import com.auramusic.app.data.local.entity.SpotifyTokenEntity
import com.auramusic.app.data.remote.SpotifyPlaylistDto
import com.auramusic.app.data.remote.SpotifyTrackDto
import com.auramusic.app.data.remote.SpotifyUserProfileDto
import kotlinx.coroutines.flow.Flow

interface SpotifyRepository {

    /** Emite true mientras haya un token válido en Room */
    fun isAuthenticated(): Flow<Boolean>

    /** Perfil del usuario (display name, avatar) para mostrar en la UI */
    fun observeUserProfile(): Flow<SpotifyTokenEntity?>

    /**
     * Genera la URL OAuth PKCE que debe abrirse en Custom Tabs.
     * También almacena internamente el code_verifier para el exchange posterior.
     * @return Pair(authUrl, codeVerifier)
     */
    fun buildAuthUrl(): Pair<String, String>

    /**
     * Intercambia el authorization code por access_token + refresh_token.
     * Persiste los tokens en Room.
     */
    suspend fun exchangeCode(code: String, codeVerifier: String): Result<Unit>

    /** Refresca el access_token si está a <60s de expirar */
    suspend fun refreshTokenIfNeeded(): Result<Unit>

    /** Recupera el perfil del usuario de Spotify y actualiza Room */
    suspend fun fetchAndStoreProfile(): Result<SpotifyUserProfileDto>

    /**
     * Obtiene TODAS las playlists del usuario, paginando automáticamente.
     * Spotify devuelve máx 50 por página.
     */
    suspend fun getAllPlaylists(): Result<List<SpotifyPlaylistDto>>

    /**
     * Obtiene TODOS los tracks de una playlist, paginando automáticamente.
     * Filtra tracks locales (is_local=true) y nulos.
     */
    suspend fun getAllPlaylistTracks(playlistId: String): Result<List<SpotifyTrackDto>>

    /** Elimina el token de Room (logout completo) */
    suspend fun logout()
}
