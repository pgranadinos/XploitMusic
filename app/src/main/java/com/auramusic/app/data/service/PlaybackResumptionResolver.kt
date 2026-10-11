package com.auramusic.app.data.service

import android.net.Uri
import androidx.media3.common.MediaItem
import com.auramusic.app.core.media.toMediaMetadata
import com.auramusic.app.core.util.Resource
import com.auramusic.app.domain.usecase.GetAudioStreamUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

internal data class ResolvedPlaybackSession(val saved: SavedPlaybackSession, val item: MediaItem)

class PlaybackResumptionResolver @Inject constructor(
    private val store: PlaybackSessionStore,
    private val offline: OfflineAwareMediaSourceFactory,
    private val audio: GetAudioStreamUseCase
) {
    internal suspend fun loadSavedSession(): SavedPlaybackSession? =
        withContext(Dispatchers.IO) { store.load() }

    internal suspend fun resolve(): ResolvedPlaybackSession? =
        loadSavedSession()?.let { resolve(it) }

    internal suspend fun resolve(saved: SavedPlaybackSession): ResolvedPlaybackSession? = withTimeoutOrNull(20_000L) {
        withContext(Dispatchers.IO) {
            val song = saved.songs.getOrNull(saved.index) ?: return@withContext null
            val local = offline.resolveUri(song.id, song.streamUrl.orEmpty())
            val uri = if (local.toString().isNotBlank()) local else {
                if (song.id.startsWith("local_")) return@withContext null
                val result = audio(song.id)
                val url = (result as? Resource.Success)?.data?.takeIf { it.isNotBlank() }
                    ?: return@withContext null
                Uri.parse(url)
            }
            ResolvedPlaybackSession(saved, MediaItem.Builder().setMediaId(song.id).setUri(uri)
                .setMediaMetadata(song.toMediaMetadata()).build())
        }
    }
}
