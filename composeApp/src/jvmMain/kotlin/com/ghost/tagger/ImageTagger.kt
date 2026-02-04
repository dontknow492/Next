package com.ghost.tagger

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import com.ghost.tagger.data.models.ImageTag
import java.io.File
import java.nio.FloatBuffer
import javax.imageio.ImageIO
import kotlin.math.exp

class ImageTagger(modelPath: String, csvPath: String) {
    private val env = OrtEnvironment.getEnvironment()
    private val session = env.createSession(modelPath)
    private val tags = File(csvPath).readLines().drop(1) // Skip CSV header

    fun tagImage(imagePath: String, limit: Int? = null): List<ImageTag> {
        val imagetags = mutableListOf<ImageTag>()
        val img = ImageIO.read(File(imagePath))

        // 1. Preprocess: Resize to 448x448 and flatten to BGR
        val floatBuffer = FloatBuffer.allocate(1 * 448 * 448 * 3)
        // Note: You'd typically use a loop to get RGB, convert to BGR, and normalize (0-255)
        // For simplicity, this is the conceptual step:
        for (y in 0 until 448) {
            for (x in 0 until 448) {
                val rgb = img.getRGB(x, y)
                floatBuffer.put(((rgb shr 0) and 0xFF).toFloat()) // Blue
                floatBuffer.put(((rgb shr 8) and 0xFF).toFloat()) // Green
                floatBuffer.put(((rgb shr 16) and 0xFF).toFloat()) // Red
            }
        }
        floatBuffer.rewind()

        // 2. Create Input Tensor (Shape: Batch=1, Height=448, Width=448, Channels=3)
        val inputTensor = OnnxTensor.createTensor(env, floatBuffer, longArrayOf(1, 448, 448, 3))

        // 3. Run Inference
        val result = session.run(mapOf(session.inputNames.first() to inputTensor))
        val output = (result[0].value as Array<FloatArray>)[0]

        // 4. Map to Tags (Sigmoid + Threshold)
//        println("Top Tags:")
        output.forEachIndexed { index, score ->
            val probability = 1.0 / (1.0 + exp(-score.toDouble())) // Manual Sigmoid
            imagetags.add(
                ImageTag(
                    name = tags[index].split(",")[1],
                    confidence = probability
                )
            )
        }

        return imagetags.sortedByDescending { it.confidence }.take(limit ?: imagetags.size)
    }
}


//fun main() {
//    // 1. Define paths to your local files
//    val modelPath = """C:\Users\Ghost\.cache\huggingface\hub\models--SmilingWolf--wd-eva02-large-tagger-v3\snapshots\b25b82a03f7282e41aa2f257a52c7583b710bd1c\model.onnx"""
//    val csvPath = """C:\Users\Ghost\.cache\huggingface\hub\models--SmilingWolf--wd-eva02-large-tagger-v3\snapshots\b25b82a03f7282e41aa2f257a52c7583b710bd1c\selected_tags.csv"""
//
//    // 2. Initialize the tagger
//    val tagger = ImageTagger(modelPath, csvPath)
//
//    // 3. Run the tagging process
//    println("Starting AI Image Tagging...")
//    tagger.tagImage("""D:\Program\python\PyTorchBegginner\src\images\mouse.png""")
//}