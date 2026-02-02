package com.ghost.tagger.core.downloader

/**
 * Result of the file verification check.
 */
sealed class FileValidationResult {
    data object Valid : FileValidationResult()
    data object NotDownloaded : FileValidationResult()
    data class Corrupted(val localSize: Long, val remoteSize: Long) : FileValidationResult()
    data class Error(val message: String) : FileValidationResult()
}