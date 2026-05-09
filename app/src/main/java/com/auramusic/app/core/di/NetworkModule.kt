package com.auramusic.app.core.di

import android.util.Log
import com.auramusic.app.BuildConfig
import com.auramusic.app.core.network.NetworkErrorInterceptor
import com.auramusic.app.core.network.PipedMirrorInterceptor
import com.auramusic.app.core.network.RequestIdInterceptor
import com.auramusic.app.data.remote.AuraBffApi
import com.auramusic.app.data.remote.InvidiousApi
import com.auramusic.app.data.remote.PipedApi
import com.auramusic.app.data.remote.LrcLibApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.URI
import java.net.URISyntaxException
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .addInterceptor(RequestIdInterceptor())
            .addInterceptor(NetworkErrorInterceptor())
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)

        // FIX: logging con BODY solo en DEBUG — evita leak de tokens/headers en release
        if (BuildConfig.DEBUG) {
            val loggingInterceptor = HttpLoggingInterceptor { message ->
                Log.d("AuraDebug-HTTP", message)
            }.apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
            builder.addInterceptor(loggingInterceptor)
        }

        return builder.build()
    }

    @Provides
    @Singleton
    fun provideAuraBffApi(okHttpClient: OkHttpClient): AuraBffApi {
        val safeBaseUrl = getSafeBffBaseUrl(BuildConfig.BFF_BASE_URL)

        return Retrofit.Builder()
            .baseUrl(safeBaseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AuraBffApi::class.java)
    }

    /**
     * Validación de arranque del endpoint BFF sin crashear la aplicación.
     * Si la configuración es insegura (ej. cleartext en release) o malformada,
     * aplica un fallback seguro (host Piped primario) para evitar un crash fatal 
     * en tiempo de ejecución de Hilt y permite al ProviderGateway de la app usar 
     * su propio mecanismo de fallback.
     */
    private fun getSafeBffBaseUrl(configuredUrl: String): String {
        var isSafe = true
        var safeUrl = configuredUrl

        if (configuredUrl.isBlank() || configuredUrl == "https://api.example.com/api/v1/") {
            Log.e("AuraNetwork", "BFF_BASE_URL no está configurada o está vacía.")
            isSafe = false
        } else if (!configuredUrl.endsWith("/")) {
            Log.e("AuraNetwork", "BFF_BASE_URL debe terminar con '/'")
            isSafe = false
        } else if (!configuredUrl.startsWith("http://") && !configuredUrl.startsWith("https://")) {
            Log.e("AuraNetwork", "BFF_BASE_URL debe comenzar con 'https://' o 'http://'")
            isSafe = false
        } else if (configuredUrl.startsWith("http://") && !BuildConfig.ALLOW_LOCALHOST) {
            Log.e("AuraNetwork", "Cleartext HTTP bloqueado en release. Forzando fallback seguro.")
            isSafe = false
        } else {
            try {
                val uri = URI(configuredUrl)
                if (uri.host.isNullOrBlank()) {
                    Log.e("AuraNetwork", "BFF_BASE_URL no tiene host válido.")
                    isSafe = false
                }
            } catch (e: URISyntaxException) {
                Log.e("AuraNetwork", "BFF_BASE_URL no es una URL válida.", e)
                isSafe = false
            }
        }

        if (!isSafe) {
            safeUrl = "https://${PipedMirrorInterceptor.PRIMARY_HOST}/"
            Log.w("AuraNetwork", "Usando fallback base URL seguro y válido: $safeUrl")
        }

        return safeUrl
    }

    @Provides
    @Singleton
    fun provideLrcLibApi(okHttpClient: OkHttpClient): LrcLibApi {
        return Retrofit.Builder()
            .baseUrl("https://lrclib.net/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(LrcLibApi::class.java)
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

        // La base URL usa el mirror primario; el PipedMirrorInterceptor
        // cambia el host automáticamente si este falla.
        return Retrofit.Builder()
            .baseUrl("https://${PipedMirrorInterceptor.PRIMARY_HOST}/")
            .client(pipedClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PipedApi::class.java)
    }

    @Provides
    @Singleton
    fun provideInvidiousApi(okHttpClient: OkHttpClient): InvidiousApi {
        return Retrofit.Builder()
            .baseUrl("https://vid.priv.au/") // an invidious instance API
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(InvidiousApi::class.java)
    }

    @Provides
    @Singleton
    fun provideSpotifyAuthApi(okHttpClient: OkHttpClient): com.auramusic.app.data.remote.SpotifyAuthApi {
        return Retrofit.Builder()
            .baseUrl("https://accounts.spotify.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(com.auramusic.app.data.remote.SpotifyAuthApi::class.java)
    }

    @Provides
    @Singleton
    fun provideSpotifyApi(
        okHttpClient: OkHttpClient,
        spotifyAuthInterceptor: com.auramusic.app.core.network.SpotifyAuthInterceptor
    ): com.auramusic.app.data.remote.SpotifyApi {
        val spotifyClient = okHttpClient.newBuilder()
            .addInterceptor(spotifyAuthInterceptor)
            .build()

        return Retrofit.Builder()
            .baseUrl("https://api.spotify.com/v1/")
            .client(spotifyClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(com.auramusic.app.data.remote.SpotifyApi::class.java)
    }
}
