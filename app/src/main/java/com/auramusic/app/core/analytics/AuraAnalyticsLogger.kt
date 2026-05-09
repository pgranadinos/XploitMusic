package com.auramusic.app.core.analytics

import android.util.Log
import com.google.gson.Gson
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuraAnalyticsLogger @Inject constructor() {

    private val gson = Gson()

    companion object {
        private const val TAG = "AuraAnalytics"
    }

    /**
     * Modelo base estructurado de eventos para agregaciones en SRE
     */
    data class AuraEvent(
        val eventName: String,
        val feature: String,
        val timestamp: Long = System.currentTimeMillis(),
        val properties: Map<String, Any>
    )

    private fun logEvent(name: String, feature: String, properties: Map<String, Any>) {
        if (!com.auramusic.app.BuildConfig.DEBUG) return // 🔒 Hardening: Silent Telemetry for Release Builds

        val event = AuraEvent(
            eventName = name,
            feature = feature,
            properties = properties
        )
        // Simulando el envío a Datadog/NewRelic mediante Stdout/Logcat JSON Lines
        val jsonPayload = gson.toJson(event)
        Log.i(TAG, jsonPayload)
    }

    // ─── PII Sanitization ───
    private fun sanitizeQuery(query: String): String {
        // Regla PII: Si parece un email, un ID de spotify o es muy local, generalizamos.
        // Simularemos una sanetización básica ocultando longitud vs literal para búsquedas largas (potencial copia y pega de texto)
        return if (query.length > 20) {
            "long_search_query_len_${query.length}"
        } else {
            query.lowercase()
        }
    }

    // ─── Métricas de Búsqueda ───

    fun logSearch(query: String, success: Boolean, durationMs: Long, resultCount: Int, isError: Boolean = false, errorMsg: String? = null) {
        val props = mutableMapOf<String, Any>(
            "query_sanitized" to sanitizeQuery(query),
            "success" to success,
            "duration_ms" to durationMs,
            "result_count" to resultCount
        )
        if (isError && errorMsg != null) {
            props["error_reason"] = errorMsg
        }
        val eventName = if (success) "search_success" else "search_failed"
        logEvent(eventName, "SEARCH", props)
    }

    // ─── Métricas de Reproducción ───

    fun logPlayAttempt(songId: String, componentName: String) {
        logEvent("play_attempt", "PLAYER", mapOf(
            "song_id" to songId,
            "source_component" to componentName
        ))
    }

    fun logPlaySuccess(songId: String, loadTimeMs: Long) {
        logEvent("play_started", "PLAYER", mapOf(
            "song_id" to songId,
            "load_time_ms" to loadTimeMs
        ))
    }

    fun logPlayError(songId: String, errorMessage: String, durationMs: Long) {
        logEvent("play_failed", "PLAYER", mapOf(
            "song_id" to songId,
            "error_reason" to errorMessage,
            "failed_after_ms" to durationMs
        ))
    }

    fun logSkipFailed(fromSongId: String?, errorReason: String) {
        logEvent("skip_failed", "PLAYER", mapOf(
            "from_song_id" to (fromSongId ?: "unknown"),
            "error_reason" to errorReason
        ))
    }

    fun logAutoplayStarted(fromSongId: String, newSongId: String) {
        logEvent("autoplay_started", "PLAYER", mapOf(
            "anchor_song_id" to fromSongId,
            "new_song_id" to newSongId
        ))
    }
}
