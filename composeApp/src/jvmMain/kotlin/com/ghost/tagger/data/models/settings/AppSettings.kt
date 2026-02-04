package com.ghost.tagger.data.models.settings

import com.ghost.tagger.core.FileSerializer
import com.ghost.tagger.core.ModelManager
import com.ghost.tagger.data.enums.GalleryMode
import com.ghost.tagger.data.enums.ThemeMode
import com.ghost.tagger.data.models.ImageTag
import kotlinx.serialization.Serializable
import java.io.File


@Serializable
data class AppSettings(
    // Global App State
    @Serializable(with = FileSerializer::class)
    val modelDownloadPath: File = File(System.getProperty("user.home"), ".ghosttagger/models"),
    val lastModelType: ModelType = ModelType.TAGGER,
    val themeMode: ThemeMode = ThemeMode.AUTO,
    val isDarkMode: Boolean = true,
    val windowWidth: Int = 1280,
    val windowHeight: Int = 800,

    // AI Specific Buckets
    val tagger: TaggerSettings = TaggerSettings(),
    val descriptor: DescriptorSettings = DescriptorSettings(),
    val system: SystemSettings = SystemSettings(),

    // Session State
    val session: SessionState = SessionState(),

    // api key
    val apiKey: String? = null

)

@Serializable
enum class ModelType {
    TAGGER, DESCRIPTOR
}

@Serializable
data class TaggerSettings(
    val lastModelId: String = ModelManager.getDefaultTaggerModelId(),
    val confidenceThreshold: Float = 0.5f,
    val maxTags: Int = 10,
    val excludedTags: Set<ImageTag> = emptySet()
)

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
    val batchSize: Int = 8,
)

@Serializable
data class SessionState(
    @Serializable(with = FileSerializer::class)
    val lastDirectory: File? = null, // Default to Home
    val lastFocusedImageId: String? = null,
    val activeTab: ModelType = ModelType.TAGGER,
    val isSidebarVisible: Boolean = false,
    val recursiveLoad: Boolean = false,
    val maxRecursionDepth: Int = 2,
    val includeHiddenFiles: Boolean = false,
    val minThumbnailSizeDp: Float = 240f,
    val previewSectionWidthDp: Float = 400f,
    val galleryViewMode: GalleryMode = GalleryMode.GRID,
    val sidePanelWidthDp: Float = 320f

)