package com.auramusic.app.data.repository

import android.net.Uri
import android.util.Log
import com.auramusic.app.BuildConfig
import com.auramusic.app.data.local.dao.SpotifyTokenDao
import com.auramusic.app.data.local.entity.SpotifyTokenEntity
import com.auramusic.app.data.remote.SpotifyApi
import com.auramusic.app.data.remote.SpotifyAuthApi
import com.auramusic.app.data.remote.SpotifyPlaylistDto
import com.auramusic.app.data.remote.SpotifyTrackDto
import com.auramusic.app.data.remote.SpotifyUserProfileDto
import com.auramusic.app.domain.repository.SpotifyRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpotifyRepositoryImpl @Inject constructor(
    private val api: SpotifyApi,
    private val authApi: SpotifyAuthApi,
    private val tokenDao: SpotifyTokenDao
) : SpotifyRepository {

    companion object {
        private const val TAG = "SpotifyRepo"
        private const val REDIRECT_URI = "auramusic://spotify/callback"
        private const val SCOPES = "user-read-email playlist-read-private playlist-read-collaborative"
        /** Renovar si faltan menos de 60 segundos */
        private const val REFRESH_THRESHOLD_MS = 60_000L

        // Genera un code_verifier PKCE aleatorio (43-128 chars, URL-safe)
        fun generateCodeVerifier(): String {
            val bytes = ByteArray(96)
            SecureRandom().nextBytes(bytes)
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        }

        // SHA-256 del verifier, codificado en Base64 URL-safe
        fun generateCodeChallenge(verifier: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(verifier.toByteArray(Charsets.US_ASCII))
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash)
        }
    }

    // ─── Auth ────────────────────────────────────────────────────────────────

    override fun isAuthenticated(): Flow<Boolean> =
        tokenDao.observeToken().map { it != null }

    override fun observeUserProfile(): Flow<SpotifyTokenEntity?> =
        tokenDao.observeToken()

    override fun buildAuthUrl(): Pair<String, String> {
        val verifier = generateCodeVerifier()
        val challenge = generateCodeChallenge(verifier)

        val url = Uri.Builder()
            .scheme("https")
            .authority("accounts.spotify.com")
            .path("/authorize")
            .appendQueryParameter("response_type",         "code")
            .appendQueryParameter("client_id",             BuildConfig.SPOTIFY_CLIENT_ID)
            .appendQueryParameter("redirect_uri",          REDIRECT_URI)
            .appendQueryParameter("scope",                 SCOPES)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge",        challenge)
            .build()
            .toString()

        return Pair(url, verifier)
    }

    override suspend fun exchangeCode(code: String, codeVerifier: String): Result<Unit> {
        return try {
            val response = authApi.exchangeCode(
                code         = code,
                redirectUri  = REDIRECT_URI,
                clientId     = BuildConfig.SPOTIFY_CLIENT_ID,
                codeVerifier = codeVerifier
            )
            val expiresAt = System.currentTimeMillis() + (response.expiresIn * 1000L)
            tokenDao.upsertToken(
                SpotifyTokenEntity(
                    accessToken  = response.accessToken,
                    refreshToken = response.refreshToken ?: "",
                    expiresAt    = expiresAt
                )
            )
            Log.d(TAG, "exchangeCode OK — expires in ${response.expiresIn}s")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "exchangeCode failed", e)
            Result.failure(e)
        }
    }

    override suspend fun refreshTokenIfNeeded(): Result<Unit> {
        val token = tokenDao.getToken() ?: return Result.success(Unit) // no session
        if (System.currentTimeMillis() < token.expiresAt - REFRESH_THRESHOLD_MS) {
            return Result.success(Unit) // still valid
        }
        return try {
            val response = authApi.refreshToken(
                refreshToken = token.refreshToken,
                clientId     = BuildConfig.SPOTIFY_CLIENT_ID
            )
            val expiresAt = System.currentTimeMillis() + (response.expiresIn * 1000L)
            tokenDao.upsertToken(
                token.copy(
                    accessToken = response.accessToken,
                    // Spotify solo devuelve nuevo refresh_token si lo rotó
                    refreshToken = response.refreshToken ?: token.refreshToken,
                    expiresAt    = expiresAt
                )
            )
            Log.d(TAG, "Token refreshed — next expiry in ${response.expiresIn}s")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Token refresh failed", e)
            Result.failure(e)
        }
    }

    override suspend fun fetchAndStoreProfile(): Result<SpotifyUserProfileDto> {
        return try {
            val profile = api.getProfile()
            val token = tokenDao.getToken()
            if (token != null) {
                tokenDao.upsertToken(
                    token.copy(
                        spotifyUserId = profile.id,
                        displayName   = profile.displayName ?: "",
                        avatarUrl     = profile.images?.firstOrNull()?.url ?: ""
                    )
                )
            }
            Result.success(profile)
        } catch (e: Exception) {
            Log.e(TAG, "fetchProfile failed", e)
            Result.failure(e)
        }
    }

    // ─── Playlists ───────────────────────────────────────────────────────────

    override suspend fun getAllPlaylists(): Result<List<SpotifyPlaylistDto>> {
        return try {
            val all = mutableListOf<SpotifyPlaylistDto>()
            var offset = 0
            while (true) {
                val page = api.getPlaylists(limit = 50, offset = offset)
                all.addAll(page.items)
                if (page.next == null) break
                offset += 50
            }
            Log.d(TAG, "Fetched ${all.size} playlists")
            Result.success(all)
        } catch (e: Exception) {
            Log.e(TAG, "getAllPlaylists failed", e)
            Result.failure(e)
        }
    }

    // ─── Tracks ──────────────────────────────────────────────────────────────

    override suspend fun getAllPlaylistTracks(playlistId: String): Result<List<SpotifyTrackDto>> {
        return try {
            val all = mutableListOf<SpotifyTrackDto>()
            var offset = 0
            while (true) {
                val page = api.getPlaylistTracks(playlistId = playlistId, offset = offset)
                page.items
                    .mapNotNull { it.track }
                    .filter { !it.isLocal && it.id != null } // ignorar tracks locales/eliminados
                    .forEach { all.add(it) }
                if (page.next == null) break
                offset += 100
            }
            Log.d(TAG, "Fetched ${all.size} tracks for playlist $playlistId")
            Result.success(all)
        } catch (e: Exception) {
            Log.e(TAG, "getAllPlaylistTracks failed", e)
            Result.failure(e)
        }
    }

    override suspend fun logout() {
        tokenDao.deleteToken()
        Log.d(TAG, "Logged out")
    }
}
