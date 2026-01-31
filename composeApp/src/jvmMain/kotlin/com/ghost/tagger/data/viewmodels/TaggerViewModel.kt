package com.ghost.tagger.data.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghost.tagger.core.ModelManager
import com.ghost.tagger.data.repository.SettingsRepository
import com.ghost.tagger.ui.state.TaggerUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

class TaggerViewModel(
    private val settingsRepo: SettingsRepository
): ViewModel() {
    // Expose state to Compose
    private val _uiState = MutableStateFlow(TaggerUiState())
    val uiState: StateFlow<TaggerUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepo.settings.collect { settings ->
                _uiState.update {
                    it.copy(
                        confidenceThreshold = settings.tagger.confidenceThreshold,
                        maxTags = settings.tagger.maxTags,
                        excludedTags = settings.tagger.excludedTags
                    )
                }
            }
        }
        // Initialize with the default model status
        viewModelScope.launch {
            checkModelStatus()
        }
    }

    private suspend fun checkModelStatus() {
        val model = ModelManager.taggerModels.find { it.id == _uiState.value.selectedModelId }
        _uiState.update { it.copy(isModelLoaded = model?.isDownloaded == true) }
    }

    fun selectModel(modelId: String) {
        _uiState.update { it.copy(selectedModelId = modelId) }
        viewModelScope.launch { checkModelStatus() }
    }

    fun downloadModel() {
        val model = ModelManager.taggerModels.find { it.id == _uiState.value.selectedModelId } ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isDownloading = true, error = null) }

            try {
                // Collect the Flow from your TaggerModel
                model.download().collect { status ->
                    _uiState.update { it.copy(downloadStatus = status) }

                    if (status.progress == 1.0f) {
                         // Download Complete!
                        _uiState.update { it.copy(isDownloading = false, isModelLoaded = true) }
                        // Pre-load into RAM immediately so it's ready
                        ModelManager.selectModel(model.id)
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isDownloading = false, error = "Download failed: ${e.message}") }
            }
        }
    }

    fun tagImage(file: File) {
        val model = ModelManager.taggerModels.find { it.id == _uiState.value.selectedModelId } ?: return

        if (!_uiState.value.isModelLoaded) {
            _uiState.update { it.copy(error = "Model not downloaded yet!") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isTagging = true, error = null) }

            try {
                // Ensure it's the active one in RAM
                if (ModelManager.activeModel?.id != model.id) {
                    ModelManager.selectModel(model.id)
                }

                // Run the prediction on background thread
                val resultTags = ModelManager.activeModel?.predict(file) ?: emptyList()

                _uiState.update { it.copy(tags = resultTags, isTagging = false) }

            } catch (e: Exception) {
                _uiState.update { it.copy(isTagging = false, error = "Tagging failed: ${e.message}") }
                e.printStackTrace()
            }
        }
    }

    fun setConfidenceThreshold(value: Float) {
        settingsRepo.updateSettings {
            it.copy(tagger = it.tagger.copy(confidenceThreshold = value))
        }
    }

    fun setMaxTags(value: Int) {
        settingsRepo.updateSettings {
            it.copy(tagger = it.tagger.copy(maxTags = value))
        }
    }

    fun setExcludedTags(value: List<String>) {
        settingsRepo.updateSettings {
            it.copy(tagger = it.tagger.copy(excludedTags = value))
        }
    }
}
