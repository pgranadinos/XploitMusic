package com.xploitmusic.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "playlists",
    indices = [Index(value = ["source", "sourceId"], unique = true)]
)
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val playlistId: Long = 0,
    val name: String,
    val coverUrl: String? = null,
    val source: String? = null,
    val sourceId: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
