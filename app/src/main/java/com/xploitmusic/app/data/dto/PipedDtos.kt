package com.xploitmusic.app.data.dto

data class PipedSearchResponse(
    val items: List<PipedSearchResultItem>,
    val nextpage: String?
)

data class PipedSearchResultItem(
    val url: String, // relative url e.g. /watch?v=VIDEO_ID or /channel/ID or /playlist?list=ID
    val type: String, // "stream", "channel", "playlist"
    val title: String,
    val thumbnail: String,
    val uploaderName: String = "", // Artist (empty for channels)
    val uploaderUrl: String? = null, // Channel URL
    val duration: Long = 0, // seconds (0 for non-stream types)
    val videos: Long = -1 // playlist video count (-1 if N/A)
)

data class PipedStreamResponse(
    val audioStreams: List<PipedAudioStream>,
    val relatedStreams: List<PipedRelatedStream>? = null,
    val title: String? = null,
    val uploader: String? = null,
    val thumbnailUrl: String? = null,
    val duration: Long? = null
)

data class PipedAudioStream(
    val url: String,
    val format: String, // "M4A", "WEBMA"
    val quality: String, // "128 kbps" etc
    val bitrate: Int,
    val codec: String?
)

data class PipedRelatedStream(
    val url: String, // "/watch?v=VIDEO_ID"
    val title: String,
    val thumbnail: String? = null,
    val uploaderName: String? = null,
    val duration: Long = 0 // seconds
)

// Trending response is a List<PipedTrendingItem>
data class PipedTrendingItem(
    val url: String, // "/watch?v=VIDEO_ID"
    val title: String,
    val thumbnail: String? = null,
    val uploaderName: String? = null,
    val duration: Long = 0
)
