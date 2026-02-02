package com.ghost.tagger

import androidx.compose.runtime.*
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import co.touchlab.kermit.Logger
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
        // 2. Retrieve the Repo from Koin (Java helper is easiest in 'main')
        // We use 'remember' so we don't look it up every frame.
        val settingsRepo = remember {
            GlobalContext.get().get<SettingsRepository>()
        }

        // 3. Load the path safely
        // Assuming your repo has a suspend function like getModelPath()
        // or a Flow. Here is the safest way to handle the async load:
        ModelManager.initialize(settingsRepo.settings)

        val state = rememberWindowState(size = DpSize(1200.dp, 800.dp))

        Window(
            title = "Ghost AI Tagger",
            onCloseRequest = ::exitApplication,
            state = state
        ) {
            AppTheme(darkTheme = true) {
                MainScreen()
//                GallerySection()
            }
        }
    }
}

