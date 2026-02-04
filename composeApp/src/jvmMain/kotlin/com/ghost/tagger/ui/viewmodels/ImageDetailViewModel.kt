package com.ghost.tagger.ui.viewmodels


import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.ghost.tagger.core.ModelManager
import com.ghost.tagger.data.models.ImageItem
import com.ghost.tagger.data.models.ImageTag
import com.ghost.tagger.data.repository.ImageRepository
import com.ghost.tagger.data.repository.SettingsRepository
import com.ghost.tagger.ui.actions.DetailAction
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.awt.Desktop
import java.io.File

data class ImagePreviewUiState(
    val activeImage: ImageItem? = null,
    val isPanelVisible: Boolean = true,
    val isSaving: Boolean = false,
    val isGenerating: Boolean = false,
    val dataModified: Boolean = false,
    val error: String? = null,
)

class ImageDetailViewModel(
    private val modelManager: ModelManager,
    private val imageRepository: ImageRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ImagePreviewUiState>(ImagePreviewUiState())
    val uiState = _uiState.asStateFlow()

    private var autoSaveJob: Job? = null

    // Track the currently selected ID
    private val selectedImageId = MutableStateFlow<String?>(null)

    init {
        observeActiveImage()
    }


    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeActiveImage() {
        viewModelScope.launch {
            selectedImageId
                .flatMapLatest { id ->
                    if (id == null) {
                        flowOf(null)
                    } else {
                        // Observe the specific image from your repository
                        imageRepository.observeImageById(id)
                    }
                }
                .collect { image ->
                    _uiState.update { state ->
                        state.copy(
                            activeImage = image,
                            // If the repo is empty or image is missing,
                            // we ensure the UI knows there's nothing to show
                            isPanelVisible = image != null
                        )
                    }
                }
        }
    }

    /**
     * Call this when a user clicks an image in the gallery
     */
    fun selectImage(id: String) {
        selectedImageId.value = id
    }

    // Optional: clear selection
    fun clearSelection() {
        selectedImageId.value = null
    }


    private fun triggerDebouncedSave() {
        autoSaveJob?.cancel()

        autoSaveJob = viewModelScope.launch {
            delay(800)

            val image = _uiState.value.activeImage
                ?: return@launch

            saveChanges(image, disk = false)
        }
    }

    /**
     * Main entry point for actions coming from the UI
     */
    fun handleAction(action: DetailAction) {
        val currentImage = _uiState.value.activeImage ?: return

        when (action) {
            is DetailAction.UpdateDescription -> updateDescription(currentImage, action.value)
            is DetailAction.AddTag -> addTag(currentImage, action.tag)
            is DetailAction.RemoveTag -> removeTag(currentImage, action.tag)
            is DetailAction.ClearTags -> clearTags(currentImage)
            is DetailAction.GenerateTags -> generateAiTags(currentImage)
            is DetailAction.OpenInExplorer -> openInExplorer(currentImage.metadata.path)
            is DetailAction.ClosePreview -> setPanelVisible(false)
            is DetailAction.SaveMetadata -> saveChanges(currentImage, true)
        }
    }

    // -------------------------------------------------------------------------
    // Logic Handlers
    // -------------------------------------------------------------------------

    private fun setPanelVisible(visible: Boolean) {
        _uiState.update { it.copy(isPanelVisible = visible) }
    }

    private fun updateDescription(image: ImageItem, newDesc: String) {
        val updatedImage = image.copy(
            metadata = image.metadata.copy(description = newDesc)
        )

        _uiState.update {
            it.copy(
                activeImage = updatedImage,
                dataModified = true
            )
        }

        triggerDebouncedSave()
    }


    private fun addTag(image: ImageItem, tagText: String) {
        val cleanTag = tagText.trim()
        if (cleanTag.isBlank()) return

        if (image.metadata.tags.any {
                it.name.equals(cleanTag, ignoreCase = true)
            }) return

        val newTag = ImageTag(cleanTag, confidence = 1.0)

        val updatedImage = image.copy(
            metadata = image.metadata.copy(
                tags = image.metadata.tags + newTag
            )
        )

        _uiState.update {
            it.copy(
                activeImage = updatedImage,
                dataModified = true
            )
        }

        triggerDebouncedSave()
    }


    private fun removeTag(image: ImageItem, tagToRemove: ImageTag) {
        val updatedImage = image.copy(
            metadata = image.metadata.copy(
                tags = image.metadata.tags.filterNot { it == tagToRemove }
            )
        )

        _uiState.update {
            it.copy(
                activeImage = updatedImage,
                dataModified = true
            )
        }

        triggerDebouncedSave()
    }


    private fun clearTags(image: ImageItem) {
        val updatedImage = image.copy(
            metadata = image.metadata.copy(tags = emptyList())
        )

        _uiState.update {
            it.copy(
                activeImage = updatedImage,
                dataModified = true
            )
        }

        triggerDebouncedSave()
    }


    private fun generateAiTags(image: ImageItem) {
        // 1. Snapshot settings immediately (Fast, Main Thread)
        val currentSetting = settingsRepository.settings.value
        val confidenceThreshold = currentSetting.tagger.confidenceThreshold
        val maxTags = currentSetting.tagger.maxTags

        // Optimization: Convert exclusions to HashSet for O(1) lookups
        val excludedTagNames = currentSetting.tagger.excludedTags
            .map { it.name.lowercase() }
            .toHashSet()

        val model = modelManager.activeModel // Consider emitting a UI event (e.g. Snackbar) here
        if (model == null) {
            onError(Exception("No model loaded"))
            return
        }

        viewModelScope.launch {
            // 2. Set Loading State
            saveChanges(image.copy(isTagging = false), disk = false)
            _uiState.update { it.copy(isGenerating = true) }

            try {
                // 3. Move Heavy Computation to Background (Default Dispatcher)
                val finalTagList = withContext(Dispatchers.Default) {
                    val file = image.metadata.path

                    // Heavy Inference Step: Predict tags using AI
                    val aiPredictions = model.predict(file, confidenceThreshold.toDouble())

                    // Combine existing tags with AI predictions
                    val existingTags = image.metadata.tags
                    val allCandidateTags = existingTags + aiPredictions

                    // Logic:
                    // 1. Filter out excluded tags
                    // 2. Group by name to handle duplicates
                    // 3. For each name, keep the best version (highest priority, then highest confidence)
                    val processedTags = allCandidateTags
                        .filter { it.name.lowercase() !in excludedTagNames }
                        .groupBy { it.name.lowercase() }
                        .map { (_, tagGroup) ->
                            // Pick the "best" tag in this group
                            tagGroup.sortedWith(
                                compareByDescending<ImageTag> { it.source.priority() }
                                    .thenByDescending { it.confidence }
                            ).first()
                        }

                    // 4. Final Sorting and Limiting
                    // Sort the entire list so that the most important tags stay within maxTags limit
                    processedTags
                        .sortedWith(
                            compareByDescending<ImageTag> { it.source.priority() }
                                .thenByDescending { it.confidence }
                        )
                        .take(maxTags)
                }

                Logger.d("Tag generated successfully: $finalTagList")

                // 5. Save & Update State (Back on Main Thread)
                saveChanges(
                    image.copy(
                        metadata = image.metadata.copy(tags = finalTagList),
                        isTagging = false,
                    ), disk = false
                )

                _uiState.update {
                    it.copy(
                        dataModified = true,
                        // Explicitly turn off loading here to ensure UI reflects completion
                        activeImage = it.activeImage?.copy(isTagging = false)
                    )
                }

            } catch (e: CancellationException) {
                onError(e)
            } catch (e: Exception) {
                e.printStackTrace()
                // Error State Reset
                saveChanges(image.copy(isTagging = false), disk = false)
                _uiState.update { it.copy(isGenerating = false) }
                onError(e)
            }
        }
    }


    private fun openInExplorer(path: File) {
        viewModelScope.launch(Dispatchers.IO) {
            try {

                if (path.exists() && Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().browseFileDirectory(path)
                }
            } catch (e: Exception) {
                Logger.e("Error opening in explorer: ${e.message}")
                onError(e)
            }
        }
    }

    private fun saveChanges(image: ImageItem, disk: Boolean = true) {
        Logger.i("Saving metadata for ${image.name}...")
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isSaving = true) }
            imageRepository.updateImage(
                image.copy(metadata = image.metadata.copy(lastModified = System.currentTimeMillis())),
                disk = disk,
                onError = ::onError
            )
            _uiState.update { it.copy(isSaving = false, dataModified = !disk) }
        }
    }


    private fun onError(e: Exception) {
        _uiState.update { it.copy(error = e.message) }
    }

    fun onClearError() {
        _uiState.update { it.copy(error = null) }
    }
}