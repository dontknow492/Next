package com.ghost.tagger.ui.viewmodels


import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.ghost.tagger.core.ModelManager
import com.ghost.tagger.data.models.ImageItem
import com.ghost.tagger.data.models.ImageTag
import com.ghost.tagger.data.repository.ImageRepository
import com.ghost.tagger.ui.actions.DetailAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
) : ViewModel() {

    private val _uiState = MutableStateFlow<ImagePreviewUiState>(ImagePreviewUiState())
    val uiState = _uiState.asStateFlow()

    private var autoSaveJob: Job? = null


    /**
     * Call this when a user clicks an image in the gallery
     */
    fun selectImage(image: ImageItem) {
        _uiState.update { it.copy(activeImage = image, isPanelVisible = true) }
//        _activeImage.value = image
//        _isPanelVisible.value = true
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
        val model = modelManager.activeModel
        if (model == null) {
            println("❌ No model selected for generation")
            // Ideally emit a side-effect/event to show a Snackbar here
            return
        }

        viewModelScope.launch {
            // 1. Set Loading State
            _uiState.update {
                it.copy(activeImage = it.activeImage?.copy(isTagging = true))
            }

            try {
                // 2. Run Prediction (Heavy lifting done on IO/Default dispatcher inside model)
                val file = image.metadata.path
                val generatedTags = model.predict(file)

                // 3. Merge or Replace tags?
                // Strategy: Keep existing manual tags, append new AI tags that aren't duplicates
                val currentTagNames = image.metadata.tags.map { it.name.lowercase() }.toSet()
                val newTags = generatedTags.filter { it.name.lowercase() !in currentTagNames }
                val combinedTags = image.metadata.tags + newTags

                // 4. Update State
                _uiState.update {
                    it.copy(
                        activeImage = it.activeImage?.copy(
                            isTagging = false,
                            metadata = it.activeImage.metadata.copy(tags = combinedTags),
                        ),
                        dataModified = true,
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update {
                    it.copy(activeImage = it.activeImage?.copy(isTagging = false))
                }
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
                e.printStackTrace()
            }
        }
    }

    private fun saveChanges(image: ImageItem, disk: Boolean = true) {
        Logger.i("Saving metadata for ${image.name}...")
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isSaving = true) }
            imageRepository.updateImage(image, disk = disk, onError = ::onError)
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