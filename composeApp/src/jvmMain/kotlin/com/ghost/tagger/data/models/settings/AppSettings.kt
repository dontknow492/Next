package com.ghost.tagger.data.models.settings

import com.ghost.tagger.data.enums.GalleryMode
import kotlinx.serialization.Serializable


@Serializable
data class AppSettings(
    // Global App State
    val lastModelType: ModelType = ModelType.TAGGER,
    val isDarkMode: Boolean = true,
    val windowWidth: Int = 1280,
    val windowHeight: Int = 800,

    // AI Specific Buckets
    val tagger: TaggerSettings = TaggerSettings(),
    val descriptor: DescriptorSettings = DescriptorSettings(),
    val system: SystemSettings = SystemSettings(),

    // Session State
    val session: SessionState = SessionState()

)

@Serializable
enum class ModelType {
    TAGGER, DESCRIPTOR
}

@Serializable
data class TaggerSettings(
    val modelPath: String = "",
    val confidenceThreshold: Float = 0.5f,
    val maxTags: Int = 10,
    val excludedTags: List<String> = emptyList()
) {
}

@Serializable
data class DescriptorSettings(
    val modelPath: String = "",
    val temperature: Float = 0.7f,
    val maxDescriptionLength: Int = 50,
    val useVisualPrompt: Boolean = true
)

@Serializable
data class SystemSettings(
    val useGpu: Boolean = true,
    val parallelProcessing: Boolean = false,
    val autoSaveToExif: Boolean = false,
    val writeXmp: Boolean = true,
)

@Serializable
data class SessionState(
    val lastDirectory: String = System.getProperty("user.home"), // Default to Home
    val lastFocusedImageId: String? = null,
    val activeTab: ModelType = ModelType.TAGGER,
    val isSidebarVisible: Boolean = true,
    val recursiveLoad: Boolean = false,
    val maxRecursionDepth: Int = 2,
    val includeHiddenFiles: Boolean = false,
    val minThumbnailSizeDp: Float = 240f,
    val previewSectionWidthDp: Float = 400f,
    val galleryViewMode: GalleryMode = GalleryMode.GRID,
    val sidePanelWidthDp: Float = 320f

)