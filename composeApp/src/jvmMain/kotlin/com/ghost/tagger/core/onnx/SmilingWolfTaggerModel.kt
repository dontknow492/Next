package com.ghost.tagger.core.onnx

import ai.djl.Device
import ai.djl.Model
import ai.djl.inference.Predictor
import ai.djl.modality.cv.Image
import ai.djl.modality.cv.ImageFactory
import ai.djl.modality.cv.util.NDImageUtils
import ai.djl.ndarray.NDList
import ai.djl.ndarray.types.DataType
import ai.djl.repository.zoo.Criteria
import ai.djl.translate.Batchifier
import ai.djl.translate.Translator
import ai.djl.translate.TranslatorContext
import co.touchlab.kermit.Logger
import com.ghost.tagger.core.downloader.DownloadState
import com.ghost.tagger.core.downloader.FileValidationResult
import com.ghost.tagger.core.downloader.HuggingFaceDownloader
import com.ghost.tagger.core.onnx.`interface`.HuggingFaceTaggerModel
import com.ghost.tagger.data.enums.TagSource
import com.ghost.tagger.data.models.ImageTag
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import java.io.File
import java.nio.file.Path
import java.util.Locale.getDefault

class SmilingWolfTaggerModel(
    override val repoId: String,
    val modelName: String = "model.onnx",
    val tagsName: String = "selected_tags.csv",
    private val rootModelFolder: File,
    private val targetHeight: Int = 448,
    private val targetWidth: Int = 448
) : HuggingFaceTaggerModel {

    override val rootFolder: File = File(rootModelFolder, repoId)

    override val displayName = repoId.replace("-", " ")
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(getDefault()) else it.toString() }
    override val repoUrl: String = "https://huggingface.co/$repoId"


    private val modelDir = File(rootModelFolder, repoId)
    private val modelFile = File(modelDir, "model.onnx")
    private val tagsFile = File(modelDir, "selected_tags.csv")


    private var tagNames: List<String> = emptyList()
    private var model: Model? = null
    private var predictor: Predictor<Image, List<ImageTag>>? = null


    override suspend fun getTagFileValidationResult(apiKey: String?): FileValidationResult {
        return HuggingFaceDownloader.checkModelStatus(
            repoId = repoId,
            fileName = tagsName,
            localFile = tagsFile,
            apiKey = apiKey
        )
    }

    override fun isDownloading(): Boolean {
        return HuggingFaceDownloader.isDownloading(repoId, modelName) || HuggingFaceDownloader.isDownloading(repoId, tagsName)
    }

    override suspend fun getModelFileValidationResult(apiKey: String?): FileValidationResult {

        val result = HuggingFaceDownloader.checkModelStatus(
            repoId = repoId,
            fileName = modelName,
            localFile = modelFile,
            apiKey = apiKey
        )
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
        HuggingFaceDownloader.cancelDownload(repoId, modelName)
        HuggingFaceDownloader.cancelDownload(repoId, tagsName)
    }

    @Synchronized
    override fun load(useGpu: Boolean) {
        if (Device.gpu() != null && useGpu) {
            Device.gpu()
        } else {
            Device.cpu()
        }

        // Unload existing if needed
        unload()

        if (!modelFile.exists()) throw IllegalStateException("Model file not found at $modelFile. Please download first.")
        if (!tagsFile.exists()) throw IllegalStateException("Tags file not found at $tagsFile. Please download first.")

        // 1. Load Tags (Lazy load to ensure file exists)
        if (tagNames.isEmpty()) {
            tagNames = loadTagsFromCsv(tagsFile.toPath())
        }

        // 2. Load Model
//        val newModel = Model.newInstance("ImageTagger","OnnxRuntime")
//        newModel.load(modelFile.toPath())

        // 3. Create Predictor
        val translator = TaggerTranslator(tagNames, targetHeight, targetWidth)
//        val newPredictor = newModel.newPredictor(translator)

        val criteria = Criteria.builder()
            .setTypes(Image::class.java, List::class.java as Class<List<ImageTag>>)
            .optModelPath(modelFile.toPath()) // Path to your .onnx file
            .optEngine("OnnxRuntime")        // This prevents PyTorch from touching the file
//            .optDevice(device)
            .optTranslator(translator)
            .build()

        // Assign to properties
        this.model = criteria.loadModel()
        this.predictor = model?.newPredictor(translator)
//        criteria.downloadModel()
    }

    /**
     * Predicts tags for the given image file.
     *
     * @param imageFile The input image.
     * @param threshold Confidence threshold (default 0.35, typical for these models).
     */
    @Synchronized
    override fun predict(imageFile: File, threshold: Double): List<ImageTag> {
        val currentPredictor = predictor ?: throw IllegalStateException("No model loaded. Call load() first.")

        if (!imageFile.exists()) throw IllegalArgumentException("Image file not found: $imageFile")

        val image = ImageFactory.getInstance().fromFile(imageFile.toPath())

        val allPredictions = currentPredictor.predict(image)

        return allPredictions
            .filter { it.confidence >= threshold }
            .sortedByDescending { it.confidence }
    }


    /**
     * Predicts tags for a batch of image files.
     * Efficiently processes multiple images in a single inference pass if the backend supports it.
     *
     * @param imageFiles List of input image files.
     * @param threshold Confidence threshold.
     */
    @Synchronized
    override fun predictBatch(imageFiles: List<File>, threshold: Double, internalBatchSize: Int): List<List<ImageTag>> {
        val currentPredictor = predictor ?: throw IllegalStateException("No model loaded. Call load() first.")
        if (imageFiles.isEmpty()) return emptyList()

        // 2. Use chunked to process the files in stages
        return imageFiles.chunked(internalBatchSize).flatMap { fileChunk ->
            // Convert only this small chunk of files to DJL Images
            val images = fileChunk.map { file ->
                if (!file.exists()) throw IllegalArgumentException("Image file not found: $file")
                ImageFactory.getInstance().fromFile(file.toPath())
            }

            // Run inference on this mini-batch
            val batchResults = currentPredictor.batchPredict(images)

            // Close the image objects immediately after prediction to free memory
            // if your Image implementation requires manual disposal.

            // 3. Filter and sort for this chunk
            batchResults.map { tags ->
                tags.filter { it.confidence >= threshold }
                    .sortedByDescending { it.confidence }
            }
        }
    }


    @Synchronized
    override fun close() {
        unload()
    }

    @Synchronized
    fun unload() {
        predictor?.close()
        predictor = null

        model?.close()
        model = null

        // Suggest garbage collection to free off-heap memory from ONNX Runtime if needed
        // System.gc()
    }

    /**
     * Reads the CSV and extracts the tag names in order of tag_id.
     */
    private fun loadTagsFromCsv(path: Path): List<String> {
        val file = path.toFile()
        if (!file.exists()) throw IllegalArgumentException("Tags CSV not found at $path")

        // Skip header, split by comma, take 2nd column (index 1) for name
        return file.useLines { lines ->
            lines.drop(1) // Skip header: tag_id,name,category,count
                .map { line ->
                    val parts = line.split(",")
                    if (parts.size >= 2) parts[1] else ""
                }
                .filter { it.isNotEmpty() }
                .toList()
        }
    }

    /**
     * DJL Translator to convert Image -> Tensor -> List<ImageTag>
     */
    private class TaggerTranslator(
        private val tagList: List<String>,
        private val height: Int,
        private val width: Int
    ) : Translator<Image, List<ImageTag>> {

        override fun getBatchifier(): Batchifier = Batchifier.STACK

        override fun processInput(ctx: TranslatorContext, input: Image): NDList {
            val manager = ctx.ndManager

            // 1. Convert Image to NDArray (managed by PyTorch engine)
            // This produces an HWC tensor (Height, Width, 3)
            var array = input.toNDArray(manager)

            // 2. Resize using NDImageUtils (PyTorch backend)
            array = NDImageUtils.resize(array, width, height)

            // 3. Normalize to [0, 1]
            // Note: NDImageUtils.toTensor() usually converts HWC [0,255] -> CHW [0,1]
            // SmilingWolf models usually want NHWC [0,1].
            // So we manually divide to keep the HWC shape.
            array = array.toType(DataType.FLOAT32, false).div(255f)

            // If your model specifically needs NCHW (3, H, W), uncomment the line below:
            // array = array.transpose(2, 0, 1) // HWC -> CHW

            return NDList(array)
        }

        override fun processOutput(ctx: TranslatorContext, list: NDList): List<ImageTag> {
            // Since we use Batchifier.STACK, list[0] is the probabilities for the batch
            // For a single prediction, we take the first element of the batch
            val probabilities = list[0]

            // Use toFloatArray() to bring data from the engine to Java memory
            val floatArray = probabilities.toFloatArray()

            return floatArray.toList().mapIndexedNotNull { index, confidence ->
                if (confidence > 0.05 && index < tagList.size) {
                    ImageTag(
                        name = tagList[index],
                        confidence = confidence.toDouble(),
                        source = TagSource.AI
                    )
                } else null
            }.sortedByDescending { it.confidence }
        }
    }
}