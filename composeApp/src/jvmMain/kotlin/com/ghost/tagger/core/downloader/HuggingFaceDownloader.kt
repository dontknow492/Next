package com.ghost.tagger.core.downloader

import co.touchlab.kermit.Logger
import com.ghost.tagger.data.models.DownloadStatus
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

object HuggingFaceDownloader {
    private const val USER_AGENT = "GhostTaggerApp/1.0 (Kotlin/Ktor)"
    private const val USER_AGENT_2 =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private val client = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = null
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = null
        }
        install(UserAgent) { agent = USER_AGENT }
    }

    // Scope for background downloads
    private val downloaderScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Store both the Job (to cancel) and the Flow (to listen)
    private data class DownloadTask(
        val job: Job,
        val flow: SharedFlow<DownloadState>
    )

    private val activeDownloads = ConcurrentHashMap<String, DownloadTask>()

    /**
     * Starts a download or joins an existing one.
     */
    fun download(
        repoId: String,
        fileName: String,
        destination: File,
        apiKey: String? = null
    ): Flow<DownloadState> {
        val key = "$repoId/$fileName"

        // Return existing flow if active, or create a new one
        val task = activeDownloads.computeIfAbsent(key) {
            val mutableFlow = MutableSharedFlow<DownloadState>(replay = 1)

            // Launch the download in the global scope so it survives UI changes
            val job = downloaderScope.launch {
                try {
                    createDownloadFlow(repoId, fileName, destination, apiKey)
                        .collect { state ->
                            mutableFlow.emit(state)
                            // Auto-cleanup on finish
                            if (state is DownloadState.Success || state is DownloadState.Error) {
                                activeDownloads.remove(key)
                            }
                        }
                } catch (e: CancellationException) {
                    Logger.i { "Download Job Cancelled for $key" }
                    mutableFlow.emit(DownloadState.Error("Cancelled by user"))
                    activeDownloads.remove(key)
                    throw e
                }
            }

            DownloadTask(job, mutableFlow.asSharedFlow())
        }

        return task.flow
    }

    /**
     * Explicitly cancels a running download.
     */
    fun cancelDownload(repoId: String, fileName: String) {
        val key = "$repoId/$fileName"
        Logger.i { "Requesting cancellation for $key" }

        // Remove from map and cancel the job
        activeDownloads.remove(key)?.let { task ->
            task.job.cancel()
        }
    }

    /**
     * Checks if any download is active in the downloader.
     */
    fun isAnyDownloadActive(): Boolean = activeDownloads.isNotEmpty()

    /**
     * Returns the current state of a download if it exists, otherwise null.
     * Useful for checking progress without collecting the whole flow.
     */
    fun getDownloadStatus(repoId: String, fileName: String): DownloadState? {
        return activeDownloads["$repoId/$fileName"]?.flow?.replayCache?.firstOrNull()
    }

    /**
     * Returns a Flow of download state for the specified repository and file.
     * If no active download exists, returns an empty flow.
     */
    fun getFlowDownloadStatus(repoId: String, fileName: String): Flow<DownloadState> {
        return activeDownloads["$repoId/$fileName"]?.flow
            ?: emptyFlow()
    }




    /**
     * Checks if a specific file is currently being downloaded.
     */
    fun isDownloading(repoId: String, fileName: String): Boolean {
        return activeDownloads.containsKey("$repoId/$fileName")
    }

    // -------------------------------------------------------------------------
    // Internal Logic
    // -------------------------------------------------------------------------

    private fun createDownloadFlow(
        repoId: String,
        fileName: String,
        destination: File,
        apiKey: String?
    ): Flow<DownloadState> = channelFlow {
        Logger.i { "Starting network request for: $repoId/$fileName" }
        send(DownloadState.Downloading(emptyStatus()))

        val url = "https://huggingface.co/$repoId/resolve/main/$fileName"

        try {
            val request = client.prepareGet(url) {
                header(HttpHeaders.UserAgent, USER_AGENT_2)
                if (!apiKey.isNullOrBlank()) header("Authorization", "Bearer $apiKey")
            }

            request.execute { response ->
                if (!response.status.isSuccess()) {
                    throw Exception("Server returned ${response.status}")
                }

                val totalBytes = response.contentLength()
                var downloadedBytes = 0L
                val startTime = System.currentTimeMillis()
                var lastEmittedTime = 0L

                val channel: ByteReadChannel = response.bodyAsChannel()

                destination.parentFile?.mkdirs()

                destination.outputStream().use { output ->
                    val buffer = ByteArray(32 * 1024)
                    while (!channel.isClosedForRead) {
                        // Check for cancellation before reading
                        ensureActive()

                        val bytesRead = channel.readAvailable(buffer, 0, buffer.size)
                        if (bytesRead == -1) break

                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastEmittedTime > 100 || downloadedBytes == totalBytes) {
                            val status = calculateStatus("${repoId}/${fileName}",downloadedBytes, totalBytes, startTime, currentTime)
                            send(DownloadState.Downloading(status))
                            lastEmittedTime = currentTime
                        }
                    }
                }
                send(DownloadState.Success(destination))
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e // Don't wrap cancellation
            Logger.e(e) { "Download failed: ${e.message}" }
            send(DownloadState.Error(e.message ?: "Unknown error", e))
        }
    }

    suspend fun checkModelStatus(
        repoId: String,
        fileName: String,
        localFile: File,
        apiKey: String? = null
    ): FileValidationResult = withContext(Dispatchers.IO) {
        if (!localFile.exists()) return@withContext FileValidationResult.NotDownloaded

        // If currently downloading, it's not "Valid" yet
        if (activeDownloads.containsKey("$repoId/$fileName")) {
             // You could return a specific "Downloading" state if your ValidationResult supports it
             return@withContext FileValidationResult.NotDownloaded
        }

        val url = "https://huggingface.co/$repoId/resolve/main/$fileName"
        try {
            val headResponse = client.head(url) {
                header(HttpHeaders.UserAgent, USER_AGENT_2)
                if (!apiKey.isNullOrBlank()) header("Authorization", "Bearer $apiKey")
            }

            if (!headResponse.status.isSuccess()) return@withContext FileValidationResult.Error("Network error")

            val remoteSize = headResponse.contentLength() ?: return@withContext FileValidationResult.Error("Unknown size")
            val localSize = localFile.length()

            if (localSize == remoteSize) FileValidationResult.Valid
            else FileValidationResult.Corrupted(localSize, remoteSize)
        } catch (e: Exception) {
            FileValidationResult.Error(e.message ?: "Error checking status")
        }
    }

    // Helper Calculation Functions
    private fun calculateStatus(id: String, current: Long, total: Long?, startTime: Long, now: Long): DownloadStatus {
        val timeElapsed = (now - startTime).coerceAtLeast(1)
        val speedBytesPerSec = (current / (timeElapsed / 1000.0)).toLong()
        val progress = if (total != null && total > 0) current.toFloat() / total else 0f

        return DownloadStatus(
            id = id,
            progress = progress,
            progressPercent = (progress * 100).roundToInt(),
            speed = formatSpeed(speedBytesPerSec),
            eta = formatDuration(if (speedBytesPerSec > 0 && total != null) (total - current) / speedBytesPerSec else 0L),
            downloadedText = "${formatBytes(current)} / ${if (total != null) formatBytes(total) else "..."}",
            totalBytes = total,
            downloadedBytes = current
        )
    }

    private fun emptyStatus() = DownloadStatus("", 0f, 0, "0 B/s", "--", "0 MB / ...", null, 0)
    private fun formatBytes(bytes: Long) = if (bytes > 1024 * 1024) String.format("%.2f MB", bytes / 1024.0 / 1024.0) else "$bytes B"
    private fun formatSpeed(bytesPerSec: Long) = "${formatBytes(bytesPerSec)}/s"
    private fun formatDuration(seconds: Long) = if (seconds < 60) "${seconds}s" else "${seconds / 60}m ${seconds % 60}s"
}