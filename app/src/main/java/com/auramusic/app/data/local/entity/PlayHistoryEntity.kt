package com.auramusic.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "play_history")
data class PlayHistoryEntity(
    @PrimaryKey
    val contextId: String, // ID of the Album, Playlist, or Song
    val title: String,
    val description: String,
    val type: String, // "ALBUM", "PLAYLIST", "RADIO", "SONG"
    val imageUrl: String,
    val playedAtTimestamp: Long,
    val progressMs: Long = 0L // Exact position if applicable
)
