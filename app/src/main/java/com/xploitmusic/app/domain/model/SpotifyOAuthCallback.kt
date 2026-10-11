package com.xploitmusic.app.domain.model

data class SpotifyOAuthCallback(
    val code: String? = null,
    val state: String? = null,
    val error: String? = null
)
