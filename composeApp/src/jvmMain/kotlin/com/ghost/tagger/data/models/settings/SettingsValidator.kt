package com.ghost.tagger.data.models.settings

import com.ghost.tagger.core.ModelManager
import com.ghost.tagger.ui.components.ModelSelector
import java.io.File

object SettingsValidator {

    fun validateAll(settings: AppSettings): AppSettings {
        return settings.copy(
            // 1. Global Window & UI State
            windowWidth = settings.windowWidth.coerceIn(800, 7680), // Min: 800px, Max: 8K
            windowHeight = settings.windowHeight.coerceIn(600, 4320),

            // 2. Nested Buckets
            tagger = validateTagger(settings.tagger),
            descriptor = validateDescriptor(settings.descriptor),
            system = validateSystem(settings.system)
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
            maxTags = settings.maxTags.coerceIn(1, 100),
            excludedTags = settings.excludedTags
                .map { it.trim().lowercase() } // Normalize tags for easier comparison
                .filter { it.isNotEmpty() }
                .distinct() // Remove duplicates
        )
    }

    // 2. Descriptor Validation (Generative Creativity)
    fun validateDescriptor(settings: DescriptorSettings): DescriptorSettings {
        return settings.copy(
            modelPath = settings.modelPath.trim(),
            temperature = settings.temperature.coerceIn(0.1f, 2.0f),
            maxDescriptionLength = settings.maxDescriptionLength.coerceIn(5, 1000)
        )
    }

    // 3. System Settings (Boolean logic)
    fun validateSystem(settings: SystemSettings): SystemSettings {
        // Most system settings are Booleans (True/False), which are valid by nature.
        // But we return a copy for consistency in our architecture.
        return settings.copy(
            useGpu = settings.useGpu,
            parallelProcessing = settings.parallelProcessing,
            autoSaveToExif = settings.autoSaveToExif
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