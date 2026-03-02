package com.auramusic.app.domain.model

data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String,
    val durationSeconds: Long,
    val streamUrl: String? = null,
    val genre: String? = null
)
