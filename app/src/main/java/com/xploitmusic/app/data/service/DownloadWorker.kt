package com.xploitmusic.app.data.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.xploitmusic.app.core.util.Resource
import com.xploitmusic.app.data.local.dao.DownloadDao
import com.xploitmusic.app.data.repository.OfflineManager
import com.xploitmusic.app.domain.repository.StreamRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * WorkManager worker that downloads a single track's audio file as a Foreground Service.
 * Displays real-time progress, supports cancellation, and notifies on completion or failure.
 */
@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val downloadDao: DownloadDao,
    private val offlineManager: OfflineManager,
    private val streamRepository: StreamRepository,
    private val okHttpClient: OkHttpClient
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "XploitDebug"
        const val KEY_SONG_ID = "songId"
        const val KEY_QUALITY = "quality"
        const val KEY_PROGRESS = "progress"
        const val KEY_ERROR = "error"

        private const val CHANNEL_ID = "downloads_channel"
        private const val CHANNEL_NAME = "Descargas"

        internal fun extensionForAudio(contentType: String?, url: String): String {
            val mimeType = contentType
                ?.substringBefore(';')
                ?.trim()
                ?.lowercase()
            return when (mimeType) {
                "audio/mp4", "audio/x-m4a", "video/mp4" -> "m4a"
                "audio/webm", "video/webm" -> "webm"
                "audio/mpeg", "audio/mp3" -> "mp3"
                "audio/ogg", "application/ogg" -> "ogg"
                "audio/aac" -> "aac"
                else -> url.substringBefore('?')
                    .substringAfterLast('.', "")
                    .lowercase()
                    .takeIf { it in setOf("m4a", "webm", "mp3", "ogg", "aac") }
                    ?: "m4a"
            }
        }
    }

    private data class DownloadResult(
        val checksum: String,
        val extension: String,
        val contentLength: Long
    )

    override suspend fun doWork(): Result {
        val songId = inputData.getString(KEY_SONG_ID) ?: return Result.failure()
        val track = downloadDao.getBySongId(songId) ?: return Result.failure()

        // Establish foreground notification for WorkManager (targetSdk 34 compliant)
        try {
            setForeground(createForegroundInfo(track.title, 0))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "DownloadWorker: Could not set foreground service: ${e.message}")
        }

        // Pre-eviction: make room before starting a new download
        try {
            offlineManager.evictIfNeeded()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "DownloadWorker: Pre-eviction failed (non-fatal): ${e.message}")
        }

        // 1. Resolve the URL through the same provider/fallback chain as playback.
        val streamResult = try {
            streamRepository.getStreamInfo(songId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "DownloadWorker: Failed to resolve stream for $songId: ${e.message}")
            downloadDao.updateProgress(songId, "FAILED", track.progress)
            if (runAttemptCount < 3) {
                return Result.retry()
            } else {
                showFailureNotification(track.title, "No se pudo resolver la URL de descarga")
                return Result.failure(workDataOf(KEY_ERROR to "No se pudo resolver la URL de descarga"))
            }
        }

        val targetUrl = (streamResult as? Resource.Success)?.data
        if (targetUrl.isNullOrBlank()) {
            val reason = (streamResult as? Resource.Error)?.message ?: "Stream no disponible"
            Log.e(TAG, "DownloadWorker: No audio stream found for $songId")
            downloadDao.updateProgress(songId, "FAILED", 0)
            if (runAttemptCount < 3) {
                return Result.retry()
            } else {
                showFailureNotification(track.title, reason)
                return Result.failure(workDataOf(KEY_ERROR to reason))
            }
        }

        // 2. Download to a temporary file; the final extension comes from Content-Type.
        downloadDao.updateProgress(songId, "DOWNLOADING", 0)
        val downloadDir = File(applicationContext.filesDir, "downloads")
        downloadDir.mkdirs()
        val partialFile = File(downloadDir, "$songId.part")
        var completedFile: File? = null

        return try {
            val downloaded = downloadFile(targetUrl, partialFile, songId, track.title)

            // 3. Integrity check — file must be non-empty and match Content-Length when present.
            if (partialFile.length() == 0L ||
                (downloaded.contentLength > 0L && partialFile.length() != downloaded.contentLength)
            ) {
                partialFile.delete()
                Log.e(TAG, "DownloadWorker: Empty file after download for $songId")
                downloadDao.insertRaw(track.copy(
                    status = "FAILED", errorMessage = "Descarga incompleta", retryCount = runAttemptCount
                ))
                showFailureNotification(track.title, "Descarga incompleta")
                return Result.failure(workDataOf(KEY_ERROR to "Descarga incompleta"))
            }

            val file = File(downloadDir, "$songId.${downloaded.extension}")
            if (file.exists()) file.delete()
            if (!partialFile.renameTo(file)) {
                partialFile.copyTo(file, overwrite = true)
                partialFile.delete()
            }
            completedFile = file

            // 4. Store completed record with checksum
            downloadDao.insertRaw(track.copy(
                status = "COMPLETED",
                progress = 100,
                filePath = file.absolutePath,
                fileSizeBytes = file.length(),
                downloadedAt = System.currentTimeMillis(),
                lastAccessedAt = System.currentTimeMillis(),
                format = downloaded.extension.uppercase(),
                bitrate = 128,
                checksum = downloaded.checksum,
                retryCount = runAttemptCount
            ))
            Log.d(TAG, "DownloadWorker: ✅ '${track.title}' (${file.length() / 1024}KB) sha=${downloaded.checksum}")
            Result.success()

        } catch (e: CancellationException) {
            partialFile.delete()
            completedFile?.delete()
            Log.d(TAG, "DownloadWorker: Download cancelled for '${track.title}'")
            throw e
        } catch (e: IOException) {
            partialFile.delete()
            completedFile?.delete()
            val isNoSpace = e.message?.contains("ENOSPC") == true || e.message?.contains("No space") == true
            Log.e(TAG, "DownloadWorker: IOException for '${track.title}': ${e.message}")

            if (isNoSpace) {
                downloadDao.insertRaw(track.copy(
                    status = "FAILED",
                    errorMessage = "Almacenamiento insuficiente — libera espacio e intenta de nuevo",
                    retryCount = runAttemptCount
                ))
                showFailureNotification(track.title, "Almacenamiento insuficiente")
                Result.failure(workDataOf(KEY_ERROR to "Almacenamiento insuficiente"))
            } else if (runAttemptCount < 3) {
                downloadDao.updateProgress(songId, "QUEUED", 0)
                Result.retry()
            } else {
                downloadDao.insertRaw(track.copy(
                    status = "FAILED",
                    errorMessage = e.message,
                    retryCount = runAttemptCount
                ))
                showFailureNotification(track.title, "Error al descargar")
                Result.failure(workDataOf(KEY_ERROR to (e.message ?: "Error de descarga")))
            }
        } catch (e: Exception) {
            partialFile.delete()
            completedFile?.delete()
            Log.e(TAG, "DownloadWorker: Unexpected error for '${track.title}': ${e.message}", e)
            downloadDao.insertRaw(track.copy(
                status = "FAILED", errorMessage = e.message, retryCount = runAttemptCount
            ))
            showFailureNotification(track.title, "Error inesperado")
            Result.failure(workDataOf(KEY_ERROR to (e.message ?: "Error inesperado")))
        }
    }

    /** Downloads [url] to [file], tracks progress and updates foreground notification. */
    private suspend fun downloadFile(url: String, file: File, songId: String, songTitle: String): DownloadResult {
        if (!url.startsWith("https://")) {
            throw IOException(
                "[DownloadWorker] URL de stream insegura o inválida para $songId. " +
                "Se requiere HTTPS. URL recibida: ${url.take(80)}"
            )
        }
        val request = Request.Builder().url(url).build()
        val digest = MessageDigest.getInstance("SHA-256")

        return okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")

            val body = response.body ?: throw IOException("Empty response body")
            val total = body.contentLength()
            val extension = extensionForAudio(body.contentType()?.toString(), url)
            var downloaded = 0L
            var lastReportedPct = 0

            file.outputStream().use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        downloaded += read

                        val pct = if (total > 0) ((downloaded * 100) / total).toInt() else 0
                        if (pct >= lastReportedPct + 5) {
                            lastReportedPct = pct
                            setProgress(workDataOf(KEY_PROGRESS to pct))
                            downloadDao.updateProgress(songId, "DOWNLOADING", pct)
                            try {
                                setForeground(createForegroundInfo(songTitle, pct))
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {}
                        }
                    }
                }
            }

            DownloadResult(
                checksum = digest.digest().joinToString("") { "%02x".format(it) },
                extension = extension,
                contentLength = total
            )
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificaciones de progreso de descargas de música"
            }
            val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createForegroundInfo(songTitle: String, progress: Int): ForegroundInfo {
        createNotificationChannel()
        val cancelIntent = WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(songTitle)
            .setContentText(if (progress > 0) "Descargando: $progress%" else "Iniciando descarga…")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setProgress(100, progress, progress == 0)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancelar", cancelIntent)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                id.hashCode(),
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(id.hashCode(), notification)
        }
    }

    private fun showFailureNotification(songTitle: String, errorMessage: String) {
        createNotificationChannel()
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Error al descargar")
            .setContentText("$songTitle — $errorMessage")
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setAutoCancel(true)
            .build()

        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(id.hashCode(), notification)
    }
}
