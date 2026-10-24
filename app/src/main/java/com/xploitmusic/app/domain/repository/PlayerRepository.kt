package com.xploitmusic.app.domain.repository

import com.xploitmusic.app.domain.model.PlayerError
import com.xploitmusic.app.domain.model.Song
import kotlinx.coroutines.flow.Flow

interface PlayerRepository {
    /** Emits the currently loaded Song reconstructed from MediaItem metadata, or null if nothing is loaded */
    val currentSong: Flow<Song?>
    val isPlaying: Flow<Boolean>
    val currentPosition: Flow<Long>
    val duration: Flow<Long>
    val trackEnded: Flow<Unit>

    /** Emits the mediaId of the new track when ExoPlayer transitions between items */
    val mediaItemTransition: Flow<String?>

    suspend fun play()
    suspend fun awaitPlaybackStarted(songId: String)
    suspend fun pause()
    suspend fun seekTo(position: Long)
    suspend fun prepare(url: String, song: Song)
    suspend fun addNextMediaItem(url: String, song: Song)
    suspend fun clearUpcomingMediaItems()
    suspend fun clearQueue()
    suspend fun hasNextMediaItem(): Boolean
    suspend fun skipToNext()
    fun release()

    // Shuffle & Repeat controls
    fun setRepeatMode(repeatMode: Int)
    fun setShuffleModeEnabled(shuffleModeEnabled: Boolean)

    /** Emits structured error information when ExoPlayer encounters a playback error */
    val playerError: Flow<PlayerError>
}
