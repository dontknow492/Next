package com.ghost.tagger.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun OpenDirectoryPanel(
    modifier: Modifier = Modifier,
    // Pass ViewModel action here
    onDirectorySelected: (File) -> Unit
) {
    // 1. Setup the Picker
    var isFileDialogOpened by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val openDir = rememberDirectoryPicker(title = "Open Image Folder") { file ->
        if (file != null) {
            onDirectorySelected(file)
        }
        isFileDialogOpened = false
    }

    // 2. Draw the UI
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        DashedDropZone(
            title = "Open Image Directory",
            subtitle = "Supports JPG, PNG, WEBP",
            onClick = {
                if (!isFileDialogOpened){
                    scope.launch {
                        isFileDialogOpened = true
                        openDir()
                    }
                }
            } // <--- Just pass the launcher lambda!
        )
    }
}