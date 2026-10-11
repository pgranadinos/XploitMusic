package com.xploitmusic.app.data.repository

import com.xploitmusic.app.data.remote.SpotifyTrackDto
import com.xploitmusic.app.domain.model.Song
import kotlin.math.abs
import kotlin.math.min

/**
 * Motor de matching Spotify → YouTube/Piped.
 *
 * Dada una [SpotifyTrackDto] y una lista de candidatos de YouTube (obtenidos
 * via búsqueda en Piped), asigna un score 0..1 a cada candidato y devuelve
 * el mejor resultado con su nivel de confianza.
 *
 * ### Algoritmo (suma ponderada)
 * | Componente       | Peso | Método                                     |
 * |------------------|------|--------------------------------------------|
 * | Similitud título | 40%  | Levenshtein normalizado sobre texto limpio |
 * | Similitud artista| 35%  | Ratio de tokens coincidentes               |
 * | Duración         | 25%  | Lineal, penaliza >15s de diferencia        |
 *
 * ### Umbrales de confianza
 * - ≥ 0.85 → EXACT (aceptación automática)
 * - 0.65..0.84 → HIGH_CONFIDENCE
 * - 0.40..0.64 → LOW_CONFIDENCE (usuario puede confirmar)
 * - < 0.40 → NO_MATCH
 */
class SpotifyMatchingEngine {

    // ─── Tipos públicos ───────────────────────────────────────────────────────

    enum class MatchType { EXACT, HIGH_CONFIDENCE, LOW_CONFIDENCE, NO_MATCH }

    data class MatchResult(
        val spotifyTrack: SpotifyTrackDto,
        val candidate: Song?,          // null si no hubo candidatos
        val score: Float,              // 0.0 .. 1.0
        val matchType: MatchType
    )

    // ─── API pública ──────────────────────────────────────────────────────────

    /**
     * Busca el mejor candidato entre [candidates] para el [spotifyTrack] dado.
     * @return [MatchResult] — siempre devuelve algo (puede ser NO_MATCH con candidate=null)
     */
    fun findBestMatch(
        spotifyTrack: SpotifyTrackDto,
        candidates: List<Song>
    ): MatchResult {
        if (candidates.isEmpty()) {
            return MatchResult(spotifyTrack, null, 0f, MatchType.NO_MATCH)
        }

        val spotifyTitle   = normalize(spotifyTrack.name)
        val spotifyArtist  = spotifyTrack.artists.joinToString(" ") { normalize(it.name) }
        val spotifyDurSec  = spotifyTrack.durationMs / 1000f

        var bestScore = -1f
        var bestCandidate: Song? = null

        for (candidate in candidates) {
            val ytTitle  = normalize(candidate.title)
            val ytArtist = normalize(candidate.artist)
            val ytDurSec = candidate.durationSeconds.toFloat()

            val titleScore    = levenshteinSimilarity(spotifyTitle, ytTitle)
            val artistScore   = tokenOverlapScore(spotifyArtist, ytArtist)
            val durationScore = durationScore(spotifyDurSec, ytDurSec)

            val score = (titleScore * 0.40f) + (artistScore * 0.35f) + (durationScore * 0.25f)

            if (score > bestScore) {
                bestScore = score
                bestCandidate = candidate
            }
        }

        val matchType = when {
            bestScore >= 0.85f -> MatchType.EXACT
            bestScore >= 0.65f -> MatchType.HIGH_CONFIDENCE
            bestScore >= 0.40f -> MatchType.LOW_CONFIDENCE
            else               -> MatchType.NO_MATCH
        }

        return MatchResult(spotifyTrack, bestCandidate, bestScore, matchType)
    }

    // ─── Normalización ────────────────────────────────────────────────────────

    /**
     * Limpia el texto antes de comparar:
     * - Minúsculas
     * - Elimina paréntesis con contenido típico: (Official Video), (Lyrics), (feat. X), etc.
     * - Elimina corchetes: [Official], [HQ], etc.
     * - Colapsa espacios múltiples
     */
    fun normalize(text: String): String = text
        .lowercase()
        .replace(Regex("""\((?:official\s*(?:video|audio|music\s*video|lyric(?:s)?|hd|hq)?|lyrics?|feat\.?.*?|ft\.?.*?|en\s*vivo|live|acoustic|remix|remaster(?:ed)?|traduc(?:ción|tion)?)\)"""), "")
        .replace(Regex("""\[(?:official\s*(?:video|audio|hd|hq)?|lyrics?|live|remix|remaster(?:ed)?)\]"""), "")
        .replace(Regex("""\b(?:feat|ft)\.?\s+.*$"""), "")
        .replace(Regex("""\s+(?:official\s*(?:music\s*)?(?:video|audio)|lyrics?(?:\s+video)?)\s*$"""), "")
        .replace(Regex("""[^\w\s]"""), " ")       // quitar puntuación
        .replace(Regex("""\s+"""), " ")
        .trim()

    // ─── Similitud de texto ───────────────────────────────────────────────────

    /**
     * Levenshtein normalizado: 1 - (editDistance / max(len1, len2)).
     * Si ambos strings son vacíos → 1.0 (perfectamente iguales).
     */
    fun levenshteinSimilarity(a: String, b: String): Float {
        if (a == b) return 1.0f
        if (a.isEmpty() || b.isEmpty()) return 0.0f

        val distance = levenshteinDistance(a, b)
        return 1f - (distance.toFloat() / maxOf(a.length, b.length))
    }

    private fun levenshteinDistance(a: String, b: String): Int {
        val m = a.length
        val n = b.length
        val dp = Array(m + 1) { IntArray(n + 1) }
        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j
        for (i in 1..m) {
            for (j in 1..n) {
                dp[i][j] = if (a[i - 1] == b[j - 1]) {
                    dp[i - 1][j - 1]
                } else {
                    1 + minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])
                }
            }
        }
        return dp[m][n]
    }

    /**
     * Coeficiente Dice sobre tokens. Penaliza tanto artistas faltantes como
     * nombres extraños añadidos por un canal no oficial.
     */
    fun tokenOverlapScore(a: String, b: String): Float {
        val tokensA = a.split(" ").filter { it.length > 1 }.toSet()
        val tokensB = b.split(" ").filter { it.length > 1 }.toSet()
        if (tokensA.isEmpty() || tokensB.isEmpty()) return 0.5f
        val intersection = tokensA.intersect(tokensB).size
        return (2f * intersection) / (tokensA.size + tokensB.size)
    }

    /**
     * Score de duración: lineal, tolerando hasta 15s de diferencia perfecta,
     * y cayendo a 0 en 30s de diferencia.
     */
    fun durationScore(spotifySec: Float, youtubeSec: Float): Float {
        val diff = abs(spotifySec - youtubeSec)
        if (diff <= 15f) return 1f
        return (1f - min(diff - 15f, 15f) / 15f).coerceIn(0f, 1f)
    }
}
