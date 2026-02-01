package com.ghost.tagger.core

import co.touchlab.kermit.Logger
import com.ghost.tagger.data.models.DownloadStatus
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import io.ktor.client.request.head
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.CancellationException
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt



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

/**
 * Result of the file verification check.
 */
sealed class FileValidationResult {
    data object Valid : FileValidationResult()
    data object NotDownloaded : FileValidationResult()
    data class Corrupted(val localSize: Long, val remoteSize: Long) : FileValidationResult()
    data class Error(val message: String) : FileValidationResult()
}


object HuggingFaceDownloader {
    private const val USER_AGENT = "GhostTaggerApp/1.0 (Kotlin/Ktor)"
    private const val USER_AGENT_2 = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    // Single client instance for connection pooling
    private val client = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = null // No limit for large files
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = null
        }
        install(UserAgent) {
            agent = USER_AGENT
        }
    }

    private var currentDownloadJob: Job? = null

    private val downloaderScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Downloads a model from Hugging Face with progress tracking.
     * Cancels any existing download automatically.
     */
    fun download(
        repoId: String,
        fileName: String,
        destination: File,
        apiKey: String? = null
    ): Flow<DownloadState> = flow {
        // 1. Cancel previous downloads and emit initial state
        cancelCurrentDownload()
        emit(DownloadState.Downloading(emptyStatus()))

        val url = "https://huggingface.co/$repoId/resolve/main/$fileName"

        try {
            // 2. Prepare the request
            val response = client.prepareGet(url) {
                if (!apiKey.isNullOrBlank()) {
                    header("Authorization", "Bearer $apiKey")
                }
            }.execute()

            if (!response.status.isSuccess()) {
                throw Exception("Server returned ${response.status}: ${response.bodyAsText().take(200)}")
            }

            // 3. Setup tracking variables
            val totalBytes = response.contentLength()
            var downloadedBytes = 0L
            val startTime = System.currentTimeMillis()
            var lastEmittedTime = 0L

            // 4. Create stream
            val channel: ByteReadChannel = response.bodyAsChannel()
            destination.parentFile.mkdirs()

            destination.outputStream().use { output ->
                val buffer = ByteArray(8 * 1024) // 8KB buffer

                while (!channel.isClosedForRead) {
                    val bytesRead = channel.readAvailable(buffer, 0, buffer.size)
                    if (bytesRead == -1) break

                    output.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead

                    // 5. Emit status update (throttled to every ~100ms to save UI resources)
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastEmittedTime > 100 || downloadedBytes == totalBytes) {
                        val status = calculateStatus(
                            downloadedBytes,
                            totalBytes,
                            startTime,
                            currentTime
                        )
                        emit(DownloadState.Downloading(status))
                        lastEmittedTime = currentTime
                    }
                }
            }

            emit(DownloadState.Success(destination))

        } catch (e: CancellationException) {
            // Don't emit error on manual cancel, just clean up
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            emit(DownloadState.Error(e.message ?: "Unknown download error", e))
        }
    }.flowOn(Dispatchers.IO)
     .onStart { currentDownloadJob = currentCoroutineContext().job }
     .onCompletion { currentDownloadJob = null }

    /**
     * Checks if the local file exists and matches the remote file size.
     * Useful for checking "Is model ready?" at app launch.
     */
    suspend fun checkModelStatus(
        repoId: String,
        fileName: String,
        localFile: File,
        apiKey: String? = null
    ): FileValidationResult = withContext(Dispatchers.IO) {
        if (!localFile.exists()) return@withContext FileValidationResult.NotDownloaded

        val url = "https://huggingface.co/$repoId/resolve/main/$fileName"
        try {
            val headResponse = client.head(url) {
                if (!apiKey.isNullOrBlank()) {
                    header("Authorization", "Bearer $apiKey")
                }
            }

            if (!headResponse.status.isSuccess()) {
                return@withContext FileValidationResult.Error("Failed to fetch remote info: ${headResponse.status}")
            }

            val remoteSize = headResponse.contentLength() ?: return@withContext FileValidationResult.Error("Remote size unknown")
            val localSize = localFile.length()

            if (localSize == remoteSize) {
                FileValidationResult.Valid
            } else {
                FileValidationResult.Corrupted(localSize, remoteSize)
            }
        } catch (e: Exception) {
            FileValidationResult.Error(e.message ?: "Network error during check")
        }
    }

    /**
     * Cancel the active download if one exists.
     */
    fun cancelCurrentDownload() {
        currentDownloadJob?.cancel()
        currentDownloadJob = null
    }

    // -------------------------------------------------------------------------
    // Helper Calculation Functions
    // -------------------------------------------------------------------------

    private fun calculateStatus(
        current: Long,
        total: Long?,
        startTime: Long,
        now: Long
    ): DownloadStatus {
        val timeElapsed = (now - startTime).coerceAtLeast(1)
        val speedBytesPerSec = (current / (timeElapsed / 1000.0)).toLong()

        val progress = if (total != null && total > 0) current.toFloat() / total else 0f
        val percent = (progress * 100).roundToInt()

        val etaSeconds = if (speedBytesPerSec > 0 && total != null) {
            (total - current) / speedBytesPerSec
        } else 0L

        return DownloadStatus(
            progress = progress,
            progressPercent = percent,
            speed = formatSpeed(speedBytesPerSec),
            eta = formatDuration(etaSeconds),
            downloadedText = "${formatBytes(current)} / ${if (total != null) formatBytes(total) else "..."}",
            totalBytes = total,
            downloadedBytes = current
        )
    }

    private fun emptyStatus() = DownloadStatus(0f, 0, "0 B/s", "--", "0 MB / ...", null, 0)

    private fun formatBytes(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format("%.2f GB", gb)
            mb >= 1.0 -> String.format("%.2f MB", mb)
            kb >= 1.0 -> String.format("%.0f KB", kb)
            else -> "$bytes B"
        }
    }

    private fun formatSpeed(bytesPerSec: Long): String {
        return "${formatBytes(bytesPerSec)}/s"
    }

    private fun formatDuration(seconds: Long): String {
        if (seconds < 60) return "${seconds}s"
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return "${minutes}m ${remainingSeconds}s"
    }

    suspend fun getRemoteFileSize(url: String): Long {
        return try {
            val response = client.head(url) {
                // Also add User-Agent here for the size check!
                header(HttpHeaders.UserAgent, USER_AGENT_2)
            }
            response.headers[HttpHeaders.ContentLength]?.toLong() ?: -1L
        } catch (e: Exception) {
            Logger.e { "Failed to get file size for $url: ${e.message}" }
            -1L
        }
    }
}