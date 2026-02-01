package com.ghost.tagger.data.models

/**
 * Represents the detailed status of an active download.
 */
data class DownloadStatus(
    val progress: Float,        // 0.0 to 1.0
    val progressPercent: Int,   // 0 to 100
    val speed: String,          // "1.2 MB/s"
    val eta: String,            // "2m 15s"
    val downloadedText: String, // "15 MB / 200 MB"
    val totalBytes: Long?,      // Null if unknown
    val downloadedBytes: Long
)