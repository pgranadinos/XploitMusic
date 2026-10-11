package com.xploitmusic.app.domain.repository

import com.xploitmusic.app.domain.model.RepeatMode
import com.xploitmusic.app.domain.model.Song
import kotlinx.coroutines.flow.StateFlow

/**
 * Manages the playback queue as the single source of truth for
 * what's playing, what's next, and queue ordering.
 *
 * Replaces the ad-hoc _playlist/_autoplayQueue/_currentIndex
 * scattered across PlayerViewModel.
 */
interface QueueManager {

    // ── Observable State ──

    /** Currently playing song, or null if queue is empty */
    val currentTrack: StateFlow<Song?>

    /** Current index in the full queue */
    val currentIndex: StateFlow<Int>

    /** Full ordered queue (user-added + autoplay) */
    val queue: StateFlow<List<Song>>

    /** Only upcoming songs after current (for "Up Next" UI) */
    val upNext: StateFlow<List<Song>>

    /** Current repeat mode */
    val repeatMode: StateFlow<RepeatMode>

    /** Whether shuffle is active */
    val isShuffleEnabled: StateFlow<Boolean>

    /** Whether playback may continue automatically when the queue item ends */
    val isAutoplayEnabled: StateFlow<Boolean>

    /** IDs of songs already played (for autoplay dedup) */
    val playedIds: Set<String>

    // ── Queue Manipulation ──

    /** Replace the entire queue with [songs], starting at [startIndex] */
    fun setQueue(songs: List<Song>, startIndex: Int = 0)

    /** Insert [song] immediately after the current track */
    fun addToUpNext(song: Song)

    /** Append [song] to the end of the queue */
    fun addToEndOfQueue(song: Song)

    /** Remove song at [index] from the queue */
    fun removeFromQueue(index: Int)

    /** Move a song from position [from] to position [to] (drag-to-reorder) */
    fun moveInQueue(from: Int, to: Int)

    /** Clear all upcoming songs (keep current) */
    fun clearUpcoming()

    // ── Navigation ──

    /** Advance to next song. Returns the next [Song] or null if queue ended */
    fun skipToNext(): Song?

    /** Go back to previous song. Returns the [Song] or null */
    fun skipToPrevious(): Song?

    /** Jump to a specific index. Returns the [Song] or null */
    fun skipToIndex(index: Int): Song?

    // ── Modes ──

    /** Set repeat mode */
    fun setRepeatMode(mode: RepeatMode)

    /** Toggle shuffle on/off. Reshuffles upcoming when turning on. */
    fun toggleShuffle()

    /** Toggle automatic continuation on/off. */
    fun toggleAutoplay()

    // ── Autoplay ──

    /** Append autoplay tracks (from related streams), deduplicating against played IDs */
    fun appendAutoplayTracks(songs: List<Song>)

    /** Check if there's a next song available */
    fun hasNext(): Boolean

    /** Peek at the next song without advancing */
    fun peekNext(): Song?

    /** Record a song ID as played */
    fun markAsPlayed(songId: String)

    /** Clear played IDs before starting a new independent playback session */
    fun resetPlayedHistory()
}
