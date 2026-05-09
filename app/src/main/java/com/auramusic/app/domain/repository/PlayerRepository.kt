package com.auramusic.app.domain.repository

import com.auramusic.app.domain.model.Song
import kotlinx.coroutines.flow.Flow

interface PlayerRepository {
    val isPlaying: Flow<Boolean>
    val currentPosition: Flow<Long>
    val duration: Flow<Long>
    val trackEnded: Flow<Unit>

    /** Emits the mediaId of the new track when ExoPlayer transitions between items */
    val mediaItemTransition: Flow<String?>

    suspend fun play()
    suspend fun pause()
    suspend fun seekTo(position: Long)
    suspend fun prepare(url: String, song: Song)
    suspend fun addNextMediaItem(url: String, song: Song)
    suspend fun clearQueue()
    suspend fun hasNextMediaItem(): Boolean
    suspend fun skipToNext()
    fun release()

    // Shuffle & Repeat controls
    fun setRepeatMode(repeatMode: Int)
    fun setShuffleModeEnabled(shuffleModeEnabled: Boolean)

    /** Emits user-friendly error messages when ExoPlayer encounters a playback error */
    val playerError: Flow<String?>
}
