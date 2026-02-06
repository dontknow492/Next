package com.ghost.tagger.ui.viewmodels


import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ghost.tagger.core.openFileInExplorer
import com.ghost.tagger.data.enums.ThemeMode
import com.ghost.tagger.data.models.ImageTag
import com.ghost.tagger.data.models.settings.AppSettings
import com.ghost.tagger.data.models.settings.ModelType
import com.ghost.tagger.data.repository.SettingsRepository
import com.ghost.tagger.ui.state.SettingsUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

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

    fun settingsFlow(): Flow<AppSettings> = repository.settings

    fun setModelDownloadPath(path: File) {
        repository.updateSettings { it.copy(modelDownloadPath = path) }
    }

    fun setModelType(type: ModelType) {
        repository.updateSettings { it.copy(lastModelType = type) }
    }

    fun setDarkMode(enabled: Boolean) {
        repository.updateSettings { it.copy(isDarkMode = enabled) }
    }

    // =====================================================
    // TAGGER SETTINGS
    // =====================================================

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

    fun addExcludedTag(tag: ImageTag) {
        repository.updateSettings { current ->
            val newList = current.tagger.excludedTags + tag
            current.copy(tagger = current.tagger.copy(excludedTags = newList))
        }
    }

    fun removeExcludedTag(tag: ImageTag) {
        repository.updateSettings { current ->
            val newList = current.tagger.excludedTags - tag
            current.copy(tagger = current.tagger.copy(excludedTags = newList))
        }
    }

    fun clearExcludedTags() {
        repository.updateSettings {
            it.copy(tagger = it.tagger.copy(excludedTags = emptySet()))
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

    fun setLastDirectory(path: File) {
        repository.updateSettings {
            it.copy(session = it.session.copy(lastDirectory = path))
        }
    }

    fun toggleSidebar(visible: Boolean) {
        repository.updateSettings {
            it.copy(session = it.session.copy(isSidebarVisible = visible))
        }
    }


    fun setPreviewPanelWidth(width: Float) {
        repository.updateSettings {
            it.copy(session = it.session.copy(previewSectionWidthDp = width))
        }
    }

    fun saveToXmpFile(enabled: Boolean) {
        repository.updateSettings {
            it.copy(system = it.system.copy(writeXmp = enabled))
        }
    }

    fun setSideBarWidth(width: Float) {
        repository.updateSettings {
            it.copy(session = it.session.copy(sidePanelWidthDp = width))
        }
    }

    fun toggleSideBarVisible() {
        repository.updateSettings {
            it.copy(session = it.session.copy(isSidebarVisible = it.session.isSidebarVisible.not()))
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        repository.updateSettings {
            it.copy(themeMode = mode)
        }
    }

    fun openModelDownloadFolder() {
        viewModelScope.launch(Dispatchers.IO) {
            val path = uiState.value.settings.modelDownloadPath
            if (path.exists()) {
                openFileInExplorer(path)
            }
        }
    }
}