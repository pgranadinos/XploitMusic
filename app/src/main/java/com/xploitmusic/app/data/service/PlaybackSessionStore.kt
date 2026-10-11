package com.xploitmusic.app.data.service

import android.content.Context
import com.xploitmusic.app.domain.model.Song
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

internal data class SavedPlaybackSession(val songs: List<Song>, val index: Int, val positionMs: Long)

@Singleton
class PlaybackSessionStore @Inject constructor(@ApplicationContext context: Context) {
    private val preferences = context.getSharedPreferences("playback_session", Context.MODE_PRIVATE)
    private val gson = Gson()

    internal fun save(session: SavedPlaybackSession) {
        if (session.index !in session.songs.indices) return
        val sanitized = session.copy(
            // Do not reuse expiring remote audio URLs after a process restart.
            songs = session.songs.map { song -> song.copy(streamUrl = song.streamUrl?.takeIf {
                it.startsWith("content://") || it.startsWith("file://") || it.startsWith("/")
            }) },
            positionMs = session.positionMs.coerceAtLeast(0)
        )
        preferences.edit().putString("snapshot", gson.toJson(sanitized)).apply()
    }

    internal fun load(): SavedPlaybackSession? = runCatching {
        val json = preferences.getString("snapshot", null) ?: return null
        gson.fromJson(json, SavedPlaybackSession::class.java).takeIf {
            it.index in it.songs.indices && it.positionMs >= 0 &&
                it.songs.all { song -> song.id.isNotBlank() && song.title.isNotBlank() }
        }
    }.getOrNull()
}
