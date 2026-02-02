package com.ghost.tagger.core.onnx.`interface`

import com.ghost.tagger.core.downloader.DownloadState
import com.ghost.tagger.core.downloader.FileValidationResult
import com.ghost.tagger.data.models.ImageTag
import kotlinx.coroutines.flow.Flow
import java.io.File

interface HuggingFaceTaggerModel {
    val repoId: String
    val displayName: String
    val repoUrl: String
    val rootFolder: File





    fun download(apiKey: String? = null): Flow<DownloadState>

    fun cancelDownload()

    suspend fun load()

    fun close()

    suspend fun predict(file: File): List<ImageTag>

    suspend fun getTagFileValidationResult(apiKey: String? = null): FileValidationResult

    suspend fun getModelFileValidationResult(apiKey: String? = null): FileValidationResult

}