package com.xploitmusic.app.core.util

import com.xploitmusic.app.domain.model.LyricLine
import java.util.regex.Pattern

object LrcParser {
    private val PATTERN = Pattern.compile("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?\\](.*)")

    fun parse(lrcContent: String?): List<LyricLine> {
        if (lrcContent.isNullOrBlank()) return emptyList()

        val lines = mutableListOf<LyricLine>()
        lrcContent.lines().forEach { line ->
            val matcher = PATTERN.matcher(line)
            if (matcher.find()) {
                val min = matcher.group(1)?.toLongOrNull() ?: 0
                val sec = matcher.group(2)?.toLongOrNull() ?: 0
                val fraction = matcher.group(3).orEmpty()
                val ms = when (fraction.length) {
                    1 -> fraction.toLongOrNull()?.times(100)
                    2 -> fraction.toLongOrNull()?.times(10)
                    3 -> fraction.toLongOrNull()
                    else -> 0
                } ?: 0
                val text = matcher.group(4)?.trim() ?: ""

                val totalMs = (min * 60 * 1000) + (sec * 1000) + ms
                lines.add(LyricLine(totalMs, text))
            }
        }
        return lines.sortedBy { it.startTimeMs }
    }

    fun parse(
        syncedLyrics: String?,
        plainLyrics: String?,
        durationSeconds: Double
    ): List<LyricLine> {
        val synced = parse(syncedLyrics)
        if (synced.isNotEmpty()) return synced

        val plainLines = plainLyrics
            ?.lines()
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            .orEmpty()
        if (plainLines.isEmpty()) return emptyList()

        val durationMs = (durationSeconds.coerceAtLeast(0.0) * 1000).toLong()
        val stepMs = if (durationMs > 0) durationMs / plainLines.size else 1L
        return plainLines.mapIndexed { index, text ->
            LyricLine(startTimeMs = index * stepMs, text = text)
        }
    }
}
