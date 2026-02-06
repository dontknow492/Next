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

    fun isDownloading(): Boolean

    fun download(apiKey: String? = null): Flow<DownloadState>

    fun cancelDownload()

    fun load(useGpu: Boolean = true)

    fun close()

    fun predict(imageFile: File, threshold: Double = 0.35): List<ImageTag>

    fun predictBatch(imageFiles: List<File>, threshold: Double, internalBatchSize: Int = 8): List<List<ImageTag>>

    suspend fun getTagFileValidationResult(apiKey: String? = null): FileValidationResult

    suspend fun getModelFileValidationResult(apiKey: String? = null): FileValidationResult

}