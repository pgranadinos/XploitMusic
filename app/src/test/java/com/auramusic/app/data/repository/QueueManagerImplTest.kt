package com.auramusic.app.data.repository

import com.auramusic.app.domain.model.RepeatMode
import com.auramusic.app.domain.model.Song
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

class QueueManagerImplTest {

    private lateinit var queueManager: QueueManagerImpl

    private fun song(id: String, title: String = "Song $id", artist: String = "Artist") =
        Song(id = id, title = title, artist = artist, thumbnailUrl = "", durationSeconds = 180)

    @Before
    fun setUp() {
        queueManager = QueueManagerImpl()
    }

    // ═══════════════════════════════════════════════════════════════════
    // setQueue
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `setQueue initializes queue, currentTrack, currentIndex and upNext`() {
        val songs = listOf(song("1"), song("2"), song("3"))
        queueManager.setQueue(songs, startIndex = 1)

        assertThat(queueManager.queue.value).hasSize(3)
        assertThat(queueManager.currentIndex.value).isEqualTo(1)
        assertThat(queueManager.currentTrack.value).isEqualTo(song("2"))
        assertThat(queueManager.upNext.value).containsExactly(song("3"))
    }

    @Test
    fun `setQueue with empty list results in empty state`() {
        queueManager.setQueue(emptyList())

        assertThat(queueManager.queue.value).isEmpty()
        assertThat(queueManager.currentIndex.value).isEqualTo(0)
        assertThat(queueManager.currentTrack.value).isNull()
        assertThat(queueManager.upNext.value).isEmpty()
    }

    @Test
    fun `setQueue coerces out-of-bounds startIndex`() {
        val songs = listOf(song("1"), song("2"))
        queueManager.setQueue(songs, startIndex = 99)

        assertThat(queueManager.currentIndex.value).isEqualTo(1) // coerced to last index
        assertThat(queueManager.currentTrack.value?.id).isEqualTo("2")
    }

    @Test
    fun `setQueue with shuffle keeps starting song at position 0`() {
        val songs = listOf(song("1"), song("2"), song("3"), song("4"), song("5"))
        // Enable shuffle first
        queueManager.setQueue(songs, startIndex = 0)
        queueManager.toggleShuffle() // ON

        // Now set a new queue with shuffle enabled
        val newSongs = listOf(song("A"), song("B"), song("C"), song("D"))
        queueManager.setQueue(newSongs, startIndex = 2)

        // Starting song should be first
        assertThat(queueManager.queue.value.first().id).isEqualTo("C")
        assertThat(queueManager.currentIndex.value).isEqualTo(0)
        assertThat(queueManager.currentTrack.value?.id).isEqualTo("C")
        // All songs present (just reordered)
        assertThat(queueManager.queue.value.map { it.id }).containsExactly("A", "B", "C", "D")
    }

    // ═══════════════════════════════════════════════════════════════════
    // addToUpNext / addToEndOfQueue
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `addToUpNext inserts after current track`() {
        queueManager.setQueue(listOf(song("1"), song("2"), song("3")), startIndex = 0)
        queueManager.addToUpNext(song("X"))

        assertThat(queueManager.queue.value.map { it.id })
            .containsExactly("1", "X", "2", "3").inOrder()
    }

    @Test
    fun `addToEndOfQueue appends to the end`() {
        queueManager.setQueue(listOf(song("1"), song("2")), startIndex = 0)
        queueManager.addToEndOfQueue(song("Z"))

        assertThat(queueManager.queue.value.map { it.id })
            .containsExactly("1", "2", "Z").inOrder()
    }

    // ═══════════════════════════════════════════════════════════════════
    // removeFromQueue
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `removeFromQueue before current adjusts index down`() {
        queueManager.setQueue(listOf(song("1"), song("2"), song("3")), startIndex = 2)
        assertThat(queueManager.currentIndex.value).isEqualTo(2)

        queueManager.removeFromQueue(0) // remove "1"

        assertThat(queueManager.currentIndex.value).isEqualTo(1)
        assertThat(queueManager.currentTrack.value?.id).isEqualTo("3")
    }

    @Test
    fun `removeFromQueue after current does not change index`() {
        queueManager.setQueue(listOf(song("1"), song("2"), song("3")), startIndex = 0)
        queueManager.removeFromQueue(2) // remove "3"

        assertThat(queueManager.currentIndex.value).isEqualTo(0)
        assertThat(queueManager.currentTrack.value?.id).isEqualTo("1")
        assertThat(queueManager.upNext.value).containsExactly(song("2"))
    }

    @Test
    fun `removeFromQueue with invalid index does nothing`() {
        queueManager.setQueue(listOf(song("1")), startIndex = 0)
        queueManager.removeFromQueue(-1)
        queueManager.removeFromQueue(5)

        assertThat(queueManager.queue.value).hasSize(1)
    }

    // ═══════════════════════════════════════════════════════════════════
    // moveInQueue
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `moveInQueue reorders and updates currentIndex when current is moved`() {
        queueManager.setQueue(listOf(song("1"), song("2"), song("3")), startIndex = 0)
        queueManager.moveInQueue(0, 2) // move current to end

        assertThat(queueManager.queue.value.map { it.id })
            .containsExactly("2", "3", "1").inOrder()
        assertThat(queueManager.currentIndex.value).isEqualTo(2)
        assertThat(queueManager.currentTrack.value?.id).isEqualTo("1")
    }

    @Test
    fun `moveInQueue with invalid indices does nothing`() {
        queueManager.setQueue(listOf(song("1"), song("2")), startIndex = 0)
        queueManager.moveInQueue(-1, 0)
        queueManager.moveInQueue(0, 5)

        assertThat(queueManager.queue.value.map { it.id })
            .containsExactly("1", "2").inOrder()
    }

    // ═══════════════════════════════════════════════════════════════════
    // clearUpcoming
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `clearUpcoming keeps current and removes the rest`() {
        queueManager.setQueue(listOf(song("1"), song("2"), song("3")), startIndex = 0)
        queueManager.clearUpcoming()

        assertThat(queueManager.queue.value).containsExactly(song("1"))
        assertThat(queueManager.upNext.value).isEmpty()
        assertThat(queueManager.currentTrack.value?.id).isEqualTo("1")
    }

    // ═══════════════════════════════════════════════════════════════════
    // skipToNext
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `skipToNext advances and returns next song`() {
        queueManager.setQueue(listOf(song("1"), song("2"), song("3")), startIndex = 0)

        val next = queueManager.skipToNext()

        assertThat(next?.id).isEqualTo("2")
        assertThat(queueManager.currentIndex.value).isEqualTo(1)
    }

    @Test
    fun `skipToNext at end with repeat OFF returns null`() {
        queueManager.setQueue(listOf(song("1"), song("2")), startIndex = 1)
        queueManager.setRepeatMode(RepeatMode.OFF)

        val next = queueManager.skipToNext()

        assertThat(next).isNull()
    }

    @Test
    fun `skipToNext at end with repeat ALL wraps to index 0`() {
        queueManager.setQueue(listOf(song("1"), song("2")), startIndex = 1)
        queueManager.setRepeatMode(RepeatMode.ALL)

        val next = queueManager.skipToNext()

        assertThat(next?.id).isEqualTo("1")
        assertThat(queueManager.currentIndex.value).isEqualTo(0)
    }

    // ═══════════════════════════════════════════════════════════════════
    // skipToPrevious
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `skipToPrevious goes back one`() {
        queueManager.setQueue(listOf(song("1"), song("2"), song("3")), startIndex = 2)

        val prev = queueManager.skipToPrevious()

        assertThat(prev?.id).isEqualTo("2")
        assertThat(queueManager.currentIndex.value).isEqualTo(1)
    }

    @Test
    fun `skipToPrevious at start with repeat OFF returns null`() {
        queueManager.setQueue(listOf(song("1"), song("2")), startIndex = 0)
        queueManager.setRepeatMode(RepeatMode.OFF)

        val prev = queueManager.skipToPrevious()

        assertThat(prev).isNull()
    }

    @Test
    fun `skipToPrevious at start with repeat ALL wraps to last`() {
        queueManager.setQueue(listOf(song("1"), song("2"), song("3")), startIndex = 0)
        queueManager.setRepeatMode(RepeatMode.ALL)

        val prev = queueManager.skipToPrevious()

        assertThat(prev?.id).isEqualTo("3")
        assertThat(queueManager.currentIndex.value).isEqualTo(2)
    }

    // ═══════════════════════════════════════════════════════════════════
    // skipToIndex
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `skipToIndex jumps to correct position`() {
        queueManager.setQueue(listOf(song("1"), song("2"), song("3")), startIndex = 0)

        val result = queueManager.skipToIndex(2)

        assertThat(result?.id).isEqualTo("3")
        assertThat(queueManager.currentIndex.value).isEqualTo(2)
    }

    @Test
    fun `skipToIndex with invalid index returns null`() {
        queueManager.setQueue(listOf(song("1")), startIndex = 0)

        assertThat(queueManager.skipToIndex(-1)).isNull()
        assertThat(queueManager.skipToIndex(5)).isNull()
    }

    // ═══════════════════════════════════════════════════════════════════
    // toggleShuffle
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `toggleShuffle ON shuffles upcoming songs`() {
        val songs = listOf(song("1"), song("2"), song("3"), song("4"), song("5"))
        queueManager.setQueue(songs, startIndex = 0)

        queueManager.toggleShuffle()

        assertThat(queueManager.isShuffleEnabled.value).isTrue()
        // Current song should still be "1" at index 0
        assertThat(queueManager.currentTrack.value?.id).isEqualTo("1")
        assertThat(queueManager.currentIndex.value).isEqualTo(0)
        // All songs should still be present
        assertThat(queueManager.queue.value.map { it.id }).containsExactly("1", "2", "3", "4", "5")
        // First song stays the same (before = q.take(ci + 1))
        assertThat(queueManager.queue.value.first().id).isEqualTo("1")
    }

    @Test
    fun `toggleShuffle OFF restores original order`() {
        val songs = listOf(song("1"), song("2"), song("3"), song("4"), song("5"))
        queueManager.setQueue(songs, startIndex = 0)

        queueManager.toggleShuffle()  // ON
        queueManager.toggleShuffle()  // OFF

        assertThat(queueManager.isShuffleEnabled.value).isFalse()
        assertThat(queueManager.queue.value.map { it.id })
            .containsExactly("1", "2", "3", "4", "5").inOrder()
    }

    // ═══════════════════════════════════════════════════════════════════
    // appendAutoplayTracks
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `appendAutoplayTracks deduplicates against playedIds and existing queue`() {
        queueManager.setQueue(listOf(song("1"), song("2")), startIndex = 0)
        queueManager.markAsPlayed("3")

        queueManager.appendAutoplayTracks(listOf(song("1"), song("2"), song("3"), song("4"), song("5")))

        // "1" and "2" in queue, "3" in played, so only "4" and "5" are added
        val ids = queueManager.queue.value.map { it.id }
        assertThat(ids).containsExactly("1", "2", "4", "5").inOrder()
    }

    @Test
    fun `appendAutoplayTracks with all duplicates does nothing`() {
        queueManager.setQueue(listOf(song("1")), startIndex = 0)

        queueManager.appendAutoplayTracks(listOf(song("1")))

        assertThat(queueManager.queue.value).hasSize(1)
    }

    // ═══════════════════════════════════════════════════════════════════
    // hasNext / peekNext
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `hasNext returns true when songs remain`() {
        queueManager.setQueue(listOf(song("1"), song("2")), startIndex = 0)

        assertThat(queueManager.hasNext()).isTrue()
    }

    @Test
    fun `hasNext returns false at end with repeat OFF`() {
        queueManager.setQueue(listOf(song("1")), startIndex = 0)
        queueManager.setRepeatMode(RepeatMode.OFF)

        assertThat(queueManager.hasNext()).isFalse()
    }

    @Test
    fun `hasNext returns true at end with repeat ALL`() {
        queueManager.setQueue(listOf(song("1")), startIndex = 0)
        queueManager.setRepeatMode(RepeatMode.ALL)

        assertThat(queueManager.hasNext()).isTrue()
    }

    @Test
    fun `peekNext returns next song without advancing`() {
        queueManager.setQueue(listOf(song("1"), song("2"), song("3")), startIndex = 0)

        val peeked = queueManager.peekNext()

        assertThat(peeked?.id).isEqualTo("2")
        assertThat(queueManager.currentIndex.value).isEqualTo(0) // didn't advance
    }

    @Test
    fun `peekNext at end with repeat ALL returns first song`() {
        queueManager.setQueue(listOf(song("1"), song("2")), startIndex = 1)
        queueManager.setRepeatMode(RepeatMode.ALL)

        assertThat(queueManager.peekNext()?.id).isEqualTo("1")
    }

    // ═══════════════════════════════════════════════════════════════════
    // markAsPlayed
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `markAsPlayed registers song ID`() {
        queueManager.markAsPlayed("abc")

        assertThat(queueManager.playedIds).contains("abc")
    }

    @Test
    fun `setQueue auto-marks current track as played`() {
        queueManager.setQueue(listOf(song("1")), startIndex = 0)

        assertThat(queueManager.playedIds).contains("1")
    }
}
