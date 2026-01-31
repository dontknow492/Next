package com.ghost.tagger.data.models

data class ImageItem(
    val id: String,
    val name: String,
    val isTagging: Boolean = false,
    val isFocused: Boolean = false, // The "Active" image for the sidebar
    val metadata: ImageMetadata
)

