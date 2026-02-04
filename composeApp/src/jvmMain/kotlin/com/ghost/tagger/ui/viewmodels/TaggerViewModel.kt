package com.ghost.tagger.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.ghost.tagger.data.enums.TagSource
import com.ghost.tagger.core.ModelManager
import com.ghost.tagger.core.downloader.DownloadState
import com.ghost.tagger.core.downloader.FileValidationResult
import com.ghost.tagger.core.onnx.`interface`.HuggingFaceTaggerModel
import com.ghost.tagger.data.models.DownloadStatus
import com.ghost.tagger.data.models.ImageTag
import com.ghost.tagger.data.repository.SettingsRepository
import com.ghost.tagger.ui.state.TaggerUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
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
        val model = ModelManager.taggerModels.value.find { it.repoId == _uiState.value.selectedModelId }
        val validate = model?.getModelFileValidationResult()
        if (validate is FileValidationResult.Valid){
            _uiState.update { it.copy(isModelLoaded = true) }
        }

    }

    fun selectModel(model: HuggingFaceTaggerModel) {
        viewModelScope.launch {
            settingsRepo.updateSettings {
                it.copy(tagger = it.tagger.copy(lastModelId = model.repoId))
            }
            checkModelStatus()
            ModelManager.selectModel(model.repoId)
        }
    }

    fun downloadModel(model: HuggingFaceTaggerModel) {
//        val model = ModelManager.taggerModels.value.find { it.id == _uiState.value.selectedModelId } ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(downloadState = DownloadState.Downloading(
                DownloadStatus(0.5f , 0, "0 B/s", "--", "0 MB / ...", null, 0)
            ), error = null) }

            try {
                ModelManager.downloadModel(model).collect { status ->
                    Logger
                    _uiState.update { it.copy(downloadState = status) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(
                    downloadState = DownloadState.Error("Download failed: ${e.message}"), error = "Download failed: ${e.message}") }
            }
        }
    }

    fun cancelDownload(model: HuggingFaceTaggerModel) {
        ModelManager.cancelDownload(model)
    }

    fun tagImage(file: File) {
        val model = ModelManager.taggerModels.value.find { it.repoId == _uiState.value.selectedModelId } ?: return

        if (!_uiState.value.isModelLoaded) {
            _uiState.update { it.copy(error = "Model not downloaded yet!") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isTagging = true, error = null) }

            try {
                if (ModelManager.activeModel?.repoId != model.repoId) {
                    ModelManager.selectModel(model.repoId)
                }

                val resultTags = ModelManager.activeModel?.predict(file) ?: emptyList()
                _uiState.update { it.copy(tags = resultTags, isTagging = false) }
                Logger.i("Tagging result: ${resultTags.size}, ${resultTags.take(3)}")

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

    fun addExcludedTag(tag: ImageTag) {
        settingsRepo.updateSettings { current ->
            val newList = current.tagger.excludedTags + tag
            current.copy(tagger = current.tagger.copy(excludedTags = newList))
        }
    }
    fun addExcludedTag(tag: String) {
        val tag = ImageTag(name = tag, source = TagSource.MANUAL)
        settingsRepo.updateSettings { current ->
            val newList = current.tagger.excludedTags + tag
            current.copy(tagger = current.tagger.copy(excludedTags = newList))
        }
    }

    fun removeExcludedTag(tag: ImageTag) {
        settingsRepo.updateSettings { current ->
            val newList = current.tagger.excludedTags - tag
            current.copy(tagger = current.tagger.copy(excludedTags = newList))
        }
    }

    fun clearExcludedTags() {
        settingsRepo.updateSettings {
            it.copy(tagger = it.tagger.copy(excludedTags = emptySet()))
        }
    }

    fun openInExplorer(model: HuggingFaceTaggerModel) {
        viewModelScope.launch {
            ModelManager.openInExplorer(model.repoId)
        }
        
    }
}
