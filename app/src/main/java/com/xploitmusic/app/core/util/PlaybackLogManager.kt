package com.xploitmusic.app.core.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackLogManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "XploitPlaybackAudit"
        private const val LOG_DIR = "logs"
        private const val LOG_FILE_NAME = "playback_audit.log"
        private const val ROTATED_LOG_FILE_NAME = "playback_audit.1.log"
        private const val MAX_LOG_FILE_SIZE_BYTES = 2 * 1024 * 1024L // 2 MB

        // Regex para capturar cualquier URL HTTP/HTTPS y extraer solo su dominio
        private val URL_REGEX = Regex("https?://([^/\\s?#]+)\\S*", RegexOption.IGNORE_CASE)

        /**
         * Sanitiza el mensaje reduciendo cualquier URL completa a solo su nombre de dominio.
         * Elimina tokens, query params y paths por privacidad.
         */
        internal fun sanitizeMessage(msg: String): String {
            return msg.replace(URL_REGEX) { match ->
                val fullUrl = match.value
                val domain = try {
                    URI(fullUrl).host ?: match.groupValues.getOrNull(1) ?: "url_desconocida"
                } catch (_: Exception) {
                    match.groupValues.getOrNull(1) ?: "url_desconocida"
                }
                domain
            }
        }
    }

    // Cola asíncrona de un solo hilo para garantizar que log() nunca bloquee el hilo llamante
    private val singleThreadExecutor = Executors.newSingleThreadExecutor()
    private val singleThreadDispatcher = singleThreadExecutor.asCoroutineDispatcher()
    private val writeScope = CoroutineScope(SupervisorJob() + singleThreadDispatcher)

    private val logDir: File
        get() {
            val dir = File(context.filesDir, LOG_DIR)
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    private val logFile: File
        get() = File(logDir, LOG_FILE_NAME)

    private val rotatedLogFile: File
        get() = File(logDir, ROTATED_LOG_FILE_NAME)

    fun logSessionHeader(
        versionName: String,
        versionCode: Int,
        gitHash: String,
        model: String,
        androidRelease: String,
        sdkInt: Int
    ) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        val header = buildString {
            appendLine("==================== NUEVA SESIÓN ====================")
            appendLine("Versión: v$versionName ($versionCode) | Git: $gitHash")
            appendLine("Dispositivo: $model | Android: $androidRelease (API $sdkInt)")
            appendLine("Fecha de inicio: $timestamp")
            appendLine("======================================================")
        }
        writeScope.launch {
            writeLineToFile(header, skipTimestamp = true)
        }
    }

    fun log(category: String, level: String, message: String) {
        // (a) Sanitiza el mensaje UNA sola vez y usa ese mismo texto para Logcat y para el archivo
        val sanitized = sanitizeMessage(message)

        // Logcat inmediato
        when (level.uppercase(Locale.US)) {
            "ERROR" -> Log.e("$TAG-$category", sanitized)
            "WARN" -> Log.w("$TAG-$category", sanitized)
            else -> Log.d("$TAG-$category", sanitized)
        }

        // (b) Escritura asíncrona no bloqueante
        writeScope.launch {
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
            val line = "[$timestamp] [$level] [$category] $sanitized"
            writeLineToFile(line, skipTimestamp = true)
        }
    }

    fun markManualProblem() {
        val timeStr = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        log("USER_MARK", "WARN", "=== MARCA MANUAL === [$timeStr]")
    }

    private fun writeLineToFile(line: String, skipTimestamp: Boolean = false) {
        try {
            val file = logFile
            // (d) Rotación de archivos al superar 2 MB
            if (file.exists() && file.length() >= MAX_LOG_FILE_SIZE_BYTES) {
                val rotated = rotatedLogFile
                if (rotated.exists()) rotated.delete()
                file.renameTo(rotated)
            }

            FileWriter(logFile, true).use { fw ->
                PrintWriter(fw).use { pw ->
                    pw.println(line)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write audit log line", e)
        }
    }

    suspend fun readLastLines(lineCount: Int = 200): String {
        return withContext(singleThreadDispatcher) {
            val file = logFile
            if (!file.exists()) {
                "No hay registros de auditoría aún."
            } else {
                try {
                    val lines = file.readLines()
                    if (lines.isEmpty()) {
                        "Archivo de auditoría vacío."
                    } else {
                        val tail = if (lines.size > lineCount) lines.takeLast(lineCount) else lines
                        tail.joinToString("\n")
                    }
                } catch (e: Exception) {
                    "Error al leer la bitácora: ${e.message}"
                }
            }
        }
    }

    suspend fun clearLogs() {
        withContext(singleThreadDispatcher) {
            try {
                if (logFile.exists()) {
                    logFile.delete()
                }
                if (rotatedLogFile.exists()) {
                    rotatedLogFile.delete()
                }
                Unit
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing logs", e)
            }
        }
    }

    fun startNetworkMonitoring() {
        try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager ?: return
            val request = android.net.NetworkRequest.Builder().build()
            connectivityManager.registerNetworkCallback(request, object : android.net.ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: android.net.Network) {
                    val caps = connectivityManager.getNetworkCapabilities(network)
                    val typeStr = when {
                        caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true -> "Wi-Fi"
                        caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Datos móviles"
                        caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet"
                        else -> "Red conectada"
                    }
                    log("NETWORK", "INFO", "Estado de red cambiado: $typeStr")
                }

                override fun onLost(network: android.net.Network) {
                    log("NETWORK", "WARN", "Estado de red cambiado: Sin red (Desconectado)")
                }
            })
        } catch (e: Exception) {
            Log.w(TAG, "Network monitoring registration failed: ${e.message}")
        }
    }

    fun getShareIntent(): Intent? {
        val file = logFile
        if (!file.exists() || file.length() == 0L) return null

        val authority = "${context.packageName}.fileprovider"
        val contentUri: Uri = FileProvider.getUriForFile(context, authority, file)

        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Bitácora de Diagnóstico XploitMusic")
            putExtra(Intent.EXTRA_STREAM, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
