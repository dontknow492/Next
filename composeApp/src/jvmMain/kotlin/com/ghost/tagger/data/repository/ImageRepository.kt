package com.ghost.tagger.data.repository

import com.ghost.tagger.core.MetadataWriterV2
import com.ghost.tagger.data.models.ImageItem
import com.ghost.tagger.data.models.SaveOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

/**
 * The Single Source of Truth for the app's image data.
 * * Responsibilities:
 * 1. Holds the master list of loaded images in memory.
 * 2. Coordinates scanning files via GalleryRepository.
 * 3. Handles updates (tagging, metadata changes) and notifies all observers.
 */
class ImageRepository(
    private val galleryScanner: GalleryRepository,
    private val settingsRepository: SettingsRepository
) {
    //    val settings by settingsRepository.settings.collect {  }
    // A scope that lives as long as the app/repository to keep data alive
    private val repoScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // THE SOURCE OF TRUTH
    // All screens (Gallery, Detail, Batch) should observe this flow.
    private val _images = MutableStateFlow<List<ImageItem>>(emptyList())
    val images = _images.asStateFlow()

    /**
     * Starts a new scan. This clears the current list and progressively appends
     * new chunks of images as they are found by the scanner.
     */
    fun loadImagesFromDirectory(root: File, recursive: Boolean = false, maxDepth: Int = 1) {
        repoScope.launch {
            // 1. Reset state
            _images.value = emptyList()

            // 2. Collect chunks from the scanner
            galleryScanner.getImages(root, recursive, maxDepth).collect { newChunk ->
                // 3. Append new items to the master list safely
                _images.update { currentList ->
                    currentList + newChunk
                }
            }
        }
    }

    /**
     * Smart Refresh:
     * Scans the directory again but only reads metadata for NEW files.
     * Removes files that are no longer on disk.
     */
    fun refresh(root: File, maxDepth: Int = 1) {
        repoScope.launch {
            val currentList = _images.value

            galleryScanner.refreshDirectory(root, currentList, maxDepth).collect { diff ->
                _images.update { current ->
                    // 1. Remove deleted IDs
                    val afterRemovals = if (diff.removedIds.isNotEmpty()) {
                        current.filterNot { it.id in diff.removedIds }
                    } else {
                        current
                    }

                    // 2. Add new items
                    val afterAdditions = if (diff.added.isNotEmpty()) {
                        afterRemovals + diff.added
                    } else {
                        afterRemovals
                    }
                    // 3. Update existing item
                    if (diff.updated.isNotEmpty()) {
                        afterAdditions.map { existing ->
                            diff.updated.find { it.id == existing.id } ?: existing
                        }
                    } else {
                        afterAdditions
                    }
                }
            }
        }
    }

    /**
     * Updates a single image in the master list.
     * Use this when the Detail View modifies a description or adds a tag.
     */
    fun updateImage(updatedItem: ImageItem, disk: Boolean = false, onError: (Exception) -> Unit) {
        _images.update { list ->
            // Efficiently replace the item with the matching ID
            list.map { if (it.id == updatedItem.id) updatedItem else it }
        }
        if (disk) {
            saveMetadataToDisk(updatedItem, onError)
        }
    }


    /**
     * Updates multiple images at once.
     * Use this for Batch Operations to prevent N separate UI refreshes.
     */
    fun updateImages(updatedItems: List<ImageItem>, disk: Boolean = false, onError: (Exception) -> Unit) {
        if (updatedItems.isEmpty()) return

        // Create a map for O(1) lookups during the list traversal
        val updatesMap = updatedItems.associateBy { it.id }

        _images.update { list ->
            list.map { existing ->
                // If we have an update for this ID, use it; otherwise keep existing
                updatesMap[existing.id] ?: existing
            }
        }
        if (disk) {
            updatedItems.forEach { image ->
                saveMetadataToDisk(image, onError)
            }
        }
    }

    /**
     * Helper to get the latest version of an image by ID directly from memory.
     */
    fun getImageById(id: String): ImageItem? {
        return _images.value.find { it.id == id }
    }

    fun observeImageById(id: String): Flow<ImageItem?> {
        return _images.map { list ->
            list.find { it.id == id }
        }.distinctUntilChanged()

    }

    fun observeImagesByIds(ids: Set<String>): Flow<List<ImageItem>> {
        return _images.map { list ->
            list.filter { it.id in ids }
        }.distinctUntilChanged()
    }

    fun clearImages() {
        _images.value = emptyList()
    }

    fun removeImage(id: String) {
        _images.update { list ->
            list.filter { it.id != id }
        }
    }

    fun removeImages(id: Set<String>) {
        _images.update { list ->
            list.filterNot { it.id in id }
        }
    }


    private fun saveMetadataToDisk(image: ImageItem, onError: (Exception) -> Unit) {
        MetadataWriterV2.save(
            file = image.metadata.path,
            metadata = image.metadata,
            options = SaveOptions(
                embedInFile = settingsRepository.settings.value.system.autoSaveToExif,
                createSidecar = settingsRepository.settings.value.system.writeXmp,
                strictFallback = true
            ),
            onError = onError
        )
    }
}