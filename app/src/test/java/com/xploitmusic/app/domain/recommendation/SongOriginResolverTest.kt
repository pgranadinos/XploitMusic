package com.xploitmusic.app.domain.recommendation

import com.google.common.truth.Truth.assertThat
import com.xploitmusic.app.data.local.entity.DownloadedTrackEntity
import com.xploitmusic.app.domain.model.Song
import com.xploitmusic.app.domain.model.SongOrigin
import org.junit.Test

class SongOriginResolverTest {

    private fun song(id: String, title: String = "Song $id", artist: String = "Artist"): Song =
        Song(id, title, artist, "", 180)

    private fun downloadedTrack(songId: String, status: String): DownloadedTrackEntity =
        DownloadedTrackEntity(
            songId = songId,
            title = "Song $songId",
            artist = "Artist",
            thumbnailUrl = "",
            durationSeconds = 180,
            status = status,
            quality = "HIGH",
            filePath = "/path/$songId",
            fileSizeBytes = 1000L,
            lastAccessedAt = System.currentTimeMillis()
        )

    @Test
    fun `DOWNLOAD wins over FAVORITE and RECOMMENDED`() {
        val downloads = listOf(downloadedTrack("s1", "COMPLETED"))
        val favorites = listOf(song("s1"))
        val recommended = setOf("s1")

        val origin = SongOriginResolver.resolve("s1", downloads, favorites, recommended)

        assertThat(origin).isEqualTo(SongOrigin.DOWNLOAD)
    }

    @Test
    fun `FAVORITE wins over RECOMMENDED`() {
        val downloads = emptyList<DownloadedTrackEntity>()
        val favorites = listOf(song("s1"))
        val recommended = setOf("s1")

        val origin = SongOriginResolver.resolve("s1", downloads, favorites, recommended)

        assertThat(origin).isEqualTo(SongOrigin.FAVORITE)
    }

    @Test
    fun `RECOMMENDED alone returns RECOMMENDED`() {
        val downloads = emptyList<DownloadedTrackEntity>()
        val favorites = emptyList<Song>()
        val recommended = setOf("s1")

        val origin = SongOriginResolver.resolve("s1", downloads, favorites, recommended)

        assertThat(origin).isEqualTo(SongOrigin.RECOMMENDED)
    }

    @Test
    fun `download in DOWNLOADING or FAILED status with favorite returns FAVORITE`() {
        val downloadsDownloading = listOf(downloadedTrack("s1", "DOWNLOADING"))
        val downloadsFailed = listOf(downloadedTrack("s2", "FAILED"))
        val favorites = listOf(song("s1"), song("s2"))
        val recommended = setOf("s1", "s2")

        val origin1 = SongOriginResolver.resolve("s1", downloadsDownloading, favorites, recommended)
        val origin2 = SongOriginResolver.resolve("s2", downloadsFailed, favorites, recommended)

        assertThat(origin1).isEqualTo(SongOrigin.FAVORITE)
        assertThat(origin2).isEqualTo(SongOrigin.FAVORITE)
    }

    @Test
    fun `song without any matching origin returns null`() {
        val downloads = emptyList<DownloadedTrackEntity>()
        val favorites = emptyList<Song>()
        val recommended = emptySet<String>()

        val origin = SongOriginResolver.resolve("s1", downloads, favorites, recommended)

        assertThat(origin).isNull()
    }
}
