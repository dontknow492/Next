package com.ghost.tagger.core

import co.touchlab.kermit.Logger
import com.ghost.tagger.core.onnx.SmilingWolfTagger
import com.ghost.tagger.core.onnx.`interface`.TaggerModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import java.io.File

object ModelManager {
    // 1. Internal storage for the list using StateFlow
    private val _taggerModels = MutableStateFlow<List<TaggerModel>>(emptyList())

    // 2. Public accessor as StateFlow
    val taggerModels: StateFlow<List<TaggerModel>> = _taggerModels.asStateFlow()

    // 3. The Active Model State
    var activeModel: TaggerModel? = null
        private set

    private lateinit var rootDir: File

    // 4. The Setup Function (Call this once when App starts!)
    fun init(userModelPath: String?) {
        // Fallback: If user hasn't set a path, use default AppData location
        rootDir = if (!userModelPath.isNullOrBlank()) {
            File(userModelPath)
        } else {
            File(System.getProperty("user.home"), ".ghosttagger/models")
        }

        // Create the folder if it doesn't exist
        if (!rootDir.exists()) rootDir.mkdirs()

        // 5. Instantiate the models with the correct path
        _taggerModels.value = listOf(
            SmilingWolfTagger(
                "wd-vit-large-tagger-v3",
                "https://huggingface.co/SmilingWolf/wd-vit-large-tagger-v3",
                rootDir
            ),
            SmilingWolfTagger(
                "wd-eva02-large-tagger-v3",
                "https://huggingface.co/SmilingWolf/wd-eva02-large-tagger-v3",
                rootDir
            ),
            SmilingWolfTagger(
                "wd-swinv2-tagger-v3",
                "https://huggingface.co/SmilingWolf/wd-swinv2-tagger-v3",
                rootDir
            ),
            SmilingWolfTagger(
                "wd-convnext-tagger-v3",
                "https://huggingface.co/SmilingWolf/wd-convnext-tagger-v3",
                rootDir
            ),
            SmilingWolfTagger(
                "wd-vit-tagger-v3",
                "https://huggingface.co/SmilingWolf/wd-vit-tagger-v3",
                rootDir
            ),
            SmilingWolfTagger(
                "wd-v1-4-moat-tagger-v2",
                "https://huggingface.co/SmilingWolf/wd-v1-4-moat-tagger-v2",
                rootDir
            ),
            SmilingWolfTagger(
                "wd-v1-4-swinv2-tagger-v2",
                "https://huggingface.co/SmilingWolf/wd-v1-4-swinv2-tagger-v2",
                rootDir
            ),
            SmilingWolfTagger(
                "wd-v1-4-convnext-tagger-v2",
                "https://huggingface.co/SmilingWolf/wd-v1-4-convnext-tagger-v2",
                rootDir
            ),
            SmilingWolfTagger(
                "wd-v1-4-convnextv2-tagger-v2",
                "https://huggingface.co/SmilingWolf/wd-v1-4-convnextv2-tagger-v2",
                rootDir
            ),
            SmilingWolfTagger(
                "wd-v1-4-vit-tagger-v2",
                "https://huggingface.co/SmilingWolf/wd-v1-4-vit-tagger-v2",
                rootDir
            )
        )
    }

    suspend fun selectModel(modelId: String) {
        // 1. Unload previous to save RAM
        activeModel?.close()

        // 2. Find new one
        val nextModel = _taggerModels.value.find { it.id == modelId } ?: return

        Logger.d ("Model ${nextModel.id} has been successfully selected $modelId", tag = "ModelManager:selectModel" )

        // 3. Load it (assuming it's downloaded)
        if (nextModel.isDownloaded) {
            nextModel.load()
            activeModel = nextModel
        }
    }

    fun getDefaultTaggerModelId(): String{
        val default by lazy { _taggerModels.value.firstOrNull()?.id ?: "wd-vit-large-tagger-v3"}
        return default
    }

    fun openInExplorer(modelId: String) {
        val file = getFilePath(modelId)
        openFileInExplorer(file)
    }

    fun getFilePath(modelId: String): File{
//        return "${rootDir}/$modelId.onnx"
        return File(rootDir, "$modelId.onnx")
    }
}
