package com.auramusic.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persists recent search queries with optional result context.
 * Used for search history display and suggestion ranking.
 */
@Entity(tableName = "recent_searches")
data class SearchHistoryEntity(
    @PrimaryKey val query: String,           // normalized lowercase
    val displayQuery: String,                // original casing for display
    val resultType: String? = null,          // "track", "artist", "playlist", null = raw text
    val resultId: String? = null,            // song.id or channel URL if user clicked a result
    val resultTitle: String? = null,         // for showing "Bad Bunny — artist" in history
    val resultThumbnail: String? = null,     // thumbnail for rich history items
    val searchedAt: Long,                    // System.currentTimeMillis()
    val clickedResult: Boolean = false,      // true if user tapped a result (vs just searched)
    val playedFromSearch: Boolean = false     // true if user played a song from this search
)
