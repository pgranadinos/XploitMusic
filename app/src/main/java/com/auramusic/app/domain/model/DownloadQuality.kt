package com.auramusic.app.domain.model

/**
 * Audio quality for downloads. Maps to approximate bitrate targets
 * when selecting from available Piped audio streams.
 */
enum class DownloadQuality(val targetBitrate: Int, val label: String) {
    LOW(64, "Baja (64 kbps)"),
    NORMAL(128, "Normal (128 kbps)"),
    HIGH(256, "Alta (256 kbps)");
}
