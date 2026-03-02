package com.auramusic.app.data.dto

data class InvidiousSearchResponse(
    val type: String,
    val title: String,
    val videoId: String,
    val author: String,
    val videoThumbnails: List<ThumbnailDto>?,
    val lengthSeconds: Long
)

data class ThumbnailDto(
    val quality: String,
    val url: String,
    val width: Int,
    val height: Int
)

data class InvidiousVideoDetails(
    val title: String,
    val videoId: String,
    val adaptiveFormats: List<AdaptiveFormat>
)

data class AdaptiveFormat(
    val index: String,
    val bitrate: String,
    val type: String,
    val url: String
)
