package com.auramusic.app.core.media

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.auramusic.app.domain.model.Song

private const val EXTRA_DURATION_SECONDS = "com.auramusic.app.extra.DURATION_SECONDS"

internal fun Song.toMediaMetadata(): MediaMetadata {
    val builder = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artist)
        .setGenre(genre)
        .setExtras(Bundle().apply {
            putLong(EXTRA_DURATION_SECONDS, durationSeconds.coerceAtLeast(0L))
        })

    if (thumbnailUrl.isNotBlank()) builder.setArtworkUri(Uri.parse(thumbnailUrl))
    return builder.build()
}

internal fun MediaItem.toSongOrNull(): Song? {
    val id = mediaId.takeIf { it.isNotBlank() } ?: return null
    val metadata = mediaMetadata
    return Song(
        id = id,
        title = metadata.title?.toString() ?: "Sin título",
        artist = metadata.artist?.toString() ?: "Artista desconocido",
        thumbnailUrl = metadata.artworkUri?.toString().orEmpty(),
        durationSeconds = metadata.extras?.getLong(EXTRA_DURATION_SECONDS) ?: 0L,
        streamUrl = null,
        genre = metadata.genre?.toString()
    )
}
