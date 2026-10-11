package com.xploitmusic.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "songs",
    indices = [
        Index(value = ["isFavorite"]),
        Index(value = ["lastPlayedAt"])
    ]
)
data class SongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String,
    val durationSeconds: Long,
    val streamUrl: String? = null,
    val genre: String? = null,
    val isFavorite: Boolean = false,
    val likedAt: Long? = null,
    val lastPlayedAt: Long? = null,
    val playCount: Int = 0
)
