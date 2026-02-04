package com.ghost.tagger.data.models.settings

import kotlinx.serialization.json.Json
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.*

class SettingsManager(private val appName: String) {

    // 🔐 INTERNAL SECRET KEY
    // This makes the obfuscation unique to your app.
    // Even if someone base64 decodes the string, they get garbage data without this.
    private val SECRET_KEY = "GHOST_TAGGER_V1_SECURE_KEY"

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
        var cleanSettings = SettingsValidator.validateAll(settings)

        // Encrypt the API Key if it exists
        if (cleanSettings.apiKey != null) {
            val encryptedKey = encrypt(cleanSettings.apiKey)
            cleanSettings = cleanSettings.copy(apiKey = encryptedKey)
        }

        try {
            val jsonString = json.encodeToString(cleanSettings)
            settingsFile.writeText(jsonString)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // 4. The "Load" function
    fun loadSettings(): AppSettings {
        return try {
            if (settingsFile.exists()) {
                val jsonString = settingsFile.readText()
                val loadedSettings = json.decodeFromString<AppSettings>(jsonString)

                // Validate the loaded data before returning it to the UI
                var settings = SettingsValidator.validateAll(loadedSettings)

                // Decrypt the API Key if it exists
                if (settings.apiKey != null) {
                    settings = try {
                        val decryptedKey = decrypt(settings.apiKey)
                        settings.copy(apiKey = decryptedKey)
                    } catch (e: Exception) {
                        // If decryption fails (e.g., user edited the file manually),
                        // reset the key to null to prevent crashes.
                        println("Failed to decrypt API key: ${e.message}")
                        settings.copy(apiKey = null)
                    }
                }

                settings

            } else {
                AppSettings() // Return defaults if file doesn't exist
            }
        } catch (e: Exception) {
            // If the JSON is so broken it can't even be parsed,
            // we log it and return the safe factory defaults.
            println("Error loading settings: ${e.message}")
            AppSettings()
        }
    }

    // -------------------------------------------------------------------------
    // 🔐 Security Helpers (XOR + Base64)
    // -------------------------------------------------------------------------

    private fun encrypt(input: String): String {
        val inputBytes = input.toByteArray(StandardCharsets.UTF_8)
        val outputBytes = xorBytes(inputBytes)
        return Base64.getEncoder().encodeToString(outputBytes)
    }

    private fun decrypt(input: String): String {
        val inputBytes = Base64.getDecoder().decode(input)
        val outputBytes = xorBytes(inputBytes)
        return String(outputBytes, StandardCharsets.UTF_8)
    }

    // XOR operation is reversible: (A xor B) xor B = A
    private fun xorBytes(input: ByteArray): ByteArray {
        val output = ByteArray(input.size)
        val keyBytes = SECRET_KEY.toByteArray(StandardCharsets.UTF_8)

        for (i in input.indices) {
            // XOR the data byte with the key byte (cycling through the key)
            output[i] = (input[i].toInt() xor keyBytes[i % keyBytes.size].toInt()).toByte()
        }
        return output
    }
}