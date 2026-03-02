package com.auramusic.app.data.dto

data class LrcLibResponse(
    val id: Int,
    val trackName: String,
    val artistName: String,
    val albumName: String?,
    val duration: Double,
    val instrumental: Boolean,
    val plainLyrics: String?,
    val syncedLyrics: String?
)
