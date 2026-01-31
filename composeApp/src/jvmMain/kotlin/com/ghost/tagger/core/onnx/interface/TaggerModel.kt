package com.ghost.tagger.core.onnx.`interface`

import com.ghost.tagger.data.models.DownloadStatus
import com.ghost.tagger.data.models.ImageTag
import java.io.File

interface TaggerModel {
    val id: String           // e.g. "wd-v3-large"
    val displayName: String  // e.g. "Waifu Diffusion V3 (Large)"
    val isDownloaded: Boolean
    val repoUrl: String

    // Returns a Flow of status so UI can show "Downloading... 45%"
    fun download(): kotlinx.coroutines.flow.Flow<DownloadStatus>

    // Loads the model into RAM (DJL initialization)
    suspend fun load()

    // Frees RAM
    fun close()

    // The main job
    suspend fun predict(file: File): List<ImageTag>
}