package com.ghost.tagger

import androidx.compose.runtime.*
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import co.touchlab.kermit.Logger
import com.example.tagger.OnnxImageTagger
import com.ghost.tagger.core.ModelManager
import com.ghost.tagger.data.repository.SettingsRepository
import com.ghost.tagger.di.appModule
import com.ghost.tagger.ui.section.GallerySection
import com.ghost.tagger.ui.theme.AppTheme
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.isSuccess
import io.ktor.client.plugins.json.Json
import io.ktor.client.request.header
import io.ktor.util.cio.writeChannel
import io.ktor.utils.io.copyAndClose
import io.ktor.utils.io.jvm.javaio.copyTo
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import java.io.File
import java.io.FileOutputStream
import java.lang.Thread.sleep

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



