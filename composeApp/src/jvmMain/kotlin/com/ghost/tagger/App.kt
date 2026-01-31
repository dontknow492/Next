package com.ghost.tagger

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.tooling.preview.Preview
import java.awt.FileDialog
import java.io.File

@Composable
@Preview
fun App() {
    MaterialTheme {
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.primaryContainer)
                .safeContentPadding()
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FilePickerScreen()

        }
    }
}


@Composable
fun openFileDialog(
    window: ComposeWindow,
    title: String,
    allowedExtensions: List<String> = listOf(".txt", ".png")
): File? {
    val fileDialog = FileDialog(window, title, FileDialog.LOAD)

    // This part tells the dialog what files to show
    fileDialog.file = allowedExtensions.joinToString(";") { "*$it" }

    fileDialog.isVisible = true // This PAUSES the code until user picks or cancels

    val directory = fileDialog.directory
    val file = fileDialog.file

    return if (directory != null && file != null) File(directory, file) else null
}