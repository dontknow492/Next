package com.ghost.tagger.core.onnx

import ai.djl.inference.Predictor
import ai.djl.modality.cv.Image
import ai.djl.modality.cv.ImageFactory
import ai.djl.modality.cv.transform.Resize
import ai.djl.modality.cv.transform.ToTensor
import ai.djl.ndarray.NDList
import ai.djl.repository.zoo.Criteria
import ai.djl.repository.zoo.ZooModel
import ai.djl.translate.Pipeline
import ai.djl.translate.Translator
import ai.djl.translate.TranslatorContext
import co.touchlab.kermit.Logger
import com.ghost.tagger.TagSource
import com.ghost.tagger.core.KtorDownloadManager
import com.ghost.tagger.core.onnx.`interface`.TaggerModel
import com.ghost.tagger.data.models.DownloadStatus
import com.ghost.tagger.data.models.ImageTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale.getDefault


class SmilingWolfTagger(
    modelName: String, // e.g. "wd-vit-large-tagger-v3"
    override val repoUrl: String, // "https://huggingface.co/SmilingWolf/..."
    private val rootModelFolder: File
) : TaggerModel {

    override val id = modelName
    override val displayName = modelName.replace("-", " ")
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(getDefault()) else it.toString() }

    // Folder: [rootModelFolder]/wd-vit-large-tagger-v3/
    private val modelDir = File(rootModelFolder, modelName)
    private val modelFile = File(modelDir, "model.onnx")
    private val tagsFile = File(modelDir, "selected_tags.csv")

    private var model: ZooModel<Image, List<ImageTag>>? = null
    private var predictor: Predictor<Image, List<ImageTag>>? = null

    override val isDownloaded: Boolean
        get() = modelFile.exists() && modelFile.length() > 0 &&
                tagsFile.exists() && tagsFile.length() > 0

    override fun download(): Flow<DownloadStatus> = flow {
        Logger.d("Downloading Model: ${id}")

        emitAll(verifyOrDownload(
            url = "$repoUrl/resolve/main/model.onnx",
            targetFile = modelFile,
            itemName = "Model"
        ))

        // 2. Verify and Download CSV
        emitAll(verifyOrDownload(
            url = "$repoUrl/resolve/main/selected_tags.csv",
            targetFile = tagsFile,
            itemName = "Tags"
        ))

    }

    /**
     * Helper logic:
     * 1. Check Remote Size.
     * 2. If Local Size matches Remote Size, skip download.
     * 3. Else, download.
     */
    private suspend fun verifyOrDownload(
        url: String,
        targetFile: File,
        itemName: String
    ): Flow<DownloadStatus> = flow {

        // Step A: Get Expected Size
        val remoteSize = KtorDownloadManager.getRemoteFileSize(url)

        // Step B: Compare with Local File
        if (targetFile.exists() && remoteSize > 0 && targetFile.length() == remoteSize) {
            Logger.i { "$itemName already exists and matches remote size ($remoteSize bytes). Skipping." }
            emit(
                DownloadStatus(
                    progress = 1.0f,
                    progressPercent = 100,
                    speed = "Checked",
                    eta = "Ready",
                    downloadedText = "Verified", // Indicate it was verified, not just downloaded
                    totalBytes = remoteSize,
                    downloadedBytes = remoteSize
                )
            )
            return@flow
        }

        // Step C: If we are here, file is missing, corrupt, or wrong size. Download it.
        Logger.i { "$itemName missing or size mismatch (Local: ${targetFile.length()} vs Remote: $remoteSize). Downloading..." }

        KtorDownloadManager.download(url, targetFile).collect { status ->
            // Pass through the status, maybe prefixing the status text with item name if you want
            emit(status)
        }
    }

    override suspend fun load() = withContext(Dispatchers.IO) {
        if (model != null) return@withContext 

        val tagNames = tagsFile.readLines().drop(1).map { line ->
            val parts = line.split(",")
            parts[1].replace("_", " ")
        }

        val translator = object : Translator<Image, List<ImageTag>> {
            override fun processInput(ctx: TranslatorContext, input: Image): NDList {
                val pipeline = Pipeline()
                .add(Resize(448, 448))
                .add(ToTensor())
                return pipeline.transform(NDList(input.toNDArray(ctx.ndManager)))
            }

            override fun processOutput(ctx: TranslatorContext, list: NDList): List<ImageTag> {
                val probs = list[0].softmax(0).toFloatArray()
                return probs.mapIndexed { index, confidence ->
                    if (index < tagNames.size) {
                        ImageTag(
                            name = tagNames[index],
                            confidence = confidence.toDouble(),
                            source = TagSource.AI
                        )
                    } else null
                }.filterNotNull()
                 .filter { it.confidence > 0.35 } 
                 .sortedByDescending { it.confidence }
            }

            override fun getBatchifier() = null
        }

        val criteria = Criteria.builder()
            .setTypes(Image::class.java, List::class.java as Class<List<ImageTag>>)
            .optModelPath(modelFile.toPath())
            .optEngine("OnnxRuntime")
            .optTranslator(translator)
            .build()

        model = criteria.loadModel()
        predictor = model!!.newPredictor()
    }

    override suspend fun predict(file: File): List<ImageTag> = withContext(Dispatchers.Default) {
        if (predictor == null) load()
        val image = ImageFactory.getInstance().fromFile(file.toPath())
        return@withContext predictor!!.predict(image)
    }

    override fun close() {
        predictor?.close()
        model?.close()
        predictor = null
        model = null
        System.gc()
    }
}
