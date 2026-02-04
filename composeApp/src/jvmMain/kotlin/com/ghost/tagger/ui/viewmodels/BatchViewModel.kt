package com.ghost.tagger.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.ghost.tagger.core.ModelManager
import com.ghost.tagger.data.enums.TagSource
import com.ghost.tagger.data.models.ImageItem
import com.ghost.tagger.data.models.ImageTag
import com.ghost.tagger.data.repository.ImageRepository
import com.ghost.tagger.data.repository.SettingsRepository
import com.ghost.tagger.ui.state.BatchDetailUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class BatchDetailViewModel(
    private val modelManager: ModelManager,
    private val imageRepository: ImageRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BatchDetailUiState())
    val uiState = _uiState.asStateFlow()

    // The current selection of IDs we are observing
    private val selectedIds = MutableStateFlow<Set<String>>(emptySet())

    init {
        observeBatchImages()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeBatchImages() {
        viewModelScope.launch {
            selectedIds
                .flatMapLatest { ids ->
                    if (ids.isEmpty()) {
                        flowOf(emptyList())
                    } else {
                        // Use the flow-based observer from your repository
                        imageRepository.observeImagesByIds(ids)
                    }
                }
                .collect { updatedList ->
                    _uiState.update { state ->
                        state.copy(
                            selectedImages = updatedList,
                            // Total progress should match the size of what we're looking at
                            progressTotal = updatedList.size
                        )
                    }
                }
        }
    }

    /**
     * Entry Point: Pass the IDs from the gallery selection.
     */
    fun selectImages(imagesIds: Set<String>) {

        // Reset state and update the IDs to trigger the flow
        _uiState.update {
            it.copy(
                isGenerating = false,
                progressCurrent = 0
            )
        }
        selectedIds.value = imagesIds
    }

    /**
     * Adds a specific tag to ALL selected images.
     * Logic: If an image already has the tag, it is skipped. If not, it is added.
     */
    fun addTagToBatch(tagText: String) {
        val cleanTag = tagText.trim()
        if (cleanTag.isEmpty()) return

        updateImages(disk = false) { currentImage, _ ->
            // Check if tag exists (case-insensitive)
            if (currentImage.metadata.tags.any { it.name.equals(cleanTag, ignoreCase = true) }) {
                currentImage // No change
            } else {
                val newTag =
                    ImageTag(cleanTag, confidence = 1.0, source = TagSource.MANUAL) // Manual tags get max confidence
                val newTags = currentImage.metadata.tags + newTag
                currentImage.copy(metadata = currentImage.metadata.copy(tags = newTags))
            }
        }
    }

    /**
     * Removes a specific tag from ALL selected images.
     */
    fun removeTagFromBatch(tagToRemove: ImageTag) {
        updateImages(disk = false) { currentImage, _ ->
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
        updateImages(disk = false) { currentImage, _ ->
            currentImage.copy(metadata = currentImage.metadata.copy(tags = emptyList()))
        }
    }

    /**
     * Runs the active AI model on every selected image sequentially.
     * Updates the UI progressively as each image finishes.
     */
    fun generateTagsForBatch() {
        val images = _uiState.value.selectedImages
        val currentSetting = settingsRepository.settings.value
        val confidenceThreshold = currentSetting.tagger.confidenceThreshold
        val maxTags = currentSetting.tagger.maxTags
        val batchSize = currentSetting.system.batchSize.coerceIn(2, 32)

        // Optimization: Convert exclusions to HashSet for O(1) lookups
        val excludedTagNames = currentSetting.tagger.excludedTags
            .map { it.name.lowercase() }
            .toHashSet()


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
            try {
                updateImages { currentImage, _ ->
                    currentImage.copy(isTagging = true)
                }
                val finalTagList = withContext(Dispatchers.IO) {
                    val files = images.map { it.metadata.path }
                    val aiPredictions = model.predictBatch(files, confidenceThreshold.toDouble(), batchSize)

                    val existingTags = images.map { it.metadata.tags }
                    val allCandidateTags = existingTags + aiPredictions

                    val processedTags = allCandidateTags.map { item ->
                        item.filter { it.name.lowercase() !in excludedTagNames }
                            .groupBy { it.name.lowercase() }
                            .map { (_, tagGroup) ->
                                // Pick the "best" tag in this group
                                tagGroup.sortedWith(
                                    compareByDescending<ImageTag> { it.source.priority() }
                                        .thenByDescending { it.confidence }
                                ).first()

                            }
                    }

                    processedTags.map { item ->
                        item.sortedWith(
                            compareByDescending<ImageTag> { it.source.priority() }
                                .thenByDescending { it.confidence }
                        ).take(maxTags)
                    }
                }
                Logger.d("Tag generated successfully for images: ${finalTagList}")

                updateImages(disk = true) { currentImage, index ->
                    currentImage.copy(
                        isTagging = false,
                        metadata = currentImage.metadata.copy(tags = finalTagList[index])
                    )
                }

                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        progressCurrent = currentImages.size,
                        progressTotal = currentImages.size
                    )
                }


            } catch (e: Exception) {
                e.printStackTrace()
                onError(e)
                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        progressCurrent = 0,
                        progressTotal = 0
                    )
                }
                // We continue to the next image even if one fails
            }
        }

        _uiState.update { it.copy(isGenerating = false) }

    }


    /**
     * Helper: Applies a transformation function to every image in the selection
     * and updates the StateFlow.
     */
    private inline fun updateImages(disk: Boolean = false, crossinline transform: (ImageItem, Int) -> ImageItem) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isSaving = true) }
            val newImages = uiState.value.selectedImages.mapIndexed { index, image ->
                transform(image.copy(metadata = image.metadata.copy(lastModified = System.currentTimeMillis())), index)
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