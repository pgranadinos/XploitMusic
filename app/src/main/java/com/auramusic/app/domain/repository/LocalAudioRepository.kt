package com.auramusic.app.domain.repository

import com.auramusic.app.domain.model.LocalAudioFolder
import com.auramusic.app.domain.model.Song

interface LocalAudioRepository {
    suspend fun getLocalSongs(): List<Song>
    fun getSelectedFolders(): List<LocalAudioFolder>
    fun addSelectedFolder(uri: String): Boolean
    fun removeSelectedFolder(uri: String)
}
