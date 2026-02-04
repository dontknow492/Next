package com.ghost.tagger.data.repository

import com.ghost.tagger.data.models.settings.AppSettings
import com.ghost.tagger.data.models.settings.SessionState
import com.ghost.tagger.data.models.settings.SettingsManager
import com.ghost.tagger.data.models.settings.SettingsValidator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*

@OptIn(FlowPreview::class)
class SettingsRepository(
    private val manager: SettingsManager,
    private val scope: CoroutineScope // Pass a scope (usually the one from your ViewModel)
) {
    // 1. The private mutable state
    private val _settings = MutableStateFlow(manager.loadSettings())

    // 2. The public "Read-Only" flow that the UI will observe
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    init {
        // 3. Automatic Saving: Whenever the flow changes, save it to disk!
        _settings
            .drop(1) // Skip the first emission (the initial load)
            .debounce(500) // Wait for 500ms of "silence" before saving (prevents lag while sliding)
            .onEach { updatedSettings ->
                manager.saveSettings(updatedSettings)
            }
            .launchIn(scope)
    }

    // 4. The update function (Aesthetic "Copy" pattern)
    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val current = _settings.value
        val updated = transform(current)
        // We validate before pushing to the flow
        _settings.value = SettingsValidator.validateAll(updated)
//        _settings.value = updated

    }

    fun updateSessionSettings(transform: SessionState) {
        _settings.update {
            it.copy(session = transform)
        }
    }
}