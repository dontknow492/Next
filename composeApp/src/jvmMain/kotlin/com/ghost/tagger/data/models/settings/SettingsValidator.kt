package com.ghost.tagger.data.models.settings

import java.io.File

object SettingsValidator {

    fun validateAll(settings: AppSettings): AppSettings {
        return settings.copy(
            // 1. Global Window & UI State
            windowWidth = settings.windowWidth.coerceIn(800, 7680), // Min: 800px, Max: 8K
            windowHeight = settings.windowHeight.coerceIn(600, 4320),

            // Validate API Key (Trim whitespace, null if blank)
            apiKey = settings.apiKey?.trim()?.ifBlank { null },

            // 2. Nested Buckets
            tagger = validateTagger(settings.tagger),
            descriptor = validateDescriptor(settings.descriptor),
            system = validateSystem(settings.system),
            session = validateSession(settings.session)
        )
    }

    // 1. Tagger Validation (Probability & List based)
    fun validateTagger(settings: TaggerSettings): TaggerSettings {
        return settings.copy(
            // If path isn't empty but file is missing, we keep the path but
            // the UI should handle the warning. We don't clear it automatically
            // so the user can see what they typed.
//            modelPath = settings.modelPath.trim(),
//            lastModelId = ModelManager.taggerModels.find {
//                it.id == settings.lastModelId
//            }?.id ?: ModelManager.taggerModels.first().id,

            confidenceThreshold = settings.confidenceThreshold.coerceIn(0.0f, 1.0f),
            maxTags = settings.maxTags.coerceIn(1, 200),
            excludedTags = settings.excludedTags
                .map { tag -> tag.copy(name = tag.name.trim().lowercase()) }
                .filter { tag -> tag.name.isNotBlank() }
                .distinctBy { tag -> tag.name }
                .toSet()
        )
    }

    // 2. Descriptor Validation (Generative Creativity)
    fun validateDescriptor(settings: DescriptorSettings): DescriptorSettings {
        return settings.copy(
            modelPath = settings.modelPath.trim(),
            temperature = settings.temperature.coerceIn(0.1f, 2.0f),
            maxDescriptionLength = settings.maxDescriptionLength.coerceIn(5, 1000),
            useVisualPrompt = settings.useVisualPrompt
        )
    }

    // 3. System Settings (Boolean logic & Performance)
    fun validateSystem(settings: SystemSettings): SystemSettings {
        return settings.copy(
            useGpu = settings.useGpu,
            parallelProcessing = settings.parallelProcessing,
            autoSaveToExif = settings.autoSaveToExif,
            writeXmp = settings.writeXmp,
            // Batch size 1 is min, 128 is a reasonable upper safety limit for VRAM
            batchSize = settings.batchSize.coerceIn(1, 128)
        )
    }

    // 4. Session State (UI Dimensions & limits)
    fun validateSession(settings: SessionState): SessionState {
        return settings.copy(
            // Recursion shouldn't go too deep to prevent stack/memory issues
            maxRecursionDepth = settings.maxRecursionDepth.coerceIn(0, 10),

            // UI sizing constraints
            minThumbnailSizeDp = settings.minThumbnailSizeDp.coerceIn(60f, 600f),
            previewSectionWidthDp = settings.previewSectionWidthDp.coerceAtLeast(200f),
            sidePanelWidthDp = settings.sidePanelWidthDp.coerceIn(200f, 800f),

            // Pass through logic for state fields
            lastDirectory = settings.lastDirectory,
            lastFocusedImageId = settings.lastFocusedImageId,
            activeTab = settings.activeTab,
            isSidebarVisible = settings.isSidebarVisible,
            recursiveLoad = settings.recursiveLoad,
            includeHiddenFiles = settings.includeHiddenFiles,
            galleryViewMode = settings.galleryViewMode
        )
    }

    /**
     * A helper for the UI to show a red warning icon if the path is invalid.
     */
    fun isValidModelPath(path: String): Boolean {
        if (path.isBlank()) return false
        val file = File(path)
        return file.exists() && file.isFile && file.extension.lowercase() == "onnx"
    }
}