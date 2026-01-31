package com.ghost.tagger.data.models

data class DownloadStatus(
    val progress: Float,        // 0.0 to 1.0 (for Progress Bar)
    val progressPercent: Int,   // 0 to 100 (for Text)
    val speed: String,          // "1.2 MB/s"
    val eta: String,            // "2m 15s"
    val downloadedText: String, // "15 MB / 200 MB" (Pre-formatted)

    // NEW RAW FIELDS
    val totalBytes: Long?,      // Null if server doesn't send Content-Length
    val downloadedBytes: Long   // The actual bytes read so far
)