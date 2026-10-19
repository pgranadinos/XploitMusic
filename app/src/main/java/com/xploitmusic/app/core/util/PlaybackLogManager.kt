package com.xploitmusic.app.core.util

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
        private const val MAX_LOG_FILE_SIZE_BYTES = 2 * 1024 * 1024 // 2 MB
    }

    private val logFile: File
        get() {
            val dir = File(context.filesDir, LOG_DIR)
            if (!dir.exists()) dir.mkdirs()
            return File(dir, LOG_FILE_NAME)
        }

    @Synchronized
    fun log(category: String, level: String, message: String) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        val logLine = "[$timestamp] [$level] [$category] $message"

        // Also output to Logcat for real-time debugging
        when (level.uppercase()) {
            "ERROR" -> Log.e("$TAG-$category", message)
            "WARN" -> Log.w("$TAG-$category", message)
            else -> Log.d("$TAG-$category", message)
        }

        try {
            val file = logFile
            if (file.exists() && file.length() > MAX_LOG_FILE_SIZE_BYTES) {
                // Rotate / truncate if too large
                file.writeText("")
            }
            FileWriter(file, true).use { fw ->
                PrintWriter(fw).use { pw ->
                    pw.println(logLine)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write playback audit log", e)
        }
    }

    fun readLogs(): String {
        return try {
            val file = logFile
            if (file.exists()) {
                file.readText()
            } else {
                "No playback audit logs found."
            }
        } catch (e: Exception) {
            "Error reading playback logs: ${e.message}"
        }
    }

    fun clearLogs() {
        try {
            val file = logFile
            if (file.exists()) {
                file.writeText("")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear playback audit logs", e)
        }
    }
}
