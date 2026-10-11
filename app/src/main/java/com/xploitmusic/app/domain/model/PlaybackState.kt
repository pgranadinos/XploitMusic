package com.xploitmusic.app.domain.model

sealed class PlaybackState {
    object Idle : PlaybackState()
    data class Buffering(val currentSong: Song) : PlaybackState()
    data class Playing(val currentSong: Song, val progressMs: Long) : PlaybackState()
    data class Paused(val currentSong: Song, val progressMs: Long) : PlaybackState()
    data class Ended(val lastSong: Song) : PlaybackState()
    data class Error(val message: String) : PlaybackState()
}
