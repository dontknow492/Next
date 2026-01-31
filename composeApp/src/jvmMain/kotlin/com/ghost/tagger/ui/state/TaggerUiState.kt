package com.ghost.tagger.ui.state

import com.ghost.tagger.data.models.DownloadStatus
import com.ghost.tagger.data.models.ImageTag

data class TaggerUiState(
    val selectedModelId: String = "wd-vit-large-tagger-v3",
    val isModelLoaded: Boolean = false,

    // Download Status
    val isDownloading: Boolean = false,
    val downloadStatus: DownloadStatus? = null,

    // tagger settings
    val confidenceThreshold: Float = 0.5f,
    val maxTags: Int = 10,
    val excludedTags: List<String> = emptyList(),


    // Tagging Results
    val isTagging: Boolean = false,
    val tags: List<ImageTag> = emptyList(),
    val error: String? = null
)