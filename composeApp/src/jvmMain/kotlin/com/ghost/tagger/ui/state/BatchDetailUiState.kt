package com.ghost.tagger.ui.state

import com.ghost.tagger.data.models.ImageItem
import com.ghost.tagger.data.models.ImageTag

data class BatchDetailUiState(
    val selectedImages: List<ImageItem> = emptyList(),
    val isGenerating: Boolean = false,
    val progressCurrent: Int = 0,
    val progressTotal: Int = 0
){
    val totalSize: String
        get() {
            val totalBytes = selectedImages.sumOf { it.metadata.fileSizeBytes }
            val mb = totalBytes / (1024.0 * 1024.0)
            return if (mb > 1024) String.format("%.2f GB", mb / 1024.0) else String.format("%.1f MB", mb)
        }

    val count: Int get() = selectedImages.size

    // Tags present in ALL selected images
    val commonTags: List<ImageTag>
        get() {
            if (selectedImages.isEmpty()) return emptyList()
            val firstTags = selectedImages.first().metadata.tags.toSet()
            // Intersect with all other images
            return selectedImages.drop(1).fold(firstTags) { acc, img ->
                acc.intersect(img.metadata.tags.toSet())
            }.toList().sortedBy { it.name }
        }

    // Tags present in SOME but not ALL
    val mixedTags: List<ImageTag>
        get() {
            if (selectedImages.isEmpty()) return emptyList()
            val allTags = selectedImages.flatMap { it.metadata.tags }.toSet()
            // Use the instance’s commonTags property
            val common = this.commonTags.toSet()   // or just `commonTags.toSet()`
            return (allTags - common).toList().sortedBy { it.name }
        }
}