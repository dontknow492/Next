package com.ghost.tagger.ui.state

import com.ghost.tagger.data.models.settings.AppSettings

// This is what the UI observes
data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val isLoading: Boolean = false,
    val error: String? = null
)