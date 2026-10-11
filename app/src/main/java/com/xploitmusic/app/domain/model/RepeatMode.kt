package com.xploitmusic.app.domain.model

/**
 * Typed repeat mode replacing raw Player.REPEAT_MODE_* ints.
 * Maps directly to ExoPlayer constants for easy conversion.
 */
enum class RepeatMode {
    OFF,    // Player.REPEAT_MODE_OFF  (0)
    ALL,    // Player.REPEAT_MODE_ALL  (2)
    ONE;    // Player.REPEAT_MODE_ONE  (1)

    /** Convert to ExoPlayer's Player.REPEAT_MODE_* int */
    fun toExoPlayerMode(): Int = when (this) {
        OFF -> 0  // Player.REPEAT_MODE_OFF
        ALL -> 2  // Player.REPEAT_MODE_ALL
        ONE -> 1  // Player.REPEAT_MODE_ONE
    }

    companion object {
        /** Convert from ExoPlayer's Player.REPEAT_MODE_* int */
        fun fromExoPlayerMode(mode: Int): RepeatMode = when (mode) {
            1 -> ONE
            2 -> ALL
            else -> OFF
        }
    }
}
