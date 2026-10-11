package com.xploitmusic.app.data.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.KeyEvent
import com.xploitmusic.app.core.util.Resource
import com.xploitmusic.app.domain.model.Song
import com.xploitmusic.app.domain.usecase.GetAudioStreamUseCase
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class PlaybackResumptionResolverTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private lateinit var store: PlaybackSessionStore
    private val offline = mockk<OfflineAwareMediaSourceFactory>()
    private val audio = mockk<GetAudioStreamUseCase>()
    private lateinit var resolver: PlaybackResumptionResolver

    @Before fun setup() {
        context.getSharedPreferences("playback_session", Context.MODE_PRIVATE).edit().clear().commit()
        store = PlaybackSessionStore(context)
        resolver = PlaybackResumptionResolver(store, offline, audio)
        coEvery { offline.resolveUri(any(), any()) } answers { Uri.parse(secondArg()) }
    }

    private fun save(id: String = "remote", uri: String? = "https://expired") {
        store.save(SavedPlaybackSession(listOf(
            Song("previous", "Previous", "Artist", "", 180),
            Song(id, "Last song", "Artist", "", 180, uri)
        ), 1, 42_000))
    }

    @Test fun `resumption placeholder has a URI when saved song has no stream URL`() {
        val saved = SavedPlaybackSession(
            listOf(Song("remote", "Last song", "Artist", "", 180)),
            0,
            42_000
        )

        val item = resumptionPlaceholderItem(saved)

        assertThat(item.localConfiguration?.uri.toString())
            .isEqualTo("https://pending.invalid/resumption")
        assertThat(item.mediaMetadata.title.toString()).isEqualTo("Last song")
    }

    @Test fun `remote resumption refreshes URL and retains metadata queue and position`() = runBlocking {
        save()
        coEvery { audio("remote") } returns Resource.Success("https://fresh")
        val result = resolver.resolve()!!
        assertThat(result.item.localConfiguration!!.uri.toString()).isEqualTo("https://fresh")
        assertThat(result.item.mediaMetadata.title.toString()).isEqualTo("Last song")
        assertThat(result.saved.index).isEqualTo(1)
        assertThat(result.saved.positionMs).isEqualTo(42_000L)
        assertThat(result.saved.songs).hasSize(2)
    }

    @Test fun `download resumes without requesting network audio`() = runBlocking {
        save()
        coEvery { offline.resolveUri("remote", "") } returns Uri.parse("file:///music/song.m4a")
        assertThat(resolver.resolve()!!.item.localConfiguration!!.uri.scheme).isEqualTo("file")
        coVerify(exactly = 0) { audio(any()) }
    }

    @Test fun `local audio resumes without network`() = runBlocking {
        save("local_1", "content://media/1")
        assertThat(resolver.resolve()!!.item.localConfiguration!!.uri.toString()).isEqualTo("content://media/1")
        coVerify(exactly = 0) { audio(any()) }
    }

    @Test fun `missing snapshot returns no session`() = runBlocking {
        assertThat(resolver.resolve()).isNull()
        coVerify(exactly = 0) { audio(any()) }
    }

    @Test fun `unavailable audio does not destroy saved session`() = runBlocking {
        save()
        coEvery { audio("remote") } returns Resource.Error("Sin conexión")
        assertThat(resolver.resolve()).isNull()
        assertThat(store.load()!!.positionMs).isEqualTo(42_000L)
    }

    @Test(expected = CancellationException::class)
    fun `cancellation propagates`() = runBlocking {
        save()
        coEvery { audio("remote") } throws CancellationException()
        resolver.resolve()
        Unit
    }

    @Test fun `manifest exposes media button receiver`() {
        val receivers = context.packageManager.queryBroadcastReceivers(
            Intent(Intent.ACTION_MEDIA_BUTTON).setPackage(context.packageName), 0)
        assertThat(receivers.map { it.activityInfo.name })
            .contains("androidx.media3.session.MediaButtonReceiver")
    }

    @Test fun `cold media button startup only treats Play actions as resume requests`() {
        fun intent(keyCode: Int, action: Int = KeyEvent.ACTION_DOWN) =
            Intent(Intent.ACTION_MEDIA_BUTTON)
                .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(action, keyCode))

        assertThat(mediaButtonRequestsPlay(intent(KeyEvent.KEYCODE_MEDIA_PLAY), false)).isTrue()
        assertThat(mediaButtonRequestsPlay(intent(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE), false)).isTrue()
        assertThat(mediaButtonRequestsPlay(intent(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE), true)).isFalse()
        assertThat(mediaButtonRequestsPlay(intent(KeyEvent.KEYCODE_HEADSETHOOK), false)).isTrue()
        assertThat(mediaButtonRequestsPlay(intent(KeyEvent.KEYCODE_MEDIA_NEXT), false)).isFalse()
        assertThat(mediaButtonRequestsPlay(intent(KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.ACTION_UP), false)).isFalse()
        assertThat(mediaButtonRequestsPlay(Intent(Intent.ACTION_MEDIA_BUTTON), false)).isFalse()
    }
}
