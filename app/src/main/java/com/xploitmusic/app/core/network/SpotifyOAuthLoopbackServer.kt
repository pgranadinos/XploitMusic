package com.xploitmusic.app.core.network

import android.net.Uri
import com.xploitmusic.app.domain.model.SpotifyOAuthCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpotifyOAuthLoopbackServer @Inject constructor() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val callbackChannel = Channel<SpotifyOAuthCallback>(Channel.BUFFERED)

    @Volatile
    private var activeServer: ServerSocket? = null

    val callbacks: Flow<SpotifyOAuthCallback> = callbackChannel.receiveAsFlow()

    suspend fun start(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            stop()
            val server = ServerSocket(0, 1, InetAddress.getByName(LOOPBACK_HOST)).apply {
                soTimeout = CALLBACK_TIMEOUT_MS
            }
            activeServer = server
            scope.launch { awaitCallback(server) }
            "http://$LOOPBACK_HOST:${server.localPort}$CALLBACK_PATH"
        }
    }

    fun stop() {
        runCatching { activeServer?.close() }
        activeServer = null
    }

    private fun awaitCallback(server: ServerSocket) {
        try {
            server.accept().use { socket ->
                val requestLine = socket.getInputStream().bufferedReader().readLine().orEmpty()
                val target = requestLine.split(' ').getOrNull(1).orEmpty()
                val uri = Uri.parse("http://$LOOPBACK_HOST$target")
                if (uri.path != CALLBACK_PATH) {
                    writeResponse(socket, 404, "Callback no reconocido")
                    callbackChannel.trySend(SpotifyOAuthCallback(error = "Callback no reconocido"))
                    return
                }

                val callback = SpotifyOAuthCallback(
                    code = uri.getQueryParameter("code"),
                    state = uri.getQueryParameter("state"),
                    error = uri.getQueryParameter("error")
                )
                writeResponse(socket, 200, "Autorización recibida")
                callbackChannel.trySend(callback)
            }
        } catch (_: SocketTimeoutException) {
            callbackChannel.trySend(
                SpotifyOAuthCallback(error = "La autorización de Spotify expiró")
            )
        } catch (error: Exception) {
            if (!server.isClosed) {
                callbackChannel.trySend(
                    SpotifyOAuthCallback(error = error.message ?: "Error en el callback local")
                )
            }
        } finally {
            runCatching { server.close() }
            if (activeServer === server) activeServer = null
        }
    }

    private fun writeResponse(socket: java.net.Socket, status: Int, title: String) {
        val html = """
            <!doctype html>
            <html lang="es"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width">
            <title>XploitMusic</title></head>
            <body style="font-family:sans-serif;background:#101820;color:#fff;text-align:center;padding:48px 20px">
              <h1>$title</h1><p>Ya puedes volver a XploitMusic.</p>
              <a href="app://xploitmusic/spotify-import" style="display:inline-block;padding:14px 22px;background:#4682B4;color:#fff;text-decoration:none;border-radius:24px">Volver a XploitMusic</a>
            </body></html>
        """.trimIndent()
        val body = html.toByteArray(Charsets.UTF_8)
        val reason = if (status == 200) "OK" else "Not Found"
        socket.getOutputStream().bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write("HTTP/1.1 $status $reason\r\n")
            writer.write("Content-Type: text/html; charset=utf-8\r\n")
            writer.write("Content-Length: ${body.size}\r\n")
            writer.write("Connection: close\r\n\r\n")
            writer.write(html)
            writer.flush()
        }
    }

    private companion object {
        const val LOOPBACK_HOST = "127.0.0.1"
        const val CALLBACK_PATH = "/spotify/callback"
        const val CALLBACK_TIMEOUT_MS = 5 * 60 * 1000
    }
}
