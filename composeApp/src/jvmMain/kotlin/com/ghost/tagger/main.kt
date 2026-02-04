package com.ghost.tagger

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.example.tagger.OnnxImageTagger
import com.ghost.tagger.di.appModule
import org.koin.core.context.startKoin
import java.io.File

fun main() {
    // 1. Start Koin BEFORE the UI lifecycle begins.
    // This prevents "Koin already started" errors and ensures modules are ready.
    startKoin {
        modules(appModule)
    }

    application {
        val state = rememberWindowState(size = DpSize(1200.dp, 800.dp))

        Window(
            title = "Ghost AI Tagger",
            onCloseRequest = ::exitApplication,
            state = state
        ) {
            MainScreen()
        }
    }
}

fun main2() {
    val modelPath = File("D:\\Downloads\\IDM Temp\\SmilingWolf\\wd-v1-4-swinv2-tagger-v2\\model.onnx").toPath()
    val csvPath = File("D:\\Downloads\\IDM Temp\\SmilingWolf\\wd-v1-4-swinv2-tagger-v2\\selected_tags.csv").toPath()

    OnnxImageTagger(modelPath, csvPath).use { tagger ->
        // 1. Predict with initial model
        val tags = tagger.predict(File("D:\\Media\\Image\\test.jpg"))

        // 2. Unload manually if needed (optional, load() handles this automatically)
        tagger.unload()

        tags.forEach {
            println(it.name)
        }

        // 3. Load a different model (e.g., swinv2)
//           val swinModelPath = File("path/to/wd-swinv2-tagger-v3.onnx").toPath()
//           tagger.load(swinModelPath)
//
//           val tags2 = tagger.predict(File("test_image.png"))
    }
}



