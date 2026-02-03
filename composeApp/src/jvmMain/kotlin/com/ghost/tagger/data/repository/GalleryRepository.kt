package com.ghost.tagger.data.repository


import com.ghost.tagger.core.MetadataReader
import com.ghost.tagger.data.models.ImageItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

/**
 * Result object for a refresh operation.
 * @param added Newly found images with metadata loaded.
 * @param removedIds IDs (paths) of images that no longer exist on disk.
 */
data class ScanDiff(
    val added: List<ImageItem> = emptyList(),
    val removedIds: Set<String> = emptySet()
)

class GalleryRepository {

    /**
     * Scans a directory and emits lists of images as they are found.
     * Using Flow allows the UI to start showing images immediately,
     * rather than waiting for 10,000 images to finish loading.
     */
    fun getImages(root: File, recursive: Boolean, maxRecursionDepth: Int): Flow<List<ImageItem>> = flow {
        if (!root.exists() || !root.isDirectory) return@flow

        val buffer = mutableListOf<ImageItem>()
        val CHUNK_SIZE = 50 // Emit updates every 50 images to keep UI responsive

        // Recursive walker
        root.walkTopDown()
            .maxDepth(if (recursive) maxRecursionDepth else 1) // Guardrail against deep system folders
            .filter { it.isFile && MetadataReader.isSupported(it) }
            .forEach { file ->

                // Read metadata (Disk I/O)
                val meta = MetadataReader.read(file)

                val item = ImageItem(
                    id = file.path, // Unique ID for Selection
                    name = file.name,
                    metadata = meta,
                )

                buffer.add(item)

                // If buffer is full, emit a copy and continue
                if (buffer.size >= CHUNK_SIZE) {
                    emit(buffer.toList())
                    buffer.clear()
                }
            }

        // Emit remaining items
        if (buffer.isNotEmpty()) {
            emit(buffer.toList())
            buffer.clear() // Good practice to clear even at the end
        }
    }.flowOn(Dispatchers.IO) // Run everything on the Background Thread

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
        if (!root.exists() || !root.isDirectory) return@flow

        // 1. Create a quick lookup set for existing paths
        val existingPaths = currentImages.map { it.id }.toSet()
        val foundPaths = mutableSetOf<String>()

        val addedBuffer = mutableListOf<ImageItem>()
        val CHUNK_SIZE = 50

        // 2. Walk and Find New Files
        root.walkTopDown()
            .maxDepth(maxRecursionDepth)
            .filter { it.isFile && MetadataReader.isSupported(it) }
            .forEach { file ->
                val path = file.path
                foundPaths.add(path) // Mark as found

                // Only read metadata if we DON'T have it already
                if (path !in existingPaths) {
                    val meta = MetadataReader.read(file)
                    val item = ImageItem(
                        id = path,
                        name = file.name,
                        metadata = meta,
                    )
                    addedBuffer.add(item)

                    if (addedBuffer.size >= CHUNK_SIZE) {
                        emit(ScanDiff(added = addedBuffer.toList()))
                        addedBuffer.clear()
                    }
                }
            }

        // Emit remaining added files
        if (addedBuffer.isNotEmpty()) {
            emit(ScanDiff(added = addedBuffer.toList()))
        }

        // 3. Calculate Removed Files (Existing - Found)
        // We can only know this after the walk completes
        val removed = existingPaths - foundPaths
        if (removed.isNotEmpty()) {
            emit(ScanDiff(removedIds = removed))
        }

    }.flowOn(Dispatchers.IO)
}