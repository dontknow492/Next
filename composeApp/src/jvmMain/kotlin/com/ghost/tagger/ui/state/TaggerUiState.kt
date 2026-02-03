package com.ghost.tagger.ui.state

import com.ghost.tagger.core.downloader.DownloadState
import com.ghost.tagger.core.onnx.`interface`.HuggingFaceTaggerModel
import com.ghost.tagger.data.models.ImageTag

data class TaggerUiState(
    val selectedModelId: String = "wd-vit-large-tagger-v3",
    val models: List<HuggingFaceTaggerModel> = emptyList(),
    val isModelLoaded: Boolean = false,

    // Download Status
    val downloadState: DownloadState = DownloadState.Idle,

    // tagger settings
    val confidenceThreshold: Float = 0.5f,
    val maxTags: Int = 10,
    val excludedTags: Set<ImageTag> = emptySet(),


    // Tagging Results
    val isTagging: Boolean = false,
    val tags: List<ImageTag> = emptyList(),
    val error: String? = null
)