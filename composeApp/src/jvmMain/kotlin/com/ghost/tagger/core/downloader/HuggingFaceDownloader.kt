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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.io.File
import kotlin.math.roundToInt


object HuggingFaceDownloader {
    private const val USER_AGENT = "GhostTaggerApp/1.0 (Kotlin/Ktor)"
    private const val USER_AGENT_2 =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

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

//    private val downloaderScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Downloads a model from Hugging Face with progress tracking.
     * Cancels any existing download automatically.
     */
    fun download(
        repoId: String,
        fileName: String,
        destination: File,
        apiKey: String? = null
    ): Flow<DownloadState> = channelFlow {
        // 1. Cancel previous downloads and emit initial state
        Logger.i { "Requested download: repoId=$repoId fileName=$fileName destination=${destination.absolutePath}" }

        send(DownloadState.Downloading(emptyStatus()))

        val url = "https://huggingface.co/$repoId/resolve/main/$fileName"
        Logger.i { "Resolved download URL: $url" }

        try {
            // 2. Prepare and execute the request
            Logger.d { "Preparing HTTP GET request for $url" }
            val request = client.prepareGet(url) {
                header(HttpHeaders.UserAgent, USER_AGENT_2)
                if (!apiKey.isNullOrBlank()) {
                    header("Authorization", "Bearer $apiKey")
                    Logger.d { "Added Authorization header for download" }
                }
            }

            Logger.d { "Executing HTTP request for $url" }
            val response = request.execute() { response ->
                Logger.i { "Received HTTP response: ${response.status} for $url" }

                if (!response.status.isSuccess()) {
                    val bodySnippet = response.bodyAsText().take(200)
                    Logger.e { "Non-success response ${response.status} for $url, bodySnippet=${bodySnippet}" }
                    throw Exception("Server returned ${response.status}: $bodySnippet")
                }

                // 3. Setup tracking variables
                val totalBytes = response.contentLength()
                Logger.i { "Remote content length for $url is ${totalBytes ?: "unknown"}" }
                var downloadedBytes = 0L
                val startTime = System.currentTimeMillis()
                var lastEmittedTime = 0L

                // 4. Create stream and write file
                val channel: ByteReadChannel = response.bodyAsChannel()
                Logger.d { "Opened response channel for $url" }

                destination.parentFile?.let {
                    if (!it.exists()) {
                        val created = it.mkdirs()
                        Logger.d { "Ensured parent directories for ${destination.absolutePath}, created=$created" }
                    }
                }

                destination.outputStream().use { output ->
                    val buffer = ByteArray(32 * 1024) // 32KB buffer
                    Logger.i { "Begin streaming to ${destination.absolutePath}" }

                    while (!channel.isClosedForRead) {
                        val bytesRead = try {
                            channel.readAvailable(buffer, 0, buffer.size)
                        } catch (ce: CancellationException) {
                            Logger.w { "Read cancelled for $url during streaming: ${ce.message}" }
                            throw ce
                        }

                        if (bytesRead == -1) {
                            Logger.d { "Reached end-of-stream for $url" }
                            break
                        }

                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        // 5. Emit status update (throttled to every ~100ms to save UI resources)
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastEmittedTime > 100 || (totalBytes != null && downloadedBytes == totalBytes)) {
                            val status = calculateStatus(
                                downloadedBytes,
                                totalBytes,
                                startTime,
                                currentTime
                            )
                            send(DownloadState.Downloading(status))
                            Logger.d {
                                "Progress update for $url: ${status.progressPercent}% " +
                                        "(${status.downloadedBytes}/${status.totalBytes ?: -1}) speed=${status.speed} eta=${status.eta}"
                            }
                            lastEmittedTime = currentTime
                            yield()
                        }
                    }
                }

                Logger.i { "Finished writing file to ${destination.absolutePath} for $url (downloadedBytes=$downloadedBytes)" }
                send(DownloadState.Success(destination))
            }


//            val response = client.get(url) {
//                header(HttpHeaders.UserAgent, USER_AGENT_2)
//                if (!apiKey.isNullOrBlank()) {
//                    header("Authorization", "Bearer $apiKey")
//                }
//            }


//            val response = request.execute()


        } catch (e: CancellationException) {
            // Don't emit error on manual cancel, just log and rethrow so callers can handle it
            Logger.i { "Download cancelled for $url" }
            throw e
        } catch (e: Exception) {
            Logger.e(e) { "Download failed for $url: ${e.message}" }
            e.printStackTrace()
            send(DownloadState.Error(e.message ?: "Unknown download error", e))
        }
    }
        .flowOn(Dispatchers.IO)
        .buffer(capacity = Channel.UNLIMITED)
        .onStart {
            if (currentDownloadJob?.isActive == true) {
                Logger.i { "Cancelling existing download job: $currentDownloadJob" }
            }
            cancelCurrentDownload()
            currentDownloadJob = currentCoroutineContext().job
            Logger.i { "Download job started: $currentDownloadJob for ${repoId}/$fileName" }
        }
        .onCompletion { cause ->
            if (cause == null) {
                Logger.i { "Download job completed successfully for ${repoId}/$fileName" }
            } else if (cause is CancellationException) {
                Logger.i { "Download job cancelled for ${repoId}/$fileName" }
            } else {
                Logger.w(cause) { "Download job completed with error for ${repoId}/$fileName: ${cause.message}" }
            }

            // Only clear if it's the current job (avoid clearing a new job that started)
            if (currentDownloadJob === currentCoroutineContext().job) {
                currentDownloadJob = null
            }
        }


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
                header(HttpHeaders.UserAgent, USER_AGENT_2)
                if (!apiKey.isNullOrBlank()) {
                    header("Authorization", "Bearer $apiKey")
                }
            }

            if (!headResponse.status.isSuccess()) {
                return@withContext FileValidationResult.Error("Failed to fetch remote info: ${headResponse.status}")
            }

            val remoteSize =
                headResponse.contentLength() ?: return@withContext FileValidationResult.Error("Remote size unknown")
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