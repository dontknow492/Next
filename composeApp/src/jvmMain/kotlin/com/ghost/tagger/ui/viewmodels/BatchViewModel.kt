package com.ghost.tagger.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghost.tagger.TagSource
import com.ghost.tagger.core.ModelManager
import com.ghost.tagger.data.models.ImageItem
import com.ghost.tagger.data.models.ImageTag
import com.ghost.tagger.data.repository.ImageRepository
import com.ghost.tagger.ui.state.BatchDetailUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

class BatchDetailViewModel(
    private val modelManager: ModelManager,
    private val imageRepository: ImageRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BatchDetailUiState())
    val uiState = _uiState.asStateFlow()


    /**
     * Main Entry Point: Call this when the user's selection changes in the parent view.
     * This resets any ongoing generation state and updates the panel.
     */
    fun selectImages(images: List<ImageItem>) {
        _uiState.update {
            it.copy(
                selectedImages = images,
                // Reset progress if selection changes entirely
                isGenerating = false,
                progressCurrent = 0,
                progressTotal = 0
            )
        }
    }

    /**
     * Adds a specific tag to ALL selected images.
     * Logic: If an image already has the tag, it is skipped. If not, it is added.
     */
    fun addTagToBatch(tagText: String) {
        val cleanTag = tagText.trim()
        if (cleanTag.isEmpty()) return

        updateImages { currentImage ->
            // Check if tag exists (case-insensitive)
            if (currentImage.metadata.tags.any { it.name.equals(cleanTag, ignoreCase = true) }) {
                currentImage // No change
            } else {
                val newTag = ImageTag(cleanTag, confidence = 1.0, source = TagSource.MANUAL) // Manual tags get max confidence
                val newTags = currentImage.metadata.tags + newTag
                currentImage.copy(metadata = currentImage.metadata.copy(tags = newTags))
            }
        }
    }

    /**
     * Removes a specific tag from ALL selected images.
     */
    fun removeTagFromBatch(tagToRemove: ImageTag) {
        updateImages { currentImage ->
            val newTags = currentImage.metadata.tags.filterNot { it.name == tagToRemove.name }
            // Only copy object if changes actually happened
            if (newTags.size != currentImage.metadata.tags.size) {
                currentImage.copy(metadata = currentImage.metadata.copy(tags = newTags))
            } else {
                currentImage
            }
        }
    }

    /**
     * Wipes all tags from ALL selected images.
     */
    fun clearTagsBatch() {
        updateImages { currentImage ->
            currentImage.copy(metadata = currentImage.metadata.copy(tags = emptyList()))
        }
    }

    /**
     * Runs the active AI model on every selected image sequentially.
     * Updates the UI progressively as each image finishes.
     */
    fun generateTagsForBatch() {
        val model = modelManager.activeModel
        if (model == null) {
            println("❌ Batch Generation: No model selected.")
            return
        }

        val currentImages = _uiState.value.selectedImages
        if (currentImages.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update {
                it.copy(
                    isGenerating = true,
                    progressCurrent = 0,
                    progressTotal = currentImages.size
                )
            }

            // Work on a mutable copy of the list so we can update indices as we go
            val updatedList = currentImages.toMutableList()

            updatedList.forEachIndexed { index, image ->
                try {
                    val file = image.metadata.path

                    // Only process if file exists
                    if (file.exists()) {
                        val generatedTags = model.predict(file)

                        // Strategy: Merge new AI tags with existing manual tags
                        // 1. Identify existing tag names
                        val currentTagNames = image.metadata.tags.map { it.name.lowercase() }.toSet()
                        // 2. Filter out duplicates from AI results
                        val uniqueNewTags = generatedTags.filter { it.name.lowercase() !in currentTagNames }
                        // 3. Combine
                        val combinedTags = image.metadata.tags + uniqueNewTags

                        // 4. Update the image object
                        val updatedImage = image.copy(
                            metadata = image.metadata.copy(tags = combinedTags)
                        )
                        updatedList[index] = updatedImage

                        // 5. Update State incrementally to show progress to user
                        _uiState.update { state ->
                            state.copy(
                                selectedImages = updatedList.toList(), // Force new list reference
                                progressCurrent = index + 1
                            )
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    // We continue to the next image even if one fails
                }
            }

            _uiState.update { it.copy(isGenerating = false) }

            // TODO: Trigger a save-to-disk side effect here via a Repository
        }
    }

    /**
     * Helper: Applies a transformation function to every image in the selection
     * and updates the StateFlow.
     */
    private inline fun updateImages(disk: Boolean = false, crossinline transform: (ImageItem) -> ImageItem) {
//        viewModelScope.launch(Dispatchers.IO) {
//            imageRepository.updateImages(
//                _uiState.value.selectedImages.map(transform)
//            )
//        }
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isSaving = true) }
            val newImages = uiState.value.selectedImages.map { image ->
                transform(image)
            }
            imageRepository.updateImages(newImages, disk = disk, onError = ::onError)
            _uiState.update { it.copy(isSaving = false) }
        }
    }

    private fun onError(e: Exception) {
        _uiState.update { it.copy(error = e.message) }
    }

    fun onClearError() {
        _uiState.update { it.copy(error = null) }
    }


}