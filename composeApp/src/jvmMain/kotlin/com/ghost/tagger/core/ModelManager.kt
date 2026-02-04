package com.ghost.tagger.core

import co.touchlab.kermit.Logger
import com.ghost.tagger.core.downloader.DownloadState
import com.ghost.tagger.core.downloader.FileValidationResult
import com.ghost.tagger.core.onnx.SmilingWolfTaggerModel
import com.ghost.tagger.core.onnx.`interface`.HuggingFaceTaggerModel
import com.ghost.tagger.data.models.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

object ModelManager {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // 1. Internal storage for the list using StateFlow
    private val _taggerModels = MutableStateFlow<List<HuggingFaceTaggerModel>>(emptyList())

    private val _currentSettings = MutableStateFlow(AppSettings())


    // 2. Public accessor as StateFlow
    val taggerModels: StateFlow<List<HuggingFaceTaggerModel>> = _taggerModels.asStateFlow()

    // 3. The Active Model State
    var activeModel: HuggingFaceTaggerModel? = null
        private set

    private var lastKnownPath: String? = null

    private var lastModelId: String? = null


    // 4. The Setup Function (Call this once when App starts!)
    // 4. ✅ NEW INIT: Pass the flow, not the values
    fun initialize(settingsFlow: Flow<AppSettings>) {
        settingsFlow
            .distinctUntilChanged() // Only react if settings actually change
            .onEach { settings ->
                _currentSettings.value = settings
                updateModelsConfiguration(settings)
            }
            .launchIn(scope)
    }

    // Add this variable to your class to track the actual root used
    private var currentModelsRootPath: String? = null

    private suspend fun updateModelsConfiguration(settings: AppSettings) {
        val newRoot = settings.modelDownloadPath.takeIf { it.path.isNotEmpty() }
            ?: AppSettings().modelDownloadPath

        val newPathString = newRoot.absolutePath

        // 1. Check if we actually need to reload
        if (currentModelsRootPath == newPathString && _taggerModels.value.isNotEmpty()) {
            return // Path hasn't changed and we already have models. Stop here.
        }

        Logger.i("📁 Path changed or init: $newPathString (was: $currentModelsRootPath)")

        // 2. Migration logic
        if (lastKnownPath != null && lastKnownPath != newPathString) {
            scope.launch(Dispatchers.IO) {
                migrateFiles(File(lastKnownPath!!), newRoot)
            }
        }

        // 3. Reload Models
        _taggerModels.update { createTaggerModelList(newRoot) }

        // 4. Update trackers
        currentModelsRootPath = newPathString
        lastKnownPath = newPathString

        if(lastModelId == null && settings.tagger.lastModelId.isNotBlank()){
            withContext(Dispatchers.IO){
                selectModel(settings.tagger.lastModelId)
                lastModelId = settings.tagger.lastModelId
            }
        }

    }

    private fun createTaggerModelList(rootDir: File): List<HuggingFaceTaggerModel> {
        // Helper to make the list cleaner
        fun create(repoId: String) = SmilingWolfTaggerModel(repoId, rootModelFolder = rootDir)


        return listOf(

            create("SmilingWolf/wd-vit-large-tagger-v3"),
            create("SmilingWolf/wd-eva02-large-tagger-v3"),
            create("SmilingWolf/wd-swinv2-tagger-v3"),
            create("SmilingWolf/wd-convnext-tagger-v3"),
            create("SmilingWolf/wd-vit-tagger-v3"),
            create("SmilingWolf/wd-v1-4-moat-tagger-v2"),
            create("SmilingWolf/wd-v1-4-swinv2-tagger-v2"),
            create("SmilingWolf/wd-v1-4-convnext-tagger-v2"),
            create("SmilingWolf/wd-v1-4-convnextv2-tagger-v2"),
            create("SmilingWolf/wd-v1-4-vit-tagger-v2")
        )
    }

    suspend fun selectModel(repoId: String) {
        // 1. Unload previous to save RAM
        activeModel?.close()

        // 2. Find new one
        val nextModel = _taggerModels.value.find { it.repoId == repoId } ?: return

        Logger.d("Model ${nextModel.repoId} has been successfully selected $repoId", tag = "ModelManager:selectModel")

        // 3. Load it (assuming it's downloaded)
        val validation = nextModel.getModelFileValidationResult()
        if (validation is FileValidationResult.Valid) {
            nextModel.load()
            activeModel = nextModel
        }

    }

    // ✅ Bridging the Interface: We inject the API key here
    fun downloadModel(model: HuggingFaceTaggerModel): Flow<DownloadState> {
        val apiKey = _currentSettings.value.apiKey
        return model.download(apiKey)
    }

    fun cancelDownload(model: HuggingFaceTaggerModel) {
        model.cancelDownload()
    }

    fun getDefaultTaggerModelId(): String {
        val default by lazy { _taggerModels.value.firstOrNull()?.repoId ?: "wd-vit-large-tagger-v3" }
        return default
    }

    fun openInExplorer(modelId: String) {
        val file = getFilePath(modelId)

        openFileInExplorer(file)
    }

    // ✅ Fixed: Uses the actual model instance to determine path
    fun getFilePath(modelId: String): File {
        val model = _taggerModels.value.find { it.repoId == modelId }

        return if (model != null) {
            // Best case: Ask the model (or construct based on its root)
            // Assuming the filename is just the last part of the repoId + .onnx
            val fileName = "${model.repoId.substringAfterLast("/")}.onnx"
            File(model.rootFolder, fileName)
        } else {
            // Fallback: Construct from settings (Safe default)
            val root = if (_currentSettings.value.modelDownloadPath.path.isNotBlank()) {
                _currentSettings.value.modelDownloadPath
            } else {
                File(System.getProperty("user.home"), ".ghosttagger/models")
            }
            File(root, "${modelId.substringAfterLast("/")}.onnx")
        }
    }

    /**
     * Moves .onnx and .csv files from the old directory to the new one.
     */
    private fun migrateFiles(oldRoot: File, newRoot: File) {
        if (!oldRoot.exists() || !oldRoot.isDirectory) return

        Logger.i("📦 Migration started: ${oldRoot.absolutePath} -> ${newRoot.absolutePath}")

        val files = oldRoot.listFiles() ?: return
        for (file in files) {
            // Only move relevant model files
            if (file.isFile && (file.extension == "onnx" || file.extension == "csv")) {
                val destFile = File(newRoot, file.name)
                try {
                    // renameTo is an atomic move on the same drive (fast)
                    val success = file.renameTo(destFile)
                    if (!success) {
                        // Fallback: Copy and delete if moving across different drives
                        file.copyTo(destFile, overwrite = true)
                        file.delete()
                    }
                    Logger.d("Moved: ${file.name}")
                } catch (e: Exception) {
                    Logger.e("Failed to migrate ${file.name}: ${e.message}")
                }
            }
        }
    }
}
