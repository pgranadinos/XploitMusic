package com.auramusic.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Tracks downloaded audio files for offline playback.
 * Each row represents a single downloaded (or downloading) track.
 */
@Entity(
    tableName = "downloaded_tracks",
    indices = [Index(value = ["songId"], unique = true)]
)
data class DownloadedTrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    // Track identity
    val songId: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String,
    val durationSeconds: Long,

    // Download state: QUEUED, DOWNLOADING, PAUSED, COMPLETED, FAILED, EXPIRED
    val status: String,
    val progress: Int = 0,            // 0-100
    val filePath: String? = null,     // absolute path to .m4a / .webm
    val fileSizeBytes: Long = 0,

    // Quality & format
    val quality: String = "NORMAL",   // LOW, NORMAL, HIGH
    val format: String = "M4A",
    val bitrate: Int = 128,           // kbps

    // Metadata timestamps
    val downloadedAt: Long? = null,
    val expiresAt: Long? = null,
    val lastAccessedAt: Long? = null, // for LRU eviction
    val retryCount: Int = 0,
    val errorMessage: String? = null,

    // Source tracking
    val addedFromPlaylist: String? = null,
    val workerRequestId: String? = null,

    // Compliance & integrity
    val isOfflineAllowed: Boolean = false,  // enforced from BFF response
    val checksum: String? = null            // SHA-256 of downloaded file
)
