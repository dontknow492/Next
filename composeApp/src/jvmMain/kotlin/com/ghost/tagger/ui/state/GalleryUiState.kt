package com.ghost.tagger.ui.state

import com.ghost.tagger.data.enums.GalleryMode
import com.ghost.tagger.data.enums.SortBy
import com.ghost.tagger.data.enums.SortOrder
import com.ghost.tagger.data.models.ImageItem
import com.ghost.tagger.data.models.settings.DirectorySettings
import java.io.File

data class GalleryUiState(
    val currentDirectory: File? = null,
    val minThumbnailSizeDp: Float = 240f,
    val images: List<ImageItem> = emptyList(), // All loaded images
    val selectedIds: Set<String> = emptySet(), // Fast lookup for selection
    val focusedImageId: String? = null,        // The image shown in the Sidebar
    val viewMode: GalleryMode = GalleryMode.GRID,
    val isLoading: Boolean = false,
    val statusMessage: String? = null, // e.g. "Loaded 500 images"
    val sortOrder: SortOrder = SortOrder.ASCENDING,
    val sortBy: SortBy = SortBy.NAME,
    val searchQuery: String = "",
    val isSelectionVisible: Boolean = false,
    val taggingId: String? = null,
    val advanceDirSettings: DirectorySettings = DirectorySettings()
)


