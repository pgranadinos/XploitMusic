package com.xploitmusic.app.data.repository

import android.net.Uri
import android.util.Log
import com.xploitmusic.app.data.local.SpotifyConfigurationStore
import com.xploitmusic.app.data.local.SpotifyOAuthSessionStore
import com.xploitmusic.app.data.local.dao.SpotifyTokenDao
import com.xploitmusic.app.data.local.entity.SpotifyTokenEntity
import com.xploitmusic.app.data.remote.SpotifyApi
import com.xploitmusic.app.data.remote.SpotifyAuthApi
import com.xploitmusic.app.data.remote.SpotifyPlaylistDto
import com.xploitmusic.app.data.remote.SpotifyTrackDto
import com.xploitmusic.app.data.remote.SpotifyUserProfileDto
import com.xploitmusic.app.domain.repository.SpotifyRepository
import com.xploitmusic.app.domain.model.SpotifyOAuthCallback
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpotifyRepositoryImpl @Inject constructor(
    private val api: SpotifyApi,
    private val authApi: SpotifyAuthApi,
    private val tokenDao: SpotifyTokenDao,
    private val oauthSessionStore: SpotifyOAuthSessionStore,
    private val configurationStore: SpotifyConfigurationStore
) : SpotifyRepository {

    private val refreshMutex = Mutex()
    private val oauthCallbackFlow = MutableSharedFlow<SpotifyOAuthCallback>(extraBufferCapacity = 1)

    companion object {
        private const val TAG = "SpotifyRepo"
        private const val SCOPES = "playlist-read-private playlist-read-collaborative"
        private const val REDIRECT_URI = "xploitmusic://spotify-callback"
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

        fun generateState(): String {
            val bytes = ByteArray(32)
            SecureRandom().nextBytes(bytes)
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        }
    }

    // ─── Auth ────────────────────────────────────────────────────────────────

    override fun isAuthenticated(): Flow<Boolean> =
        tokenDao.observeToken().map { token ->
            token != null && token.accessToken.isNotBlank() && token.refreshToken.isNotBlank()
        }

    override fun observeUserProfile(): Flow<SpotifyTokenEntity?> =
        tokenDao.observeToken()

    override suspend fun buildAuthUrl(): Result<String> = runCatching {
        val clientId = configurationStore.requireClientId()
        val redirectUri = REDIRECT_URI
        val verifier = generateCodeVerifier()
        val challenge = generateCodeChallenge(verifier)
        val state = generateState()
        oauthSessionStore.save(verifier, state, redirectUri)

        Uri.Builder()
            .scheme("https")
            .authority("accounts.spotify.com")
            .path("/authorize")
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("redirect_uri", redirectUri)
            .appendQueryParameter("scope", SCOPES)
            .appendQueryParameter("state", state)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", challenge)
            .build()
            .toString()
    }

    override fun observeOAuthCallbacks(): Flow<SpotifyOAuthCallback> = oauthCallbackFlow.asSharedFlow()

    override fun handleOAuthDeepLink(uri: Uri): Boolean {
        if (uri.scheme != "xploitmusic" || uri.host != "spotify-callback") {
            return false
        }
        val rawError = uri.getQueryParameter("error")
        val friendlyError = when {
            rawError == "access_denied" -> "Autorización cancelada por el usuario"
            !rawError.isNullOrBlank() -> "Spotify rechazó la autorización ($rawError)"
            else -> null
        }
        val callback = SpotifyOAuthCallback(
            code = uri.getQueryParameter("code"),
            state = uri.getQueryParameter("state"),
            error = friendlyError
        )
        oauthCallbackFlow.tryEmit(callback)
        return true
    }

    override suspend fun exchangeCode(code: String, state: String): Result<Unit> {
        return try {
            val session = oauthSessionStore.consume(state)
                ?: throw SecurityException("El callback OAuth no coincide con la sesión iniciada")
            val clientId = configurationStore.requireClientId()
            val response = authApi.exchangeCode(
                code         = code,
                redirectUri  = session.redirectUri,
                clientId     = clientId,
                codeVerifier = session.verifier
            )
            val refreshToken = response.refreshToken
                ?: throw IllegalStateException("Spotify no devolvió refresh_token")
            val expiresAt = System.currentTimeMillis() + (response.expiresIn * 1000L)
            tokenDao.upsertToken(
                SpotifyTokenEntity(
                    accessToken  = response.accessToken,
                    refreshToken = refreshToken,
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

    override suspend fun refreshTokenIfNeeded(): Result<Unit> = refreshToken(force = false)

    private suspend fun refreshToken(force: Boolean): Result<Unit> = refreshMutex.withLock {
        val token = tokenDao.getToken()
            ?: return@withLock Result.failure(IllegalStateException("No hay sesión de Spotify"))
        if (!force && System.currentTimeMillis() < token.expiresAt - REFRESH_THRESHOLD_MS) {
            return@withLock Result.success(Unit)
        }
        if (token.refreshToken.isBlank()) {
            return@withLock Result.failure(IllegalStateException("La sesión no tiene refresh_token"))
        }
        try {
            val response = authApi.refreshToken(
                refreshToken = token.refreshToken,
                clientId = configurationStore.requireClientId()
            )
            tokenDao.upsertToken(
                token.copy(
                    accessToken = response.accessToken,
                    refreshToken = response.refreshToken ?: token.refreshToken,
                    expiresAt = System.currentTimeMillis() + (response.expiresIn * 1000L)
                )
            )
            Log.d(TAG, "Token refreshed — next expiry in ${response.expiresIn}s")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Token refresh failed", e)
            Result.failure(e)
        }
    }

    private suspend fun <T> authenticatedCall(block: suspend () -> T): T {
        refreshToken(force = false).getOrThrow()
        var retriedUnauthorized = false
        var retriedRateLimit = false

        while (true) {
            try {
                return block()
            } catch (error: HttpException) {
                when {
                    error.code() == 401 && !retriedUnauthorized -> {
                        retriedUnauthorized = true
                        refreshToken(force = true).getOrThrow()
                    }
                    error.code() == 429 && !retriedRateLimit -> {
                        retriedRateLimit = true
                        val retrySeconds = error.response()
                            ?.headers()
                            ?.get("Retry-After")
                            ?.toLongOrNull()
                            ?.coerceIn(1L, 30L)
                            ?: 1L
                        delay(retrySeconds * 1000L)
                    }
                    else -> throw error
                }
            }
        }
    }

    override suspend fun fetchAndStoreProfile(): Result<SpotifyUserProfileDto> {
        return try {
            val profile = authenticatedCall { api.getProfile() }
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
                val page = authenticatedCall { api.getPlaylists(limit = 50, offset = offset) }
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
                val page = authenticatedCall {
                    api.getPlaylistItems(playlistId = playlistId, offset = offset)
                }
                page.items
                    .filterNot { it.isLocal }
                    .mapNotNull { it.resolvedItem }
                    .filter {
                        !it.isLocal &&
                            it.id != null &&
                            it.isPlayable != false &&
                            (it.type == null || it.type == "track")
                    }
                    .forEach { all.add(it) }
                if (page.next == null) break
                offset += 50
            }
            Log.d(TAG, "Fetched ${all.size} tracks for playlist $playlistId")
            Result.success(all)
        } catch (e: Exception) {
            Log.e(TAG, "getAllPlaylistTracks failed", e)
            Result.failure(e)
        }
    }

    override suspend fun logout() {
        oauthSessionStore.clear()
        tokenDao.deleteToken()
        Log.d(TAG, "Logged out")
    }
}
