package com.auramusic.app.core.util

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Global uncaught exception handler for XploitMusic.
 *
 * Logs the full stack trace and saves the crash to a file in the app's cache directory
 * for potential later reporting. After logging, delegates to the system's default handler
 * so the OS can display the standard crash dialog.
 */
class CrashHandler private constructor(
    private val context: Context,
    private val defaultHandler: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {

    companion object {
        private const val TAG = "XploitCrash"
        private const val CRASH_DIR = "crash_logs"
        private const val MAX_CRASH_FILES = 10

        fun install(context: Context) {
            val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler(
                CrashHandler(context.applicationContext, defaultHandler)
            )
            Log.d(TAG, "CrashHandler installed")
        }
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            Log.e(TAG, "═══════════════ UNCAUGHT EXCEPTION ═══════════════")
            Log.e(TAG, "Thread: ${thread.name}")
            Log.e(TAG, "Exception: ${throwable.message}", throwable)
            Log.e(TAG, "═══════════════════════════════════════════════════")

            saveCrashToFile(thread, throwable)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save crash log", e)
        } finally {
            // Delegate to system default handler (shows crash dialog)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun saveCrashToFile(thread: Thread, throwable: Throwable) {
        val crashDir = File(context.cacheDir, CRASH_DIR)
        if (!crashDir.exists()) crashDir.mkdirs()

        // Clean old crash files (keep only MAX_CRASH_FILES)
        val files = crashDir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
        if (files.size >= MAX_CRASH_FILES) {
            files.drop(MAX_CRASH_FILES - 1).forEach { it.delete() }
        }

        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        val crashFile = File(crashDir, "crash_$timestamp.txt")

        PrintWriter(FileWriter(crashFile)).use { writer ->
            writer.println("XploitMusic Crash Report")
            writer.println("Time: $timestamp")
            writer.println("Thread: ${thread.name}")
            writer.println("─".repeat(50))
            throwable.printStackTrace(writer)
        }

        Log.d(TAG, "Crash log saved to: ${crashFile.absolutePath}")
    }
}
