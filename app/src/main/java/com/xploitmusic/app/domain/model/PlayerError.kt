package com.xploitmusic.app.domain.model

/**
 * Encapsula la información técnica de un error emitido por ExoPlayer.
 */
data class PlayerError(
    val code: Int,
    val codeName: String,
    val message: String
)
