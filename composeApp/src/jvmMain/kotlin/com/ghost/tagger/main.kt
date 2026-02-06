package com.ghost.tagger

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.ghost.tagger.di.appModule
import demo.composeapp.generated.resources.Res
import demo.composeapp.generated.resources.icon
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.InternalResourceApi
import org.jetbrains.compose.resources.ResourceItem
import org.jetbrains.compose.resources.painterResource
import org.koin.core.context.startKoin
import java.awt.SplashScreen
import java.awt.SplashScreen.getSplashScreen
import java.io.File

@OptIn(InternalResourceApi::class)
fun main() {
    // 1. Start Koin BEFORE the UI lifecycle begins.
    // This prevents "Koin already started" errors and ensures modules are ready.
    startKoin {
        modules(appModule)
    }



    application {
        val state = rememberWindowState()

        Window(
            title = BuildKonfig.APP_NAME,
            onCloseRequest = ::exitApplication,
            icon = painterResource(Res.drawable.icon),
            state = state
        ) {
            LaunchedEffect(Unit) {
                getSplashScreen()?.close()
            }
            MainScreen()
        }
    }
}



