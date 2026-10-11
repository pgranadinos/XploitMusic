package com.auramusic.app.data.service

import android.content.Context
import com.auramusic.app.domain.model.Song
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class PlaybackSessionStoreTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private fun song(id: String, url: String) = Song(id, id, "Artist", "", 180, url)

    @Before fun clear() {
        context.getSharedPreferences("playback_session", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun `new store recovers order index position and local URI but not remote URL`() {
        PlaybackSessionStore(context).save(SavedPlaybackSession(listOf(
            song("remote", "https://example.com/expired"), song("local_1", "content://media/1")
        ), 1, 42_000L))
        val restored = PlaybackSessionStore(context).load()!!
        assertThat(restored.index).isEqualTo(1)
        assertThat(restored.positionMs).isEqualTo(42_000L)
        assertThat(restored.songs.map { it.id }).containsExactly("remote", "local_1").inOrder()
        assertThat(restored.songs[0].streamUrl).isNull()
        assertThat(restored.songs[1].streamUrl).isEqualTo("content://media/1")
    }

    @Test fun `invalid index does not overwrite usable session`() {
        val store = PlaybackSessionStore(context)
        val valid = SavedPlaybackSession(listOf(song("a", "")), 0, 123)
        store.save(valid)
        store.save(valid.copy(index = 5))
        assertThat(store.load()!!.positionMs).isEqualTo(123L)
    }

    @Test fun `corrupted snapshot falls back safely`() {
        context.getSharedPreferences("playback_session", Context.MODE_PRIVATE)
            .edit().putString("snapshot", "{}").commit()
        assertThat(PlaybackSessionStore(context).load()).isNull()
    }
}
