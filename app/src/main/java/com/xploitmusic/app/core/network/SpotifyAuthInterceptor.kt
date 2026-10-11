package com.xploitmusic.app.core.network

import com.xploitmusic.app.data.local.dao.SpotifyTokenDao
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/**
 * Interceptor para inyectar el token OAuth de Spotify en las peticiones a la Web API.
 */
class SpotifyAuthInterceptor @Inject constructor(
    private val tokenDao: SpotifyTokenDao
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        
        // Solo inyectar en llamadas a la API de Spotify (no en el Auth/Token endpoint)
        if (request.url.host != "api.spotify.com") {
            return chain.proceed(request)
        }

        // Obtener token sincrónicamente (estamos en un background thread del OkHttp Dispatcher)
        val token = runBlocking { tokenDao.getToken()?.accessToken }

        val authenticatedRequest = if (token != null) {
            request.newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else {
            request
        }

        return chain.proceed(authenticatedRequest)
    }
}
