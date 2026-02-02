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
import com.ghost.tagger.core.downloader.DownloadState
import com.ghost.tagger.core.downloader.FileValidationResult
import com.ghost.tagger.core.downloader.HuggingFaceDownloader
import com.ghost.tagger.core.onnx.`interface`.HuggingFaceTaggerModel
import com.ghost.tagger.data.models.ImageTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale.getDefault

class SmilingWolfTaggerModel(
    override val repoId: String,
    val modelName: String = "model.onnx",
    val tagsName: String = "selected_tags.csv",
    private val rootModelFolder: File,
) : HuggingFaceTaggerModel {

    override val rootFolder: File = File(rootModelFolder, repoId)

    override val displayName = repoId.replace("-", " ")
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(getDefault()) else it.toString() }
    override val repoUrl: String = "https://huggingface.co/$repoId"


    private val modelDir = File(rootModelFolder, repoId)
    private val modelFile = File(modelDir, "model.onnx")
    private val tagsFile = File(modelDir, "selected_tags.csv")


    private var model: ZooModel<Image, List<ImageTag>>? = null
    private var predictor: Predictor<Image, List<ImageTag>>? = null


    override suspend fun getTagFileValidationResult(apiKey: String?): FileValidationResult {
        return HuggingFaceDownloader.checkModelStatus(
            repoId = repoId,
            fileName = tagsName,
            localFile = tagsFile,
            apiKey = apiKey
        )
    }

    override suspend fun getModelFileValidationResult(apiKey: String?): FileValidationResult {

        val result =  HuggingFaceDownloader.checkModelStatus(
            repoId = repoId,
            fileName = modelName,
            localFile = modelFile,
            apiKey = apiKey
        )
        Logger.i("Model Path: $modelFile, Result: $result", tag = "GetModelFileValidationResult")
        return result
    }


    override fun download(apiKey: String?): Flow<DownloadState> = flow {
        emitAll(
            validateAndDownload(
                repoId = repoId,
                fileName = modelName,
                destination = modelFile,
                apiKey = apiKey
            )
        )
        emitAll(
            validateAndDownload(
                repoId = repoId,
                fileName = tagsName,
                destination = tagsFile,
                apiKey = apiKey
            )
        )

    }

    fun validateAndDownload(
        repoId: String,
        fileName: String,
        destination: File,
        apiKey: String? = null
    ): Flow<DownloadState> = flow {

        when (val validateResult = HuggingFaceDownloader.checkModelStatus(
            repoId = repoId,
            fileName = fileName,
            localFile = destination,
            apiKey = apiKey
        )) {

            is FileValidationResult.Corrupted -> {
                Logger.i("File corrupted, redownloading: $destination")
                destination.delete()
                emitAll(
                    HuggingFaceDownloader.download(
                        repoId, fileName, destination, apiKey
                    )
                )
            }

            is FileValidationResult.NotDownloaded -> {
                emitAll(
                    HuggingFaceDownloader.download(
                        repoId, fileName, destination, apiKey
                    )
                )
            }

            is FileValidationResult.Valid -> {
                Logger.i("File already valid: $destination")
                emit(DownloadState.Success(destination))
            }

            is FileValidationResult.Error -> {
                emit(DownloadState.Error("Validation failed: $validateResult"))
            }
        }
    }


    override fun cancelDownload() {
        HuggingFaceDownloader.cancelCurrentDownload()
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