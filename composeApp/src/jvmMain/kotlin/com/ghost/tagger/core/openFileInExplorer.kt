package com.ghost.tagger.core

import co.touchlab.kermit.Logger
import java.io.File

fun openFileInExplorer(file: File) {
    Logger.d(tag = "GalleryViewModel: OpenInExplorer", messageString = "Request for Opening ${file.path}")
    try {
        if (file.exists()) {
            if (file.isDirectory) {
                Runtime.getRuntime().exec(arrayOf("explorer.exe", file.absolutePath))
            } else {
                Runtime.getRuntime().exec(arrayOf("explorer.exe", "/select,${file.absolutePath}"))
            }
        } else {
            file.parentFile?.takeIf { it.exists() }?.let {
                Runtime.getRuntime().exec(arrayOf("explorer.exe", it.absolutePath))
            }
        }
        Logger.d(tag = "GalleryViewModel: OpenInExplorer", messageString = "Opening ${file.path}")
    } catch (e: Exception) {
        Logger.e(e) { "Error opening in explorer: ${e.stackTraceToString()}" }
    }
}