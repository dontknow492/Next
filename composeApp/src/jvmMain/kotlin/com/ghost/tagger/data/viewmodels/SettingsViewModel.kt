package com.ghost.tagger.data.viewmodels


import com.ghost.tagger.data.models.settings.ModelType
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghost.tagger.data.repository.SettingsRepository
import com.ghost.tagger.ui.state.SettingsUiState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class SettingsViewModel(
    private val repository: SettingsRepository
) : ViewModel() {

    // 1. The "Getter" - A live stream of the UI State
    // converting the Repo's AppSettings -> SettingsUiState
    val uiState: StateFlow<SettingsUiState> = repository.settings
        .map { appSettings ->
            SettingsUiState(settings = appSettings, isLoading = false)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SettingsUiState(isLoading = true)
        )
    // =====================================================
    // UI SETTINGS
    // =====================================================


    // =====================================================
    // GLOBAL SETTINGS
    // =====================================================

    fun setModelType(type: ModelType) {
        repository.updateSettings { it.copy(lastModelType = type) }
    }

    fun setDarkMode(enabled: Boolean) {
        repository.updateSettings { it.copy(isDarkMode = enabled) }
    }

    // =====================================================
    // TAGGER SETTINGS
    // =====================================================

    fun setTaggerModelPath(path: String) {
        repository.updateSettings {
            it.copy(tagger = it.tagger.copy(modelPath = path))
        }
    }

    fun setTaggerThreshold(value: Float) {
        repository.updateSettings {
            it.copy(tagger = it.tagger.copy(confidenceThreshold = value))
        }
    }

    fun setTaggerMaxTags(count: Int) {
        repository.updateSettings {
            it.copy(tagger = it.tagger.copy(maxTags = count))
        }
    }

    fun addExcludedTag(tag: String) {
        repository.updateSettings { current ->
            val newList = current.tagger.excludedTags + tag
            current.copy(tagger = current.tagger.copy(excludedTags = newList))
        }
    }

    fun removeExcludedTag(tag: String) {
        repository.updateSettings { current ->
            val newList = current.tagger.excludedTags - tag
            current.copy(tagger = current.tagger.copy(excludedTags = newList))
        }
    }

    // =====================================================
    // DESCRIPTOR SETTINGS
    // =====================================================

    fun setDescriptorModelPath(path: String) {
        repository.updateSettings {
            it.copy(descriptor = it.descriptor.copy(modelPath = path))
        }
    }

    fun setDescriptorTemperature(value: Float) {
        repository.updateSettings {
            it.copy(descriptor = it.descriptor.copy(temperature = value))
        }
    }

    fun setDescriptorLength(length: Int) {
        repository.updateSettings {
            it.copy(descriptor = it.descriptor.copy(maxDescriptionLength = length))
        }
    }

    fun setVisualPrompt(enabled: Boolean) {
        repository.updateSettings {
            it.copy(descriptor = it.descriptor.copy(useVisualPrompt = enabled))
        }
    }

    // =====================================================
    // SYSTEM SETTINGS
    // =====================================================

    fun setUseGpu(enabled: Boolean) {
        repository.updateSettings {
            it.copy(system = it.system.copy(useGpu = enabled))
        }
    }

    fun setParallelProcessing(enabled: Boolean) {
        repository.updateSettings {
            it.copy(system = it.system.copy(parallelProcessing = enabled))
        }
    }

    fun setAutoSaveToExif(enabled: Boolean) {
        repository.updateSettings {
            it.copy(system = it.system.copy(autoSaveToExif = enabled))
        }
    }

    fun setLastDirectory(path: String) {
        repository.updateSettings {
            it.copy(session = it.session.copy(lastDirectory = path))
        }
    }

    fun toggleSidebar(visible: Boolean) {
        repository.updateSettings {
            it.copy(session = it.session.copy(isSidebarVisible = visible))
        }
    }



    fun setPreviewPanelWidth(width: Float){
        repository.updateSettings {
            it.copy(session = it.session.copy(previewSectionWidthDp = width))
        }
    }

    fun saveToXmpFile(enabled: Boolean){
        repository.updateSettings {
            it.copy(system = it.system.copy(writeXmp = enabled))
        }
    }

    fun setSideBarWidth(width: Float){
        repository.updateSettings {
            it.copy(session = it.session.copy(sidePanelWidthDp = width))
        }
    }

    fun toggleSideBarVisible(){
        repository.updateSettings {
            it.copy(session = it.session.copy(isSidebarVisible = it.session.isSidebarVisible.not()))
        }
    }
}