package com.auramusic.app.data.repository

import android.util.Log
import com.auramusic.app.domain.model.RepeatMode
import com.auramusic.app.domain.model.SmartShuffler
import com.auramusic.app.domain.model.Song
import com.auramusic.app.domain.repository.QueueManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory queue manager — the single source of truth for queue state.
 *
 * Migrates queue logic out of PlayerViewModel into a testable,
 * lifecycle-independent singleton.
 */
@Singleton
class QueueManagerImpl @Inject constructor() : QueueManager {

    companion object {
        private const val TAG = "XploitDebug"
    }

    // ── Internal state ──
    private val _queue = MutableStateFlow<List<Song>>(emptyList())
    private val _currentIndex = MutableStateFlow(-1)
    private val _currentTrack = MutableStateFlow<Song?>(null)
    private val _upNext = MutableStateFlow<List<Song>>(emptyList())
    private val _repeatMode = MutableStateFlow(RepeatMode.OFF)
    private val _isShuffleEnabled = MutableStateFlow(false)
    private val _playedIds = mutableSetOf<String>()

    // Original (unshuffled) order — kept for unshuffle restore
    private var _originalQueue: List<Song> = emptyList()

    // ── Observable State ──

    override val currentTrack: StateFlow<Song?> = _currentTrack.asStateFlow()
    override val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()
    override val queue: StateFlow<List<Song>> = _queue.asStateFlow()
    override val upNext: StateFlow<List<Song>> = _upNext.asStateFlow()
    override val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()
    override val isShuffleEnabled: StateFlow<Boolean> = _isShuffleEnabled.asStateFlow()
    override val playedIds: Set<String> get() = _playedIds.toSet()

    // ── Queue Manipulation ──

    override fun setQueue(songs: List<Song>, startIndex: Int) {
        _originalQueue = songs.toList()
        val idx = startIndex.coerceIn(0, (songs.size - 1).coerceAtLeast(0))

        val ordered = if (_isShuffleEnabled.value && songs.size > 1) {
            // Keep the starting song at index 0, shuffle the rest
            val startSong = songs[idx]
            val remaining = songs.filterIndexed { i, _ -> i != idx }
            listOf(startSong) + SmartShuffler.shuffle(remaining)
        } else {
            songs
        }

        _queue.value = ordered
        _currentIndex.value = if (_isShuffleEnabled.value) 0 else idx
        updateCurrent()
        Log.d(TAG, "QueueManager: setQueue(${songs.size} songs, startIndex=$idx)")
    }

    override fun addToUpNext(song: Song) {
        val q = _queue.value.toMutableList()
        val insertAt = (_currentIndex.value + 1).coerceAtMost(q.size)
        q.add(insertAt, song)
        _queue.value = q
        updateUpNext()
        Log.d(TAG, "QueueManager: addToUpNext('${song.title}') at position $insertAt")
    }

    override fun addToEndOfQueue(song: Song) {
        _queue.value = _queue.value + song
        updateUpNext()
        Log.d(TAG, "QueueManager: addToEndOfQueue('${song.title}')")
    }

    override fun removeFromQueue(index: Int) {
        val q = _queue.value.toMutableList()
        if (index < 0 || index >= q.size) return

        q.removeAt(index)
        _queue.value = q

        // Adjust current index
        val ci = _currentIndex.value
        if (index < ci) {
            _currentIndex.value = ci - 1
        } else if (index == ci) {
            // Removed current track — advance to same index (next song)
            updateCurrent()
        }
        updateUpNext()
        Log.d(TAG, "QueueManager: removeFromQueue($index)")
    }

    override fun moveInQueue(from: Int, to: Int) {
        val q = _queue.value.toMutableList()
        if (from < 0 || from >= q.size || to < 0 || to >= q.size) return

        val item = q.removeAt(from)
        q.add(to, item)
        _queue.value = q

        // Adjust current index
        val ci = _currentIndex.value
        _currentIndex.value = when {
            from == ci -> to
            from < ci && to >= ci -> ci - 1
            from > ci && to <= ci -> ci + 1
            else -> ci
        }
        updateUpNext()
        Log.d(TAG, "QueueManager: moveInQueue($from → $to)")
    }

    override fun clearUpcoming() {
        val ci = _currentIndex.value
        _queue.value = _queue.value.take(ci + 1)
        updateUpNext()
        Log.d(TAG, "QueueManager: clearUpcoming()")
    }

    // ── Navigation ──

    override fun skipToNext(): Song? {
        val q = _queue.value
        val ci = _currentIndex.value

        val nextIndex = when {
            ci + 1 < q.size -> ci + 1
            _repeatMode.value == RepeatMode.ALL && q.isNotEmpty() -> 0
            else -> return null
        }

        _currentIndex.value = nextIndex
        updateCurrent()
        Log.d(TAG, "QueueManager: skipToNext() → index $nextIndex, '${_currentTrack.value?.title}'")
        return _currentTrack.value
    }

    override fun skipToPrevious(): Song? {
        val ci = _currentIndex.value
        val q = _queue.value

        val prevIndex = when {
            ci - 1 >= 0 -> ci - 1
            _repeatMode.value == RepeatMode.ALL && q.isNotEmpty() -> q.size - 1
            else -> return null
        }

        _currentIndex.value = prevIndex
        updateCurrent()
        Log.d(TAG, "QueueManager: skipToPrevious() → index $prevIndex")
        return _currentTrack.value
    }

    override fun skipToIndex(index: Int): Song? {
        val q = _queue.value
        if (index < 0 || index >= q.size) return null

        _currentIndex.value = index
        updateCurrent()
        return _currentTrack.value
    }

    // ── Modes ──

    override fun setRepeatMode(mode: RepeatMode) {
        _repeatMode.value = mode
        Log.d(TAG, "QueueManager: setRepeatMode($mode)")
    }

    override fun toggleShuffle() {
        val wasShuffled = _isShuffleEnabled.value
        _isShuffleEnabled.value = !wasShuffled

        val currentSong = _currentTrack.value
        val q = _queue.value

        if (!wasShuffled && q.size > 1 && currentSong != null) {
            // Turning ON: shuffle upcoming, keep current at position
            val ci = _currentIndex.value
            val before = q.take(ci + 1)
            val after = q.drop(ci + 1)
            _queue.value = before + SmartShuffler.shuffle(after)
        } else if (wasShuffled && currentSong != null) {
            // Turning OFF: restore original order, find current song in original
            val origIdx = _originalQueue.indexOfFirst { it.id == currentSong.id }
            if (origIdx >= 0) {
                _queue.value = _originalQueue
                _currentIndex.value = origIdx
            }
        }

        updateUpNext()
        Log.d(TAG, "QueueManager: toggleShuffle() → ${_isShuffleEnabled.value}")
    }

    // ── Autoplay ──

    override fun appendAutoplayTracks(songs: List<Song>) {
        val deduped = songs.filter { it.id !in _playedIds && it.id !in _queue.value.map { q -> q.id } }
        if (deduped.isEmpty()) return
        _queue.value = _queue.value + deduped
        updateUpNext()
        Log.d(TAG, "QueueManager: appendAutoplayTracks(${deduped.size} new)")
    }

    override fun hasNext(): Boolean {
        val ci = _currentIndex.value
        val q = _queue.value
        return ci + 1 < q.size || (_repeatMode.value == RepeatMode.ALL && q.isNotEmpty())
    }

    override fun peekNext(): Song? {
        val ci = _currentIndex.value
        val q = _queue.value
        return when {
            ci + 1 < q.size -> q[ci + 1]
            _repeatMode.value == RepeatMode.ALL && q.isNotEmpty() -> q[0]
            else -> null
        }
    }

    override fun markAsPlayed(songId: String) {
        _playedIds.add(songId)
    }

    // ── Internal helpers ──

    private fun updateCurrent() {
        val q = _queue.value
        val ci = _currentIndex.value
        val song = if (ci in q.indices) q[ci] else null
        _currentTrack.value = song
        if (song != null) _playedIds.add(song.id)
        updateUpNext()
    }

    private fun updateUpNext() {
        val q = _queue.value
        val ci = _currentIndex.value
        _upNext.value = if (ci + 1 < q.size) q.drop(ci + 1) else emptyList()
    }
}
