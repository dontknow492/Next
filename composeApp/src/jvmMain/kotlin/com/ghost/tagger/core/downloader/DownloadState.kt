package com.ghost.tagger.core.downloader

import com.ghost.tagger.data.models.DownloadStatus
import java.io.File

/**
 * Sealed class to represent the UI state of the downloader.
 * Collect this in your Compose UI.
 */
sealed class DownloadState {
    data object Idle : DownloadState()
    data class Downloading(val status: DownloadStatus) : DownloadState()
    data class Success(val file: File) : DownloadState()
    data class Error(val message: String, val exception: Throwable? = null) : DownloadState()
}