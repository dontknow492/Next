package com.ghost.tagger.data.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.ghost.tagger.core.ModelManager
import com.ghost.tagger.core.onnx.`interface`.TaggerModel
import com.ghost.tagger.data.repository.SettingsRepository
import com.ghost.tagger.ui.state.TaggerUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
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
        // Observe Settings
        viewModelScope.launch {
            combine(settingsRepo.settings, ModelManager.taggerModels) { settings, models ->
                _uiState.update {
                    it.copy(
                        models = models,
                        selectedModelId = settings.tagger.lastModelId,
                        confidenceThreshold = settings.tagger.confidenceThreshold,
                        maxTags = settings.tagger.maxTags,
                        excludedTags = settings.tagger.excludedTags
                    )
                }
            }.catch { error ->
                // Handle the exception
                Logger.e("SettingsViewModel", error )
            }.collect()
        }

        // Observe Model List changes (e.g. after init or path change)
        viewModelScope.launch {
            ModelManager.taggerModels.collect {
                checkModelStatus()
            }
        }
}


    private suspend fun checkModelStatus() {
        val model = ModelManager.taggerModels.value.find { it.id == _uiState.value.selectedModelId }
        _uiState.update { it.copy(isModelLoaded = model?.isDownloaded == true) }
    }

    fun selectModel(model: TaggerModel) {
        viewModelScope.launch {
            settingsRepo.updateSettings {
                it.copy(tagger = it.tagger.copy(lastModelId = model.id))
            }
            checkModelStatus()
        }
    }

    fun downloadModel(model: TaggerModel) {
//        val model = ModelManager.taggerModels.value.find { it.id == _uiState.value.selectedModelId } ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isDownloading = true, error = null) }

            try {
                model.download().collect { status ->
                    _uiState.update { it.copy(downloadStatus = status) }

                    if (status.progress == 1.0f) {
                        _uiState.update { it.copy(isDownloading = false, isModelLoaded = true) }
                        ModelManager.selectModel(model.id)
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isDownloading = false, error = "Download failed: ${e.message}") }
            }
        }
    }

    fun tagImage(file: File) {
        val model = ModelManager.taggerModels.value.find { it.id == _uiState.value.selectedModelId } ?: return

        if (!_uiState.value.isModelLoaded) {
            _uiState.update { it.copy(error = "Model not downloaded yet!") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isTagging = true, error = null) }

            try {
                if (ModelManager.activeModel?.id != model.id) {
                    ModelManager.selectModel(model.id)
                }

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
    fun openInExplorer(model: TaggerModel) {
        ModelManager.openInExplorer(model.id)
    }
}
