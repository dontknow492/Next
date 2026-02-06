package com.ghost.tagger.core

//import io.ktor.client.engine..*
import co.touchlab.kermit.Logger
import com.ghost.tagger.data.models.DownloadStatus
import io.ktor.client.*
import io.ktor.client.engine.java.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

object KtorDownloadManager {

    val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"


    private val client = HttpClient(Java) {
        install(HttpTimeout) {
            requestTimeoutMillis = Long.MAX_VALUE
            connectTimeoutMillis = 60_000L
            socketTimeoutMillis = Long.MAX_VALUE
        }
        install(HttpRedirect) {
            checkHttpMethod = false
            allowHttpsDowngrade = true
        }
    }

    fun download(url: String, outputFile: File): Flow<DownloadStatus> = flow {
        Logger.i { "Downloading $url" }
        outputFile.parentFile.mkdirs()

        val downloaded = outputFile.length()

        val startTime = System.currentTimeMillis()
        var lastEmitTime = 0L

        // 1. Define a "Bucket" (8 KB chunks)
        // This is the manual way to move water instead of using a hose (copyTo)
        val bufferSize = 8 * 1024
        val buffer = ByteArray(bufferSize)

        client.prepareGet(url) {
            // FIX 1: The "Secret Handshake".
            // Pretend to be a browser so HuggingFace/Cloudfront doesn't block us.
            header(HttpHeaders.UserAgent, USER_AGENT)
            header(HttpHeaders.Accept, "*/*")
            header(HttpHeaders.Connection, "Keep-Alive")
            header(HttpHeaders.AcceptEncoding, "identity")
            header(HttpHeaders.Range, "bytes=$downloaded-")
        }.execute { httpResponse ->

            // Get total size (if available)
            val totalBytes = httpResponse.contentLength() ?: 0L
            var bytesCopiedTotal = 0L

            val channel: ByteReadChannel = httpResponse.bodyAsChannel()

            // FIX 2: Manual Loop
            // Instead of 'copyTo', we read and write manually.
            // This ensures we know EXACTLY when it freezes.
            outputFile.outputStream().use { fileStream ->
                while (!channel.isClosedForRead) {
                    val bytesRead = channel.readAvailable(buffer, 0, buffer.size)
                    Logger.d("Bytes read: $bytesRead")

                    if (bytesRead == -1) break

                    if (bytesRead == 0) {
                        channel.awaitContent() // prevents CloudFront stall
                        continue
                    }

                    fileStream.write(buffer, 0, bytesRead)
                    bytesCopiedTotal += bytesRead
                    val currentTime = System.currentTimeMillis()

                    if (currentTime - lastEmitTime > 500) {
                        val progress = if (totalBytes > 0L) bytesCopiedTotal.toFloat() / totalBytes else 0f
                        val timeElapsed = currentTime - startTime
                        val speedBytesPerSec = if (timeElapsed > 0) (bytesCopiedTotal * 1000) / timeElapsed else 0
                        val remainingBytes = totalBytes - bytesCopiedTotal
                        val etaMillis = if (speedBytesPerSec > 0) (remainingBytes * 1000) / speedBytesPerSec else 0
                        Logger.d(
                            "Progress: $progress, Speed: ${formatSpeed(speedBytesPerSec)}, ETA: ${
                                formatDuration(
                                    etaMillis
                                )
                            }"
                        )
                        emit(
                            DownloadStatus(
                                id = url,
                                progress = progress,
                                progressPercent = (progress * 100).toInt(),
                                speed = formatSpeed(speedBytesPerSec),
                                eta = if (totalBytes > 0L) formatDuration(etaMillis) else "Calculating...",
                                downloadedText = if (totalBytes > 0L) "${formatSize(bytesCopiedTotal)} / ${
                                    formatSize(
                                        totalBytes
                                    )
                                }" else formatSize(bytesCopiedTotal),
                                totalBytes = totalBytes,
                                downloadedBytes = bytesCopiedTotal
                            )
                        )
                        lastEmitTime = currentTime
                    }
                }
            }
        }

        // Final "Done" State
        val finalSize = outputFile.length()
        emit(
            DownloadStatus(
                id = url,
                progress = 1.0f,
                progressPercent = 100,
                speed = "0 MB/s",
                eta = "Done",
                downloadedText = formatSize(finalSize),
                totalBytes = finalSize,
                downloadedBytes = finalSize
            )
        )

    }.flowOn(Dispatchers.IO)

    // --- Formatters ---
    private fun formatSpeed(bytesPerSec: Long): String {
        val mb = bytesPerSec / (1024 * 1024)
        return if (mb >= 1) "$mb MB/s" else "${bytesPerSec / 1024} KB/s"
    }

    private fun formatSize(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1) String.format("%.1f MB", mb) else "${bytes / 1024} KB"
    }

    private fun formatDuration(millis: Long): String {
        val seconds = (millis / 1000) % 60
        val minutes = (millis / (1000 * 60)) % 60
        return if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s"
    }

    suspend fun getRemoteFileSize(url: String): Long {
        return try {
            val response = client.head(url) {
                // Also add User-Agent here for the size check!
                header(
                    HttpHeaders.UserAgent,
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                )
            }
            response.headers[HttpHeaders.ContentLength]?.toLong() ?: -1L
        } catch (e: Exception) {
            Logger.e { "Failed to get file size for $url: ${e.message}" }
            -1L
        }
    }
}