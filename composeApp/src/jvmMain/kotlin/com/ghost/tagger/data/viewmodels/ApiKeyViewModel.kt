package com.ghost.tagger.data.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghost.tagger.data.models.settings.SettingsManager
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// 1. UI State Definition
data class ApiKeyDialogState(
    val isOpen: Boolean = false,
    val currentKey: String? = null,
    val isVerifying: Boolean = false,
    val error: String? = null
)

// 2. The ViewModel
class ApiKeyViewModel(
    private val settingsManager: SettingsManager
) : ViewModel() {

    // Backing property for state
    private val _state = MutableStateFlow(ApiKeyDialogState())
    val state = _state.asStateFlow()

    /**
     * Opens the dialog and pre-fills the input with the saved key (if any).
     */
    fun openDialog() {
        // Reload settings to ensure we have the latest data
        val currentSettings = settingsManager.loadSettings()

        _state.update {
            it.copy(
                isOpen = true,
                currentKey = currentSettings.apiKey,
                error = null,
                isVerifying = false
            )
        }
    }

    /**
     * Closes the dialog without saving.
     */
    fun dismissDialog() {
        if (_state.value.isVerifying) return // Prevent accidental close during network op
        _state.update { it.copy(isOpen = false) }
    }

    /**
     * Verifies the token with Hugging Face and saves it if valid.
     */
    fun verifyAndSave(apiKey: String) {
        if (apiKey.isBlank()) {
            _state.update { it.copy(error = "Token cannot be empty") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isVerifying = true, error = null) }

            val isValid = checkTokenNetwork(apiKey)

            if (isValid) {
                // Success: Save to disk
                try {
                    val currentSettings = settingsManager.loadSettings()
                    settingsManager.saveSettings(currentSettings.copy(apiKey = apiKey))

                    // Close dialog on success
                    _state.update { it.copy(isOpen = false, isVerifying = false) }
                } catch (e: Exception) {
                    _state.update {
                        it.copy(
                            isVerifying = false,
                            error = "Failed to save settings: ${e.localizedMessage}"
                        )
                    }
                }
            } else {
                // Failure: Show error
                _state.update {
                    it.copy(
                        isVerifying = false,
                        error = "Invalid token. Please check your input."
                    )
                }
            }
        }
    }

    // 3. Private Network Logic
    private suspend fun checkTokenNetwork(token: String): Boolean {
        // Using a temporary client for this specific check to avoid keeping connections open unnecessarily
        val client = HttpClient(CIO)
        return try {
            val response = client.get("https://huggingface.co/api/whoami-v2") {
                header("Authorization", "Bearer $token")
            }
            response.status == HttpStatusCode.OK
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            client.close()
        }
    }
}