package com.ghost.tagger.data.models

import java.io.File

data class ImageMetadata(
    val path: File,
    val name: String,
    val extension: String,
    val fileSizeBytes: Long,
    val lastModified: Long,
    val width: Int = 0,
    val height: Int = 0,
    val dateTaken: Long? = null, // From EXIF
    val description: String? = null,
    val tags: List<ImageTag> = emptyList() // From XMP/IPTC
) {
    // Helper to show readable size (e.g., "5.2 MB")
    fun readableSize(): String {
        val kb = fileSizeBytes / 1024.0
        return if (kb > 1024) String.format("%.2f MB", kb / 1024.0) else String.format("%.0f KB", kb)
    }

    // Helper for resolution
    fun resolution(): String = if (width > 0) "$width x $height" else "Unknown"
}