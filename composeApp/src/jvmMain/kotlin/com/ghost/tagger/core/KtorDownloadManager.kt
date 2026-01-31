package com.ghost.tagger.core

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.* // Crucial for HttpTimeout
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.*
import java.io.File
import com.ghost.tagger.data.models.DownloadStatus

object KtorDownloadManager {

    private val client = HttpClient(CIO) {
        install(HttpTimeout) {
            // "INFINITE" can be ambiguous in some Ktor versions; Long.MAX_VALUE is safer.
            requestTimeoutMillis = Long.MAX_VALUE
            connectTimeoutMillis = 30_000L
            socketTimeoutMillis = Long.MAX_VALUE
        }
    }

    fun download(url: String, outputFile: File): Flow<DownloadStatus> = flow {
        outputFile.parentFile.mkdirs()

        val startTime = System.currentTimeMillis()
        var lastEmitTime = 0L

        client.prepareGet(url) {
            onDownload { bytesRead, totalBytes ->
                // totalBytes is Long? (nullable). If null, we default to 0 for math safety.
                val safeTotalBytes = totalBytes ?: 0L

                val currentTime = System.currentTimeMillis()

                // Throttle updates (emit every 500ms) to save UI resources
                if (currentTime - lastEmitTime > 500) {

                    // 1. Calculate Progress (Guard against divide-by-zero)
                    val progress = if (safeTotalBytes > 0) bytesRead.toFloat() / safeTotalBytes else 0f

                    // 2. Calculate Speed
                    val timeElapsed = currentTime - startTime
                    val speedBytesPerSec = if (timeElapsed > 0) (bytesRead * 1000) / timeElapsed else 0

                    // 3. Calculate ETA
                    val remainingBytes = safeTotalBytes - bytesRead
                    val etaMillis = if (speedBytesPerSec > 0) (remainingBytes * 1000) / speedBytesPerSec else 0

                    emit(
                        DownloadStatus(
                            progress = progress,
                            progressPercent = (progress * 100).toInt(),
                            speed = formatSpeed(speedBytesPerSec),
                            eta = if (safeTotalBytes > 0) formatDuration(etaMillis) else "Calculating...",
                            downloadedText = if (safeTotalBytes > 0) "${formatSize(bytesRead)} / ${formatSize(safeTotalBytes)}" else formatSize(bytesRead),

                            // NEW FIELDS
                            totalBytes = totalBytes, // Pass the original nullable value
                            downloadedBytes = bytesRead
                        )
                    )
                    lastEmitTime = currentTime
                }
            }
        }.execute { httpResponse ->
            // Stream data to file
            val channel: ByteReadChannel = httpResponse.bodyAsChannel()
            val fileStream = outputFile.outputStream()
            channel.copyTo(fileStream)
        }

        // Final "Done" State
        val finalSize = outputFile.length()
        emit(
            DownloadStatus(
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
}