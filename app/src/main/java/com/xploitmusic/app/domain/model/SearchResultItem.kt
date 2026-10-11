package com.xploitmusic.app.domain.model

sealed class SearchResultItem {
    data class SongResult(val song: Song) : SearchResultItem()
    data class ChannelResult(
        val name: String,
        val url: String,
        val thumbnail: String
    ) : SearchResultItem()
    data class PlaylistResult(
        val name: String,
        val url: String,
        val thumbnail: String,
        val videoCount: Long
    ) : SearchResultItem()
}

data class SearchResults(
    val songs: List<Song>,
    val channels: List<SearchResultItem.ChannelResult>,
    val playlists: List<SearchResultItem.PlaylistResult>,
    /** Categories that failed during search (empty = all succeeded) */
    val failedCategories: Set<String> = emptySet()
)
