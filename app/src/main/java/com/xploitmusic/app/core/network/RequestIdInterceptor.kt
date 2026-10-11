package com.xploitmusic.app.core.network

import okhttp3.Interceptor
import okhttp3.Response
import java.util.UUID

/**
 * Interceptor de red (SRE) que adjunta un request-id único
 * a las llamadas, permitiendo correlacionar métricas entre el cliente
 * móvil y el backend (BFF / Piped).
 */
class RequestIdInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val requestId = UUID.randomUUID().toString()

        val newRequest = originalRequest.newBuilder()
            .header("X-Request-Id", requestId)
            // Agregamos User-Agent para mayor observabilidad SRE y Analytics en capa API
            .header("User-Agent", "XploitMusic-Android/Alpha (SREEnabled)")
            .build()

        return chain.proceed(newRequest)
    }
}
