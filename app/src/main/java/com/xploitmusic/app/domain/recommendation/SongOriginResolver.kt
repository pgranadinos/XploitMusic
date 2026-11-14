package com.xploitmusic.app.domain.recommendation

import com.xploitmusic.app.data.local.entity.DownloadedTrackEntity
import com.xploitmusic.app.domain.model.Song
import com.xploitmusic.app.domain.model.SongOrigin

object SongOriginResolver {
    fun resolve(
        songId: String,
        completedDownloads: List<DownloadedTrackEntity>,
        favorites: List<Song>,
        recommendedIds: Set<String>
    ): SongOrigin? {
        val isCompletedDownload = completedDownloads.any { it.songId == songId && it.status == "COMPLETED" }
        if (isCompletedDownload) return SongOrigin.DOWNLOAD

        val isFav = favorites.any { it.id == songId }
        if (isFav) return SongOrigin.FAVORITE

        if (songId in recommendedIds) return SongOrigin.RECOMMENDED

        return null
    }
}
