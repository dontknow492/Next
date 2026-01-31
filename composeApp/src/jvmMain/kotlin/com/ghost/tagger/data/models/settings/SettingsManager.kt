package com.ghost.tagger.data.models.settings

import kotlinx.serialization.json.Json
import java.io.File

class SettingsManager(private val appName: String) {

    // 1. Create a pretty-printing JSON engine
    private val json = Json {
        prettyPrint = true          // Makes the file readable in Notepad
        ignoreUnknownKeys = true    // Prevents crashing if you add features later
        encodeDefaults = true       // Saves default values to the file
    }

    // 2. Locate the OS-specific AppData folder
    private val settingsFile: File by lazy {
        val os = System.getProperty("os.name").lowercase()
        val userHome = System.getProperty("user.home")

        val folder = when {
            os.contains("win") -> File(System.getenv("APPDATA"), appName)
            os.contains("mac") -> File(userHome, "Library/Application Support/$appName")
            else -> File(userHome, ".$appName") // Linux/Generic
        }

        if (!folder.exists()) folder.mkdirs()
        File(folder, "settings.json")
    }

    // 3. The "Save" function
    fun saveSettings(settings: AppSettings) {
        // Validate everything before it hits the disk
        val cleanSettings = SettingsValidator.validateAll(settings)

        try {
            val jsonString = json.encodeToString(cleanSettings)
            settingsFile.writeText(jsonString)
        } catch (e: Exception) { /* ... */ }
    }

    // 4. The "Load" function
    fun loadSettings(): AppSettings {
    return try {
        if (settingsFile.exists()) {
            val jsonString = settingsFile.readText()
            val loadedSettings = json.decodeFromString<AppSettings>(jsonString)

            // Validate the loaded data before returning it to the UI
            SettingsValidator.validateAll(loadedSettings)

        } else {
            AppSettings() // Return defaults if file doesn't exist
        }
    } catch (e: Exception) {
        // If the JSON is so broken it can't even be parsed,
        // we log it and return the safe factory defaults.
        println("Error loading/validating settings: ${e.message}")
        AppSettings()
    }
}
}