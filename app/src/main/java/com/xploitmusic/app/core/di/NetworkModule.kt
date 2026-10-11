package com.xploitmusic.app.core.di

import android.util.Log
import com.xploitmusic.app.BuildConfig
import com.xploitmusic.app.core.network.NetworkErrorInterceptor
import com.xploitmusic.app.core.network.PipedMirrorInterceptor
import com.xploitmusic.app.core.network.RequestIdInterceptor
import com.xploitmusic.app.data.remote.XploitBffApi
import com.xploitmusic.app.data.remote.InvidiousApi
import com.xploitmusic.app.data.remote.PipedApi
import com.xploitmusic.app.data.remote.LrcLibApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val TAG = "XploitDebug-HTTP"
    private const val DEFAULT_PIPED_BASE_URL = "https://api.piped.private.coffee/"
    private const val DEFAULT_LRCLIB_BASE_URL = "https://lrclib.net/"

    // ── OkHttpClient ─────────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor { message ->
            Log.d(TAG, message)
        }.apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.HEADERS
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        return OkHttpClient.Builder()
            .addInterceptor(RequestIdInterceptor())
            .addInterceptor(NetworkErrorInterceptor())
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    // ── Piped / BFF API ──────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideXploitBffApi(okHttpClient: OkHttpClient): XploitBffApi {
        val baseUrl = safeBaseUrl(
            candidate = BuildConfig.BFF_BASE_URL,
            fallback = DEFAULT_PIPED_BASE_URL
        )

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(XploitBffApi::class.java)
    }

    /**
     * OkHttpClient dedicado para Piped con el interceptor de mirrors.
     * Si un mirror de Piped falla, el interceptor reintenta con el siguiente
     * automáticamente sin que el usuario lo note.
     */
    @Provides
    @Singleton
    fun providePipedApi(okHttpClient: OkHttpClient): PipedApi {
        val pipedClient = okHttpClient.newBuilder()
            .addInterceptor(PipedMirrorInterceptor())
            .build()

        val baseUrl = safeBaseUrl(
            candidate = BuildConfig.PIPED_BASE_URL,
            fallback = "https://${PipedMirrorInterceptor.PRIMARY_HOST}/"
        )

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(pipedClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PipedApi::class.java)
    }

    // ── LrcLib API (Lyrics) ──────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideLrcLibApi(okHttpClient: OkHttpClient): LrcLibApi {
        val baseUrl = safeBaseUrl(
            candidate = BuildConfig.LRCLIB_BASE_URL,
            fallback = DEFAULT_LRCLIB_BASE_URL
        )
        val lyricsClient = okHttpClient.newBuilder()
            .callTimeout(8, TimeUnit.SECONDS)
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(lyricsClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(LrcLibApi::class.java)
    }

    // ── Invidious API (Fallback) ─────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideInvidiousApi(okHttpClient: OkHttpClient): InvidiousApi {
        return Retrofit.Builder()
            .baseUrl("https://invidious.projectsegfau.lt/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(InvidiousApi::class.java)
    }

    // ── Spotify APIs ─────────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideSpotifyAuthApi(okHttpClient: OkHttpClient): com.xploitmusic.app.data.remote.SpotifyAuthApi {
        return Retrofit.Builder()
            .baseUrl("https://accounts.spotify.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(com.xploitmusic.app.data.remote.SpotifyAuthApi::class.java)
    }

    @Provides
    @Singleton
    fun provideSpotifyApi(
        okHttpClient: OkHttpClient,
        spotifyAuthInterceptor: com.xploitmusic.app.core.network.SpotifyAuthInterceptor
    ): com.xploitmusic.app.data.remote.SpotifyApi {
        val spotifyClient = okHttpClient.newBuilder()
            .addInterceptor(spotifyAuthInterceptor)
            .build()

        return Retrofit.Builder()
            .baseUrl("https://api.spotify.com/v1/")
            .client(spotifyClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(com.xploitmusic.app.data.remote.SpotifyApi::class.java)
    }

    // ── URL Validation ───────────────────────────────────────────────────────

    /**
     * Valida y normaliza una URL base para Retrofit.
     *
     * Reglas:
     * - Rechaza URLs vacías o sin esquema http/https.
     * - Normaliza slash final (Retrofit lo requiere).
     * - En RELEASE: rechaza cualquier HTTP (cleartext).
     * - En DEBUG: permite HTTP **solo** para 10.0.2.2, 127.0.0.1 y localhost.
     * - Si la URL es inválida, usa el [fallback] HTTPS en vez de crashear.
     */
    private fun safeBaseUrl(candidate: String, fallback: String): String {
        val trimmed = candidate.trim()

        if (trimmed.isBlank()) {
            Log.w(TAG, "Empty base URL. Using fallback: $fallback")
            return fallback
        }

        val normalized = if (trimmed.endsWith("/")) trimmed else "$trimmed/"
        val isHttps = normalized.startsWith("https://")
        val isHttp = normalized.startsWith("http://")

        if (!isHttps && !isHttp) {
            Log.w(TAG, "Invalid base URL scheme: $trimmed. Using fallback: $fallback")
            return fallback
        }

        // Release: rechazar HTTP siempre
        if (!BuildConfig.DEBUG && isHttp) {
            Log.w(TAG, "HTTP rejected in release. Using fallback: $fallback")
            return fallback
        }

        // Debug: permitir HTTP solo para emulador/localhost
        if (BuildConfig.DEBUG && isHttp) {
            val isAllowedLocal =
                normalized.startsWith("http://10.0.2.2") ||
                    normalized.startsWith("http://127.0.0.1") ||
                    normalized.startsWith("http://localhost")

            if (!isAllowedLocal) {
                Log.w(TAG, "Non-local HTTP rejected in debug. Using fallback: $fallback")
                return fallback
            }
        }

        return normalized
    }
}
