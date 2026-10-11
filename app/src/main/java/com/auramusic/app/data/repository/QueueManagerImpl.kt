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
        internal const val MAX_UPCOMING_TRACKS = 50
    }

    // ── Internal state ──
    private val _queue = MutableStateFlow<List<Song>>(emptyList())
    private val _currentIndex = MutableStateFlow(-1)
    private val _currentTrack = MutableStateFlow<Song?>(null)
    private val _upNext = MutableStateFlow<List<Song>>(emptyList())
    private val _repeatMode = MutableStateFlow(RepeatMode.OFF)
    private val _isShuffleEnabled = MutableStateFlow(false)
    private val _isAutoplayEnabled = MutableStateFlow(true)
    private val _playedIds = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    // Original (unshuffled) order — kept for unshuffle restore
    private var _originalQueue: List<Song> = emptyList()

    // ── Observable State ──

    override val currentTrack: StateFlow<Song?> = _currentTrack.asStateFlow()
    override val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()
    override val queue: StateFlow<List<Song>> = _queue.asStateFlow()
    override val upNext: StateFlow<List<Song>> = _upNext.asStateFlow()
    override val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()
    override val isShuffleEnabled: StateFlow<Boolean> = _isShuffleEnabled.asStateFlow()
    override val isAutoplayEnabled: StateFlow<Boolean> = _isAutoplayEnabled.asStateFlow()
    override val playedIds: Set<String>
        get() = synchronized(_playedIds) { _playedIds.toSet() }

    // ── Queue Manipulation ──

    @Synchronized
    override fun setQueue(songs: List<Song>, startIndex: Int) {
        _originalQueue = songs.toList()
        val idx = if (songs.isEmpty()) -1 else startIndex.coerceIn(0, songs.lastIndex)

        val ordered = if (_isShuffleEnabled.value && songs.size > 1) {
            // Keep the starting song at index 0, shuffle the rest
            val startSong = songs[idx]
            val remaining = songs.filterIndexed { i, _ -> i != idx }
            listOf(startSong) + SmartShuffler.shuffle(remaining)
        } else {
            songs
        }

        _queue.value = ordered
        _currentIndex.value = if (_isShuffleEnabled.value && songs.isNotEmpty()) 0 else idx
        updateCurrent()
        Log.d(TAG, "QueueManager: setQueue(${songs.size} songs, startIndex=$idx)")
    }

    @Synchronized
    override fun addToUpNext(song: Song) {
        val q = _queue.value.toMutableList()
        val insertAt = (_currentIndex.value + 1).coerceAtMost(q.size)
        q.add(insertAt, song)
        _queue.value = q
        if (_isShuffleEnabled.value) {
            val original = _originalQueue.toMutableList()
            val currentId = _currentTrack.value?.id
            val originalCurrentIndex = original.indexOfFirst { it.id == currentId }
            original.add((originalCurrentIndex + 1).coerceIn(0, original.size), song)
            _originalQueue = original
        } else {
            _originalQueue = q
        }
        updateUpNext()
        Log.d(TAG, "QueueManager: addToUpNext('${song.title}') at position $insertAt")
    }

    @Synchronized
    override fun addToEndOfQueue(song: Song) {
        _queue.value = _queue.value + song
        _originalQueue = _originalQueue + song
        updateUpNext()
        Log.d(TAG, "QueueManager: addToEndOfQueue('${song.title}')")
    }

    @Synchronized
    override fun removeFromQueue(index: Int) {
        val q = _queue.value.toMutableList()
        if (index < 0 || index >= q.size) return

        val removed = q.removeAt(index)
        _queue.value = q

        _originalQueue = if (_isShuffleEnabled.value) {
            val original = _originalQueue.toMutableList()
            val originalIndex = original.indexOfFirst { it.id == removed.id }
            if (originalIndex >= 0) original.removeAt(originalIndex)
            original
        } else {
            q
        }

        // Adjust current index
        val ci = _currentIndex.value
        _currentIndex.value = when {
            q.isEmpty() -> -1
            index < ci -> ci - 1
            index == ci -> ci.coerceAtMost(q.lastIndex)
            else -> ci
        }
        updateCurrent()
        Log.d(TAG, "QueueManager: removeFromQueue($index)")
    }

    @Synchronized
    override fun moveInQueue(from: Int, to: Int) {
        val q = _queue.value.toMutableList()
        if (from < 0 || from >= q.size || to < 0 || to >= q.size) return

        val item = q.removeAt(from)
        q.add(to, item)
        _queue.value = q
        if (!_isShuffleEnabled.value) _originalQueue = q

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

    @Synchronized
    override fun clearUpcoming() {
        val ci = _currentIndex.value
        val kept = _queue.value.take(ci + 1)
        _queue.value = kept
        _originalQueue = if (_isShuffleEnabled.value) {
            val keptIds = kept.mapTo(mutableSetOf()) { it.id }
            _originalQueue.filter { it.id in keptIds }
        } else {
            kept
        }
        updateUpNext()
        Log.d(TAG, "QueueManager: clearUpcoming()")
    }

    // ── Navigation ──

    @Synchronized
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

    @Synchronized
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

    @Synchronized
    override fun skipToIndex(index: Int): Song? {
        val q = _queue.value
        if (index < 0 || index >= q.size) return null

        _currentIndex.value = index
        updateCurrent()
        return _currentTrack.value
    }

    // ── Modes ──

    @Synchronized
    override fun setRepeatMode(mode: RepeatMode) {
        _repeatMode.value = mode
        Log.d(TAG, "QueueManager: setRepeatMode($mode)")
    }

    @Synchronized
    override fun toggleShuffle() {
        val wasShuffled = _isShuffleEnabled.value
        _isShuffleEnabled.value = !wasShuffled

        val currentSong = _currentTrack.value
        val q = _queue.value

        if (!wasShuffled && q.size > 1 && currentSong != null) {
            // Turning ON: shuffle upcoming, keep current at position
            _originalQueue = q
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

        updateCurrent()
        Log.d(TAG, "QueueManager: toggleShuffle() → ${_isShuffleEnabled.value}")
    }

    @Synchronized
    override fun toggleAutoplay() {
        _isAutoplayEnabled.value = !_isAutoplayEnabled.value
        Log.d(TAG, "QueueManager: toggleAutoplay() → ${_isAutoplayEnabled.value}")
    }

    // ── Autoplay ──

    @Synchronized
    override fun appendAutoplayTracks(songs: List<Song>) {
        val knownIds = _queue.value.mapTo(mutableSetOf()) { it.id }
        val availableSlots = (MAX_UPCOMING_TRACKS - _upNext.value.size).coerceAtLeast(0)
        if (availableSlots == 0) return
        val accepted = songs
            .asSequence()
            .filter { it.id !in _playedIds && knownIds.add(it.id) }
            .take(availableSlots)
            .toList()
        if (accepted.isEmpty()) return
        _queue.value = _queue.value + accepted
        _originalQueue = _originalQueue + accepted
        updateUpNext()
        Log.d(TAG, "QueueManager: appendAutoplayTracks(${accepted.size} new, ${_upNext.value.size} upcoming)")
    }

    @Synchronized
    override fun hasNext(): Boolean {
        val ci = _currentIndex.value
        val q = _queue.value
        return ci + 1 < q.size || (_repeatMode.value == RepeatMode.ALL && q.isNotEmpty())
    }

    @Synchronized
    override fun peekNext(): Song? {
        val ci = _currentIndex.value
        val q = _queue.value
        return when {
            ci + 1 < q.size -> q[ci + 1]
            _repeatMode.value == RepeatMode.ALL && q.isNotEmpty() -> q[0]
            else -> null
        }
    }

    @Synchronized
    override fun markAsPlayed(songId: String) {
        _playedIds.add(songId)
    }

    @Synchronized
    override fun resetPlayedHistory() {
        _playedIds.clear()
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
