package com.ghost.tagger.ui.viewmodels


import androidx.lifecycle.ViewModel
import com.ghost.tagger.data.models.ImageItem
import com.ghost.tagger.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class MainViewModel(
    private val repository: SettingsRepository
) : ViewModel() {

    // 1. Observe Settings from the Repository
    // This makes the UI reactive to any setting change
    val settings = repository.settings

    // 2. Manage the Gallery List
    private val _images = MutableStateFlow<List<ImageItem>>(emptyList())
    val images = _images.asStateFlow()

    // 3. UI logic: Focusing an item
    fun focusItem(item: ImageItem) {
        _images.update { currentList ->
            currentList.map {
                it.copy(isFocused = it.id == item.id)
            }
        }
    }

    // 4. Update a setting through the chef (ViewModel)
    fun updateConfidence(value: Float) {
        repository.updateSettings {
            it.copy(tagger = it.tagger.copy(confidenceThreshold = value))
        }
    }
}