package com.auramusic.app.core.util

import com.auramusic.app.domain.model.LyricLine
import java.util.regex.Pattern

object LrcParser {
    private val PATTERN = Pattern.compile("\\[(\\d{2}):(\\d{2})\\.(\\d{2})\\](.*)")

    fun parse(lrcContent: String?): List<LyricLine> {
        if (lrcContent.isNullOrBlank()) return emptyList()

        val lines = mutableListOf<LyricLine>()
        lrcContent.lines().forEach { line ->
            val matcher = PATTERN.matcher(line)
            if (matcher.find()) {
                val min = matcher.group(1)?.toLongOrNull() ?: 0
                val sec = matcher.group(2)?.toLongOrNull() ?: 0
                val ms = matcher.group(3)?.toLongOrNull()?.times(10) ?: 0 // LRC uses centiseconds typically
                val text = matcher.group(4)?.trim() ?: ""

                val totalMs = (min * 60 * 1000) + (sec * 1000) + ms
                lines.add(LyricLine(totalMs, text))
            }
        }
        return lines.sortedBy { it.startTimeMs }
    }
}
