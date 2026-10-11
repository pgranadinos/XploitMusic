package com.xploitmusic.app.domain.repository

import com.xploitmusic.app.domain.model.LocalAudioFolder
import com.xploitmusic.app.domain.model.Song

interface LocalAudioRepository {
    suspend fun getLocalSongs(): List<Song>
    fun getSelectedFolders(): List<LocalAudioFolder>
    fun addSelectedFolder(uri: String): Boolean
    fun removeSelectedFolder(uri: String)
}
