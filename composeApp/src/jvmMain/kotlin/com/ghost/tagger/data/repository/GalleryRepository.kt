package com.ghost.tagger.data.repository


import co.touchlab.kermit.Logger
import com.ghost.tagger.core.MetadataReader
import com.ghost.tagger.data.models.ImageItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import kotlinx.io.IOException

/**
 * Result object for a refresh operation.
 * @param added Newly found images with metadata loaded.
 * @param removedIds IDs (paths) of images that no longer exist on disk.
 */
data class ScanDiff(
    val added: List<ImageItem> = emptyList(),
    val updated: List<ImageItem> = emptyList(),
    val removedIds: Set<String> = emptySet()
)

class GalleryRepository {

    /**
     * Scans a directory and emits lists of images as they are found.
     * Using Flow allows the UI to start showing images immediately,
     * rather than waiting for 10,000 images to finish loading.
     */
    val CHUNK_SIZE = 50

    fun getImages(root: File, recursive: Boolean, maxRecursionDepth: Int): Flow<List<ImageItem>> = flow {
        try {
            if (!root.exists()) {
                throw IOException("Directory does not exist: ${root.absolutePath}")
            }
            if (!root.isDirectory) {
                throw IOException("Path is not a directory: ${root.absolutePath}")
            }

            val buffer = mutableListOf<ImageItem>()

            root.walkTopDown()
                .maxDepth(if (recursive) maxRecursionDepth else 1)
                .onEnter { directory ->
                    // Optional: Check permissions per folder if needed
                    directory.canRead()
                }
                .filter { it.isFile && MetadataReader.isSupported(it) }
                .forEach { file ->
                    val meta = MetadataReader.read(file)
                    val item = ImageItem(
                        id = file.path,
                        name = file.name,
                        metadata = meta,
                    )

                    buffer.add(item)

                    if (buffer.size >= CHUNK_SIZE) {
                        emit(buffer.toList())
                        buffer.clear()
                    }
                }

            if (buffer.isNotEmpty()) {
                emit(buffer.toList())
                buffer.clear()
            }
        } catch (e: SecurityException) {
            Logger.e(e) { "Permission denied while scanning: ${root.path}" }
            throw e // Re-throw so ViewModel can catch it
        } catch (e: Exception) {
            Logger.e(e) { "Error scanning directory: ${root.path}" }
            throw e // Re-throw
        }
    }.flowOn(Dispatchers.IO)
    /**
     * Smart Refresh:
     * 1. Walks the directory.
     * 2. If a file is already in [currentImages], it is skipped (Metadata read avoided).
     * 3. If a file is new, it is read and emitted in [ScanDiff.added].
     * 4. At the end, any image in [currentImages] not found on disk is emitted in [ScanDiff.removedIds].
     */
    fun refreshDirectory(
        root: File,
        currentImages: List<ImageItem>,
        maxRecursionDepth: Int = 1
    ): Flow<ScanDiff> = flow {
        try {
            if (!root.exists() || !root.isDirectory) {
                throw IOException("Target directory is invalid or missing.")
            }

            val currentMap = currentImages.associateBy { it.id }
            val foundPaths = mutableSetOf<String>()
            val addedBuffer = mutableListOf<ImageItem>()
            val updatedBuffer = mutableListOf<ImageItem>()

            root.walkTopDown()
                .maxDepth(maxRecursionDepth)
                .filter { it.isFile && MetadataReader.isSupported(it) }
                .forEach { file ->
                    val path = file.path
                    foundPaths.add(path)

                    val existingItem = currentMap[path]
                    val lastModified = file.lastModified()

                    val isNew = existingItem == null
                    val isModified = existingItem != null && existingItem.metadata.lastModified != lastModified

                    if (isNew || isModified) {
                        val meta = MetadataReader.read(file)
                        val newItem = ImageItem(
                            id = path,
                            name = file.name,
                            metadata = meta.copy(lastModified = lastModified),
                        )

                        if (isNew) addedBuffer.add(newItem) else updatedBuffer.add(newItem)

                        if (addedBuffer.size + updatedBuffer.size >= CHUNK_SIZE) {
                            emit(ScanDiff(added = addedBuffer.toList(), updated = updatedBuffer.toList()))
                            addedBuffer.clear()
                            updatedBuffer.clear()
                        }
                    }
                }

            if (addedBuffer.isNotEmpty() || updatedBuffer.isNotEmpty()) {
                emit(ScanDiff(added = addedBuffer.toList(), updated = updatedBuffer.toList()))
            }

            val removed = currentMap.keys - foundPaths
            if (removed.isNotEmpty()) {
                emit(ScanDiff(removedIds = removed))
            }

            Logger.i("Refreshed Successfully")
        } catch (e: Exception) {
            Logger.e(e) { "Failed to refresh directory" }
            throw e
        }
    }.flowOn(Dispatchers.IO)
}