package com.example.tagger

import ai.djl.Model
import ai.djl.inference.Predictor
import ai.djl.modality.cv.Image
import ai.djl.modality.cv.ImageFactory
import ai.djl.ndarray.NDList
import ai.djl.ndarray.types.Shape
import ai.djl.translate.Batchifier
import ai.djl.translate.Translator
import ai.djl.translate.TranslatorContext
import com.ghost.tagger.data.enums.TagSource
import com.ghost.tagger.data.models.ImageTag
import java.awt.image.BufferedImage
import java.io.File
import java.nio.FloatBuffer
import java.nio.file.Path

// --- Data Structures ---


/**
 * Predicts image tags using ONNX models (e.g., SmilingWolf/wd-vit-tagger-v3).
 *
 * @param initialModelPath Path to the .onnx model file to load initially.
 * @param tagsCsvPath Path to the selected_tags.csv file.
 * @param targetHeight Height expected by the model (e.g., 448 for ViT-v3, 384 for SwinV2-v3).
 * @param targetWidth Width expected by the model.
 */
class OnnxImageTagger(
    private val initialModelPath: Path,
    private val tagsCsvPath: Path,
    private val targetHeight: Int = 448,
    private val targetWidth: Int = 448
) : AutoCloseable {

    private val tagNames: List<String>
    private var model: Model? = null
    private var predictor: Predictor<Image, List<ImageTag>>? = null

    init {
        // 1. Parse CSV to build the index -> name mapping
        // CSV Format expected: tag_id,name,category,count
        tagNames = loadTagsFromCsv(tagsCsvPath)

        // 2. Load the initial ONNX Model
        load(initialModelPath)
    }

    /**
     * Loads a specific ONNX model file.
     * Unloads the previous model if one was loaded.
     */
    @Synchronized
    fun load(modelPath: Path) {
        // Unload existing if needed
        unload()

        val newModel = Model.newInstance("ImageTagger")
        newModel.load(modelPath)

        // Create Predictor with custom Translator
        val translator = TaggerTranslator(tagNames, targetHeight, targetWidth)
        val newPredictor = newModel.newPredictor(translator)

        // Assign to properties
        this.model = newModel
        this.predictor = newPredictor
    }

    /**
     * Unloads the current model and frees memory.
     */
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
     * Checks if a model is currently loaded.
     */
    fun isLoaded(): Boolean = predictor != null

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
     * Predicts tags for the given image file.
     *
     * @param imageFile The input image.
     * @param threshold Confidence threshold (default 0.35, typical for these models).
     */
    @Synchronized
    fun predict(imageFile: File, threshold: Double = 0.35): List<ImageTag> {
        val currentPredictor = predictor ?: throw IllegalStateException("No model loaded. Call load() first.")

        if (!imageFile.exists()) throw IllegalArgumentException("Image file not found: $imageFile")

        val image = ImageFactory.getInstance().fromFile(imageFile.toPath())

        val allPredictions = currentPredictor.predict(image)

        return allPredictions
            .filter { it.confidence >= threshold }
            .sortedByDescending { it.confidence }
    }

    @Synchronized
    override fun close() {
        unload()
    }

    /**
     * DJL Translator to convert Image -> Tensor -> List<ImageTag>
     */
    private class TaggerTranslator(
        private val tagList: List<String>,
        private val height: Int,
        private val width: Int
    ) : Translator<Image, List<ImageTag>> {

        override fun getBatchifier(): Batchifier? {
            return null // usually null for ONNX single image, or Batchifier.STACK
        }

        override fun processInput(ctx: TranslatorContext, input: Image): NDList {
            val manager = ctx.ndManager
            val originalImage = input.wrappedImage

            // We create a direct FloatBuffer for the NDArray.
            // This bypasses 'split', 'stack', 'flip', and 'resize' NDArray operations
            // which are often unsupported by the ONNX Runtime engine.
            val buffer = FloatBuffer.allocate(width * height * 3)

            if (originalImage is BufferedImage) {
                // 1. Resize using Java AWT (Robust)
                val resized = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
                val g = resized.createGraphics()
                g.drawImage(originalImage, 0, 0, width, height, null)
                g.dispose()

                // 2. Extract pixels and converting RGB -> BGR manually
                val pixels = IntArray(width * height)
                resized.getRGB(0, 0, width, height, pixels, 0, width)

                for (pixel in pixels) {
                    val r = (pixel shr 16) and 0xFF
                    val g = (pixel shr 8) and 0xFF
                    val b = pixel and 0xFF

                    // BGR Order: [Blue, Green, Red]
                    buffer.put(b.toFloat())
                    buffer.put(g.toFloat())
                    buffer.put(r.toFloat())
                }
            } else {
                // Fallback for non-standard image types (e.g. Android Bitmap logic would go here)
                // If we reach here, we try standard ops, but warn this might fail on ONNX engine.
                // For robustness, this branch assumes we can get pixels or fail.
                throw UnsupportedOperationException("Only BufferedImage is supported for this ONNX Tagger to ensure robustness.")
            }

            buffer.rewind()

            // Create NDArray directly from buffer with shape (1, Height, Width, 3)
            // This is the specific input shape for WD Taggers (NHWC)
            val array = manager.create(buffer, Shape(1, height.toLong(), width.toLong(), 3))

            // NOTE: If your specific ONNX model expects (1, 3, H, W) [NCHW],
            // you must change the Shape above to (1, 3, height, width)
            // AND change the loop order above to fill planes (RRR...GGG...BBB...).

            return NDList(array)
        }

        override fun processOutput(ctx: TranslatorContext, list: NDList): List<ImageTag> {
            // Output 0 is the probabilities tensor
            val probabilities = list[0] // Shape (1, num_tags)

            // Get float array
            val floatArray = probabilities.toFloatArray()

            val result = mutableListOf<ImageTag>()

            // Iterate and map
            for (i in floatArray.indices) {
                // Optimization: Pre-filter very low confidence to save memory
                if (floatArray[i] > 0.05) {
                    if (i < tagList.size) {
                        result.add(
                            ImageTag(
                                name = tagList[i],
                                confidence = floatArray[i].toDouble(),
                                source = TagSource.AI
                            )
                        )
                    }
                }
            }

            return result
        }
    }
}

/*
   Usage Example:

   fun main() {
       val modelPath = File("path/to/wd-vit-tagger-v3.onnx").toPath()
       val csvPath = File("path/to/selected_tags.csv").toPath()

       OnnxImageTagger(modelPath, csvPath).use { tagger ->
           // 1. Predict with initial model
           val tags = tagger.predict(File("test_image.png"))

           // 2. Unload manually if needed (optional, load() handles this automatically)
           tagger.unload()

           // 3. Load a different model (e.g., swinv2)
           val swinModelPath = File("path/to/wd-swinv2-tagger-v3.onnx").toPath()
           tagger.load(swinModelPath)

           val tags2 = tagger.predict(File("test_image.png"))
       }
   }
*/