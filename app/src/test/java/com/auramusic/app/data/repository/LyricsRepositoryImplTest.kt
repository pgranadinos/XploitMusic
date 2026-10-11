package com.auramusic.app.data.repository

import com.auramusic.app.core.util.Resource
import com.auramusic.app.data.dto.LrcLibResponse
import com.auramusic.app.data.remote.LrcLibApi
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class LyricsRepositoryImplTest {

    private lateinit var api: LrcLibApi
    private lateinit var repository: LyricsRepositoryImpl

    @Before
    fun setUp() {
        api = mockk()
        repository = LyricsRepositoryImpl(api)
    }

    @Test
    fun `cleans YouTube metadata before exact lookup`() = runTest {
        coEvery { api.getLyrics("Song", "Artist", 200.0) } returns lyrics("Song", "Artist", 200.0)

        val result = repository.getLyrics(
            trackName = "Artist - Song (Official Video)",
            artistName = "Artist - Topic",
            duration = 200.0
        )

        assertThat(result).isInstanceOf(Resource.Success::class.java)
        coVerify(exactly = 1) { api.getLyrics("Song", "Artist", 200.0) }
    }

    @Test
    fun `falls back to search and chooses matching lyrics`() = runTest {
        coEvery { api.getLyrics(any(), any(), any()) } throws IOException("404")
        coEvery { api.searchLyrics("Song Artist") } returns listOf(
            lyrics("Different song", "Other", 150.0),
            lyrics("Song", "Artist", 202.0, synced = null, plain = "Line one\nLine two")
        )

        val result = repository.getLyrics("Song", "Artist", 200.0)

        assertThat(result).isInstanceOf(Resource.Success::class.java)
        assertThat((result as Resource.Success).data?.plainLyrics).contains("Line one")
    }

    @Test
    fun `omits unknown duration and removes official channel label`() = runTest {
        coEvery { api.getLyrics("505", "Arctic Monkeys", null) } returns
            lyrics("505", "Arctic Monkeys", 253.0)

        val result = repository.getLyrics("505 (Official Audio)", "Official Arctic Monkeys", 0.0)

        assertThat(result).isInstanceOf(Resource.Success::class.java)
        coVerify(exactly = 1) { api.getLyrics("505", "Arctic Monkeys", null) }
    }

    @Test
    fun `retries search using title only`() = runTest {
        coEvery { api.getLyrics("Un Vino, Una Cerveza", "Pedro Suárez Vértiz", 276.0) } throws
            IOException("404")
        coEvery { api.searchLyrics("Un Vino, Una Cerveza Pedro Suárez Vértiz") } returns emptyList()
        coEvery { api.searchLyrics("Un Vino, Una Cerveza") } returns listOf(
            lyrics("Un Vino, Una Cerveza", "Pedro Suarez Vertiz", 275.0)
        )

        val result = repository.getLyrics(
            "Un Vino, Una Cerveza",
            "Pedro Suárez Vértiz Oficial",
            276.0
        )

        assertThat(result).isInstanceOf(Resource.Success::class.java)
        coVerify(exactly = 1) { api.searchLyrics("Un Vino, Una Cerveza") }
    }

    private fun lyrics(
        track: String,
        artist: String,
        duration: Double,
        synced: String? = "[00:01.00]Line",
        plain: String? = "Line"
    ) = LrcLibResponse(
        id = 1,
        trackName = track,
        artistName = artist,
        albumName = null,
        duration = duration,
        instrumental = false,
        plainLyrics = plain,
        syncedLyrics = synced
    )
}
