package com.ghost.tagger


import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ghost.tagger.data.models.ImageTag
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.exists
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


data class ImageData(
    val id: Int,
    val file: PlatformFile,
    val tags: List<ImageTag>
)


@Composable
fun FilePickerScreen() {
    // 1. Create the "Launcher"
    var pickedFile: PlatformFile? by remember {
        mutableStateOf(PlatformFile(path = "C:\\Users\\Ghost\\Documents\\DOpus_Wallpaper.bmp"))
    }
    val fileTags: MutableList<ImageTag> = remember { mutableStateListOf<ImageTag>() }
    var enabled by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    var isProcessing by remember { mutableStateOf(false) }
    val launcher = rememberFilePickerLauncher(
        type = FileKitType.Image

    ) { file: PlatformFile? ->
        // This runs AFTER the user picks a file
        if (file != null && file.exists()) {
            pickedFile = file
        }
        enabled = true
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Button(
            onClick = {
                launcher.launch()
                enabled = false
            },
            enabled = enabled
        ) {
            Text("Pick a File")
        }
        if (pickedFile != null) {
            ImageCard(
                data = ImageData(1, pickedFile!!, fileTags)
            )
        }
        Button(
            onClick = {
                // 2. Launch a background task
                scope.launch(Dispatchers.Default) {
                    isProcessing = true
                    try {
                        // This runs in the background
                        val result = TaggerService.instance.tagImage(pickedFile!!.path)
                        fileTags.clear()
                        fileTags.addAll(result)

                    } finally {
                        isProcessing = false // Re-enable the UI
                    }
                }
            },
            // 3. Disable the button while it's already working
            enabled = pickedFile != null && !isProcessing
        ) {
            if (isProcessing) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp))
            } else {
                Text("Tag it")
            }
        }
    }
}


@Composable
fun ImageCard(data: ImageData, modifier: Modifier = Modifier, onImageClick: () -> Unit = {}) {
    Row(
        modifier = modifier.padding(16.dp),
    ) {
        AsyncImage(
            model = data.file.path,
            modifier = Modifier
//                .size(128.dp, 128.dp)
                .padding(8.dp)
                .border(2.dp, Color.Gray, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Gray),
            contentDescription = data.file.name,
        )
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp).weight(1f),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = data.file.name,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
                style = MaterialTheme.typography.titleLarge
            )
            TextButton(
                onClick = {},
                contentPadding = PaddingValues(1.dp),
                shape = MaterialTheme.shapes.extraSmall,
                modifier = Modifier.height(24.dp)
            ) {
                Text(
                    text = data.file.path,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            if (data.tags.isNotEmpty()) {
                Row {
                    Text(
                        text = "Tags: ",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        textDecoration = TextDecoration.Underline
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    SelectionContainer {
                        Text(
                            text = data.tags.take(5).joinToString(", ") { it.name },
                            style = MaterialTheme.typography.bodySmall,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            } else {
                Text(
                    text = "No tags found",
                    style = MaterialTheme.typography.bodySmall,
                    overflow = TextOverflow.MiddleEllipsis
                )
            }

        }
    }
}

@Preview(showBackground = false, heightDp = 100, widthDp = 500, showSystemUi = false)
@Composable
fun ImageCardPreview() {
    val data = ImageData(
        id = 0,
        file = PlatformFile(path = "C:\\Users\\Ghost\\Documents\\DOpus_Wallpaper.bmp"),
        tags = listOf(
        )
    )
    ImageCard(data = data, modifier = Modifier.background(Color.Red))
}

