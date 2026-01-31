package com.ghost.tagger.core

import co.touchlab.kermit.Logger
import com.ghost.tagger.core.onnx.SmilingWolfTagger
import com.ghost.tagger.core.onnx.`interface`.TaggerModel
import java.io.File

object ModelManager {
    // 1. Internal storage for the list
    private var _taggerModels: List<TaggerModel> = emptyList()

    // 2. Public accessor (safe to call)
    val taggerModels: List<TaggerModel>
        get() = _taggerModels

    // 3. The Active Model State
    var activeModel: TaggerModel? = null
        private set

    // 4. The Setup Function (Call this once when App starts!)
    fun init(userModelPath: String?) {
        // Fallback: If user hasn't set a path, use default AppData location
        val rootDir = if (!userModelPath.isNullOrBlank()) {
            File(userModelPath)
        } else {
            File(System.getProperty("user.home"), ".ghosttagger/models")
        }

        // Create the folder if it doesn't exist
        if (!rootDir.exists()) rootDir.mkdirs()

        // 5. Instantiate the models with the correct path
        _taggerModels = listOf(
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
        val nextModel = taggerModels.find { it.id == modelId } ?: return

        Logger.d ("Model ${nextModel.id} has been successfully selected $modelId", tag = "ModelManager:selectModel" )

        // 3. Load it (assuming it's downloaded)
        if (nextModel.isDownloaded) {
            nextModel.load()
            activeModel = nextModel
        }
//        nextModel.download()
    }
}