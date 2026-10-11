package com.xploitmusic.app.core.network

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.UnknownServiceException
import javax.net.ssl.SSLHandshakeException

/**
 * Excepción centralizada para errores de política de red o conectividad.
 * Sustituye excepciones opaticas de OkHttp con mensajes accionables en español.
 */
class CleartextNetworkException(message: String, cause: Throwable? = null) : IOException(message, cause)

class NetworkErrorInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val host = chain.request().url.host
        val port = chain.request().url.port
        try {
            return chain.proceed(chain.request())
        } catch (e: Exception) {
            when (e) {
                is UnknownServiceException -> {
                    if (e.message?.contains("CLEARTEXT", ignoreCase = true) == true) {
                        throw CleartextNetworkException(
                            "[XploitNetwork] Trfico cleartext (HTTP) bloqueado por la política de red del dispositivo. " +
                            "En desarrollo, activa USE_LOCALHOST_BFF=true en local.properties. " +
                            "En producción, el endpoint debe usar HTTPS. Host: $host",
                            e
                        )
                    }
                    throw e
                }
                is ConnectException -> {
                    val isLocal = host == "10.0.2.2" || host == "localhost" || host == "127.0.0.1"
                    if (isLocal) {
                        throw CleartextNetworkException(
                            "[XploitNetwork] Conexión rechazada al BFF local ($host:$port). " +
                            "Asegúrate de que el servidor esté corriendo y que USE_LOCALHOST_BFF=true esté activo, " +
                            "o cambia a USE_LOCALHOST_BFF=false para usar el endpoint de producción.",
                            e
                        )
                    }
                    throw IOException(
                        "[XploitNetwork] No se pudo conectar con $host. " +
                        "Verifica tu conexión a internet e intenta de nuevo.",
                        e
                    )
                }
                is UnknownHostException -> {
                    // DNS fail: el host no existe o no hay internet
                    throw IOException(
                        "[XploitNetwork] No se pudo resolver el servidor '$host'. " +
                        "Verifica tu conexión a internet o que el dominio sea correcto.",
                        e
                    )
                }
                is SocketTimeoutException -> {
                    throw IOException(
                        "[XploitNetwork] Tiempo de espera agotado al conectar con '$host'. " +
                        "El servidor puede estar sobrecargado. Intenta de nuevo.",
                        e
                    )
                }
                is SSLHandshakeException -> {
                    throw IOException(
                        "[XploitNetwork] Error de seguridad SSL con '$host'. " +
                        "El certificado del servidor pode ser inválido o la conexión interceptada.",
                        e
                    )
                }
                else -> throw e
            }
        }
    }
}
