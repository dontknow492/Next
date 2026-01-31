package com.ghost.tagger.data.repository


import com.ghost.tagger.core.MetadataReader
import com.ghost.tagger.data.models.ImageItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

class GalleryRepository {

    /**
     * Scans a directory and emits lists of images as they are found.
     * Using Flow allows the UI to start showing images immediately,
     * rather than waiting for 10,000 images to finish loading.
     */
    fun getImages(rootPath: String, recursive: Boolean): Flow<List<ImageItem>> = flow {
        val root = File(rootPath)
        if (!root.exists() || !root.isDirectory) return@flow

        val buffer = mutableListOf<ImageItem>()
        val CHUNK_SIZE = 50 // Emit updates every 50 images to keep UI responsive

        // Recursive walker
        root.walkTopDown()
            .maxDepth(if (recursive) 10 else 1) // Guardrail against deep system folders
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
}