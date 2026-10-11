package com.xploitmusic.app.core.network

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

/**
 * OkHttp Interceptor que proporciona fallback automático entre múltiples
 * instancias públicas de Piped. Si el mirror primario falla o devuelve un
 * error de servidor (5xx), el interceptor reintenta automáticamente con el
 * siguiente mirror de la lista sin que el usuario note nada.
 *
 * Esto hace que la reproducción funcione aunque algunos mirrors de Piped
 * estén caídos o con limitaciones de tasa.
 */
class PipedMirrorInterceptor : Interceptor {

    companion object {
        private const val TAG = "PipedMirrorInterceptor"

        // Lista de mirrors públicos de Piped — ordenados de más confiable a menos.
        // Verificados el 2026-03-30: los primeros responden HTTP 200 consistentemente.
        val PIPED_MIRRORS = listOf(
            "api.piped.private.coffee",          // ✅ Verificado — responde 200
            "pipedapi.in.projectsegfau.lt",      // ✅ Verificado — responde 200
            "pipedapi.kavin.rocks",               // ⚠️ A veces 521 (Cloudflare caído)
            "piped.smnz.de",
            "piped.adminforge.de"
        )

        // El host "primario" configurado en NetworkModule
        val PRIMARY_HOST = PIPED_MIRRORS[0]
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // Solo actuar en requests hacia los mirrors de Piped
        if (!isPipedRequest(originalRequest)) {
            return chain.proceed(originalRequest)
        }

        var lastResponse: Response? = null
        var lastException: Exception? = null

        for ((index, mirror) in PIPED_MIRRORS.withIndex()) {
            val mirrorRequest = originalRequest.newBuilder()
                .url(
                    originalRequest.url.newBuilder()
                        .host(mirror)
                        .build()
                )
                .build()

            try {
                val response = chain.proceed(mirrorRequest)

                // Si la respuesta fue exitosa, devolverla directamente
                if (response.isSuccessful) {
                    if (index > 0) {
                        Log.d(TAG, "✅ Piped fallback exitoso: $mirror (intento ${index + 1})")
                    }
                    return response
                }

                // Códigos que indican fallo del mirror (rate limit, bloqueo, error servidor)
                // → probar el siguiente mirror
                val shouldFallback = response.code in 400..403   // 400,401,402,403: acceso bloqueado
                        || response.code == 429                   // 429: rate limited
                        || response.code in 500..599             // 5xx: error de servidor
                if (shouldFallback) {
                    Log.w(TAG, "⚠️ Mirror $mirror devolvió HTTP ${response.code}, probando siguiente...")
                    response.close()
                    lastResponse = response
                    continue
                }

                // Para otros errores (404, etc.), no tiene sentido cambiar de mirror
                Log.d(TAG, "Mirror $mirror — HTTP ${response.code}, no se hace fallback.")
                return response

            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Mirror $mirror falló con excepción: ${e.message}, probando siguiente...")
                lastException = e
            }
        }

        // Si todos los mirrors fallaron, lanzar la última excepción con mensaje enriquecido
        val videoId = originalRequest.url.pathSegments.lastOrNull()
        if (lastException != null) {
            throw IOException(
                "[PipedMirror] Todos los mirrors fallaron para ${videoId}. " +
                "Causa original: ${lastException!!.message}",
                lastException
            )
        }
        // último recurso: reenviar con el mirror primario (puede resultar en respuesta de error)
        return chain.proceed(originalRequest.newBuilder()
            .url(originalRequest.url.newBuilder().host(PRIMARY_HOST).build())
            .build()
        )
    }

    private fun isPipedRequest(request: Request): Boolean {
        return PIPED_MIRRORS.any { mirror -> request.url.host == mirror }
    }

    /**
     * Construye un mensaje de error descriptivo cuando todos los mirrors fallan,
     * para que el usuario (y logcat) vea qué pasó exactamente.
     */
    private fun allMirrorsFailedError(videoId: String?): IOException {
        val mirrorList = PIPED_MIRRORS.joinToString(", ")
        return IOException(
            "[PipedMirror] Todos los mirrors de Piped fallaron para ${videoId ?: "request"}. " +
            "Mirrors intentados: $mirrorList. " +
            "Verifica tu conexión a internet o intenta más tarde."
        )
    }
}
