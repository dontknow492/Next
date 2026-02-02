package com.ghost.tagger.data.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.ghost.tagger.core.openFileInExplorer
import com.ghost.tagger.data.enums.GalleryMode
import com.ghost.tagger.data.enums.SortBy
import com.ghost.tagger.data.enums.SortOrder
import com.ghost.tagger.data.models.ImageItem
import com.ghost.tagger.data.models.settings.DirectorySettings
import com.ghost.tagger.data.repository.GalleryRepository
import com.ghost.tagger.data.repository.SettingsRepository
import com.ghost.tagger.ui.state.GalleryUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class GalleryViewModel(
    private val repository: GalleryRepository,
    private val settingsRepo: SettingsRepository // To read "Recursive" setting
) : ViewModel() {

    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState = _uiState.asStateFlow()

    private val allLoadedImages = mutableListOf<ImageItem>()

    private var currentSearchQuery: String = ""

    // Internal tracker for Shift+Click range selection
    private var lastSelectionAnchorId: String? = null


    init {
        // 1. Start listening immediately when ViewModel is created
        viewModelScope.launch {
            settingsRepo.settings.collect { settings ->
                // 2. Whenever settings change (or on first load), update UI state
                _uiState.update { currentState ->
                    currentState.copy(
                        viewMode = settings.session.galleryViewMode,
                        minThumbnailSizeDp = settings.session.minThumbnailSizeDp,
                        advanceDirSettings = DirectorySettings(
                            isRecursive = settings.session.recursiveLoad,
                            maxDepth = settings.session.maxRecursionDepth,
                            includeHiddenFiles = settings.session.includeHiddenFiles
                        )
                    )
                }
            }
        }
    }

    // ==========================================================
    // 1. LOADING & NAVIGATION
    // ==========================================================

    fun loadDirectory(path: String) {
        val isRecursive = settingsRepo.settings.value.session.recursiveLoad

        viewModelScope.launch {
            // Reset everything
            allLoadedImages.clear()
            _uiState.update { it.copy(isLoading = true, currentDirectory = path, images = emptyList()) }

            repository.getImages(path, isRecursive).collect { newBatch ->
                // A. Add to Master List
                allLoadedImages.addAll(newBatch)

                // B. Apply Filter immediately
                // If user is searching "png", new PNGs will appear, but JPGs stay hidden in master list.
                updateUiList()

                // C. Update Status
                _uiState.update { it.copy(statusMessage = "Loaded ${allLoadedImages.size} images...") }
            }

            _uiState.update { it.copy(isLoading = false, statusMessage = null) }
        }
    }

    private fun applySort(list: List<ImageItem>, by: SortBy, order: SortOrder): List<ImageItem> {
        return when(order) {
            SortOrder.ASCENDING -> when(by) {
                SortBy.NAME -> list.sortedBy { it.name }
                SortBy.DATE -> list.sortedBy { it.metadata.lastModified }
                SortBy.SIZE -> list.sortedBy { it.metadata.fileSizeBytes }
            }
            SortOrder.DESCENDING -> when(by) {
                SortBy.NAME -> list.sortedByDescending { it.name }
                SortBy.DATE -> list.sortedByDescending { it.metadata.lastModified }
                SortBy.SIZE -> list.sortedByDescending { it.metadata.fileSizeBytes }
            }
        }
    }


    /**
     * The Central Logic: Master List + Query -> UI List
     */
    private fun updateUiList() {
        val filteredList = if (currentSearchQuery.isBlank()) {
            // No search? Show everything.
            allLoadedImages.toList()
        } else {
            // Search active? Filter the master list.
            val lowerQuery = currentSearchQuery.lowercase()
            allLoadedImages.filter { item ->
                // Search by Name
                item.name.lowercase().contains(lowerQuery) ||
                // OR Search by Extension
                item.metadata.extension.lowercase().contains(lowerQuery) ||
                // OR Search by Tags (Advanced)
                item.metadata.tags.any { tag -> tag.name.lowercase().contains(lowerQuery) }
            }
        }

        // Push to UI
        _uiState.update { it.copy(images = filteredList, searchQuery = currentSearchQuery) }
    }

    // ==========================================================
    // SEARCH LOGIC
    // ==========================================================

    fun onSearch(query: String) {
        currentSearchQuery = query
        updateUiList() // Re-run the filter on the existing master list
    }

    fun clearGallery() {
        allLoadedImages.clear()
        lastSelectionAnchorId = null
        currentSearchQuery = ""
        updateUiList()
        _uiState.update {
            it.copy(
                selectedIds = emptySet(),
                currentDirectory = null,
                focusedImageId = null,
                isLoading = false,
                statusMessage = null
            )
        }
    }

    // ==========================================================
    // 2. SELECTION LOGIC (The Complex Part)
    // ==========================================================

    fun toggleSelection(id: String, isMultiSelect: Boolean, isRangeSelect: Boolean) {
        val currentIds = _uiState.value.selectedIds.toMutableSet()

        print("Click: $id, ctrl: $isMultiSelect, shift: $isRangeSelect")

        when {
            // CASE A: Shift+Click (Range Selection)
            isRangeSelect && lastSelectionAnchorId != null -> {
                val allImages = _uiState.value.images
                val start = allImages.indexOfFirst { it.id == lastSelectionAnchorId }
                val end = allImages.indexOfFirst { it.id == id }

                if (start != -1 && end != -1) {
                    val range = if (start < end) start..end else end..start
                    // Add all IDs in that range
                    for (i in range) {
                        currentIds.add(allImages[i].id)
                    }
                }
            }

            // CASE B: Ctrl+Click (Toggle One)
            isMultiSelect -> {
                if (currentIds.contains(id)) currentIds.remove(id) else currentIds.add(id)
                lastSelectionAnchorId = id // Update anchor
            }

            // CASE C: Single Click (Reset and Select One)
            else -> {
                currentIds.clear()
//                _uiState.update { it.copy(focusedImageId = id) }
//                currentIds.add(id)
                lastSelectionAnchorId = null
            }
        }

        // Apply Update
        _uiState.update { it.copy(selectedIds = currentIds) }

        // Also update focus to the clicked item
        focusImage(id)
    }



    fun selectAll() {
        val allIds = _uiState.value.images.map { it.id }.toSet()
        _uiState.update { it.copy(selectedIds = allIds) }
    }

    fun invertSelection() {
        val allIds = _uiState.value.images.map { it.id }.toSet()
        val currentIds = _uiState.value.selectedIds

        // "All minus Current" = Inverted
        val newSelection = allIds - currentIds
        _uiState.update { it.copy(selectedIds = newSelection) }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedIds = emptySet()) }
    }

    // ==========================================================
    // 3. FOCUS & MODES
    // ==========================================================

    fun focusImage(id: String) {
        _uiState.update { it.copy(focusedImageId = id) }
        // Note: You can also verify if the file still exists here
    }

    fun setViewMode(mode: GalleryMode) {
        viewModelScope.launch {
            settingsRepo.updateSettings { it.copy(session = it.session.copy(galleryViewMode = mode)) }
        }
    }

    fun onMove(from: Int, to: Int) {
        _uiState.update { current ->
            val mutableImages = current.images.toMutableList()

            // Safety check: prevent crashing if indexes are weird (multithreading bugs)
            if (from in mutableImages.indices && to in mutableImages.indices) {
                val item = mutableImages.removeAt(from)
                mutableImages.add(to, item)
            }

            current.copy(images = mutableImages)

        }
        Logger.d(tag = "GalleryViewModel: OnMove", messageString = "Moving $from to $to")
    }

    fun openInExplorer(path: String) {
        val file = java.io.File(path)
        viewModelScope.launch {
            openFileInExplorer(file)
        }

    }

    fun removeImage(id: String) {
        // 1. Remove from the Source of Truth (Master List)
        allLoadedImages.removeAll { it.id == id }

        // 2. Sync the UI State
        _uiState.update { current ->
            // Filter the UI list as well
            val newImages = current.images.filter { it.id != id }

            // Cleanup selection and focus
            val newSelected = current.selectedIds - id
            val newFocused = if (current.focusedImageId == id) null else current.focusedImageId

            // Reset anchor if it was the one deleted
            if (lastSelectionAnchorId == id) lastSelectionAnchorId = null

            updateUiList()
            current.copy(
                selectedIds = newSelected,
                focusedImageId = newFocused
            )
        }
    }

    /**
     * Removes all currently selected images from the Master List and the UI State.
     */
    fun removeSelectedImages() {
        val idsToRemove = _uiState.value.selectedIds
        if (idsToRemove.isEmpty()) return

        // 1. Batch remove from Master List
        allLoadedImages.removeAll { it.id in idsToRemove }

        updateUiList()

        // 2. Sync the UI State
        _uiState.update { current ->
            current.copy(
                // Filter out everything that was in the selection set
                selectedIds = emptySet(), // Selection is now gone
                // If the focused image was among the deleted ones, clear focus
                focusedImageId = if (current.focusedImageId in idsToRemove) null else current.focusedImageId
            )
        }

        // Clear the anchor since those items no longer exist
        lastSelectionAnchorId = null
    }

    private fun updateSort(sortBy: SortBy, sortOrder: SortOrder){
        val sorted_list = applySort(allLoadedImages, sortBy, sortOrder)
        allLoadedImages.clear()
        allLoadedImages.addAll(sorted_list)
        updateUiList()
        _uiState.update { it.copy(sortBy = sortBy, sortOrder = sortOrder) }
    }

    fun onSortByChange(sortBy: SortBy) {
        updateSort(sortBy, uiState.value.sortOrder)
    }

    fun onSortOrderChange(sortOrder: SortOrder) {
        updateSort(uiState.value.sortBy, sortOrder)
    }

    private fun setThumbnailSize(newSize: Float){
        settingsRepo.updateSettings { it.copy(session = it.session.copy(minThumbnailSizeDp = newSize)) }
    }

    fun modifyThumbnailSize(increment: Boolean, byPercentage: Float = 0.1f){
        val current = uiState.value.minThumbnailSizeDp
        val newSize = if (increment) current + (current * byPercentage) else current - (current * byPercentage)
        setThumbnailSize(newSize)
    }

    fun updateAdvanceFolderSettings(settings: DirectorySettings){
        settingsRepo.updateSettings {
            it.copy(
                session = it.session.copy(
                    recursiveLoad = settings.isRecursive,
                    maxRecursionDepth = settings.maxDepth,
                    includeHiddenFiles = settings.includeHiddenFiles
                )
            )
        }
    }



}