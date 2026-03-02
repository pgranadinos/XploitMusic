package com.auramusic.app.domain.repository

import com.auramusic.app.domain.model.Song

interface LocalAudioRepository {
    suspend fun getLocalSongs(): List<Song>
}
