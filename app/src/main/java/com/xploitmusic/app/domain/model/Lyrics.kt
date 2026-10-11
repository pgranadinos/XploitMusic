package com.xploitmusic.app.domain.model

data class Lyrics(
    val id: Int,
    val trackName: String,
    val artistName: String,
    val albumName: String?,
    val duration: Double,
    val instrumental: Boolean,
    val plainLyrics: String?,
    val syncedLyrics: String? // LRC format string
)

data class LyricLine(
    val startTimeMs: Long,
    val text: String
)
