package com.ghost.tagger.ui.viewmodels

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
import com.ghost.tagger.data.repository.ImageRepository
import com.ghost.tagger.data.repository.SettingsRepository
import com.ghost.tagger.ui.state.GalleryUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.io.IOException
import java.io.File

class GalleryViewModelV1(
    private val repository: GalleryRepository,
    private val settingsRepo: SettingsRepository // To read "Recursive" setting
) : ViewModel() {

    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState = _uiState.asStateFlow()

    private val allLoadedImages = mutableListOf<ImageItem>()

    private var currentSearchQuery: String = ""

    // Internal tracker for Shift+Click range selection
    private var lastSelectionAnchorId: String? = null

    private var loadJob: Job? = null


    init {
        // Observe settings → update UI state only
        viewModelScope.launch {
            settingsRepo.settings.collect { settings ->
                _uiState.update {
                    it.copy(
                        viewMode = settings.session.galleryViewMode,
                        minThumbnailSizeDp = settings.session.minThumbnailSizeDp,
                        currentDirectory = settings.session.lastDirectory,
                        advanceDirSettings = DirectorySettings(
                            isRecursive = settings.session.recursiveLoad,
                            maxDepth = settings.session.maxRecursionDepth,
                            includeHiddenFiles = settings.session.includeHiddenFiles
                        )
                    )
                }
            }
        }

        // Restore last directory ONCE
        viewModelScope.launch {
            val lastDir = settingsRepo.settings
                .map { it.session.lastDirectory }
                .first()

            lastDir?.let { loadImages(it) }
        }
    }

    // ==========================================================
    // 1. LOADING & NAVIGATION
    // ==========================================================

    fun loadDirectory(path: File) {
        viewModelScope.launch {
            // Persist selection
            settingsRepo.updateSettings {
                it.copy(session = it.session.copy(lastDirectory = path))
            }

            // Load images
            loadImages(path)
        }
    }

    fun refresh() {
        val dir = uiState.value.currentDirectory ?: return
        loadImages(dir)
    }


    private fun loadImages(directory: File) {
        val isRecursive = settingsRepo.settings.value.session.recursiveLoad
        val maxDepth = settingsRepo.settings.value.session.maxRecursionDepth

        // Cancel previous load
        loadJob?.cancel()

        loadJob = viewModelScope.launch {
            allLoadedImages.clear()
            _uiState.update { it.copy(isLoading = true, images = emptyList()) }

            repository.getImages(directory, isRecursive, maxDepth).collect { newBatch ->
                allLoadedImages.addAll(newBatch)
                updateUiList()

                _uiState.update {
                    it.copy(statusMessage = "Loaded ${allLoadedImages.size} images…")
                }
            }

            _uiState.update { it.copy(isLoading = false, statusMessage = null) }
        }
    }

    private fun applySort(list: List<ImageItem>, by: SortBy, order: SortOrder): List<ImageItem> {
        return when (order) {
            SortOrder.ASCENDING -> when (by) {
                SortBy.NAME -> list.sortedBy { it.name }
                SortBy.DATE -> list.sortedBy { it.metadata.lastModified }
                SortBy.SIZE -> list.sortedBy { it.metadata.fileSizeBytes }
            }

            SortOrder.DESCENDING -> when (by) {
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
                focusedImageId = null,
                isLoading = false,
                statusMessage = null
            )
        }
        settingsRepo.updateSettings { it.copy(session = it.session.copy(lastDirectory = null)) }
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
        val file = File(path)
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
            current.images.filter { it.id != id }

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

    private fun updateSort(sortBy: SortBy, sortOrder: SortOrder) {
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

    private fun setThumbnailSize(newSize: Float) {
        settingsRepo.updateSettings { it.copy(session = it.session.copy(minThumbnailSizeDp = newSize)) }
    }

    fun modifyThumbnailSize(increment: Boolean, byPercentage: Float = 0.1f) {
        val current = uiState.value.minThumbnailSizeDp
        val newSize = if (increment) current + (current * byPercentage) else current - (current * byPercentage)
        setThumbnailSize(newSize)
    }

    fun updateAdvanceFolderSettings(settings: DirectorySettings) {
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

    fun getSelectedImages(): List<ImageItem> {
        return _uiState.value.images.filter { it.id in _uiState.value.selectedIds }
    }


}


class GalleryViewModel(
    private val imageRepository: ImageRepository,
    private val settingsRepo: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState = _uiState.asStateFlow()

    private var currentSearchQuery: String = ""

    // Internal tracker for Shift+Click range selection
    private var lastSelectionAnchorId: String? = null

    init {
        // 1. Observe Settings
        viewModelScope.launch {
            settingsRepo.settings.collect { settings ->
                _uiState.update {
                    it.copy(
                        viewMode = settings.session.galleryViewMode,
                        minThumbnailSizeDp = settings.session.minThumbnailSizeDp,
                        currentDirectory = settings.session.lastDirectory,
                        advanceDirSettings = DirectorySettings(
                            isRecursive = settings.session.recursiveLoad,
                            maxDepth = settings.session.maxRecursionDepth,
                            includeHiddenFiles = settings.session.includeHiddenFiles
                        )
                    )
                }
            }
        }

        // 2. Observe Repository (The Source of Truth)
        viewModelScope.launch {
            imageRepository.images.collect { newImages ->

                // If the list was empty and now isn't, stop loading
                if (newImages.isNotEmpty() && _uiState.value.isLoading) {
                    _uiState.update { it.copy(isLoading = false) }
                }

                // Apply current filters and sort to the new data
                updateUiList()

                // Update status count
                if (newImages.isNotEmpty()) {
                    _uiState.update { it.copy(statusMessage = "Loaded ${newImages.size} images") }
                }
            }
        }

        // 3. Restore last directory
        viewModelScope.launch {
            val lastDir = settingsRepo.settings
                .map { it.session.lastDirectory }
                .first()

            lastDir?.let { loadImages(it) }
        }
    }

    // ==========================================================
    // 1. LOADING & NAVIGATION
    // ==========================================================

    fun loadDirectory(path: File) {
        viewModelScope.launch {
            // Persist selection
            settingsRepo.updateSettings {
                it.copy(session = it.session.copy(lastDirectory = path))
            }
            loadImages(path)
        }
    }

    fun refresh() {
        val dir = uiState.value.currentDirectory ?: return
        val maxDepth = settingsRepo.settings.value.session.maxRecursionDepth
        // Delegate smart refresh to the repository
        try{
            imageRepository.refresh(dir, maxDepth)
        }catch (error: IOException){
            onError(error)
        }catch (error: Exception){
            onError(error)
        }

    }

    private fun loadImages(directory: File) {
        val isRecursive = settingsRepo.settings.value.session.recursiveLoad
        val maxDepth = settingsRepo.settings.value.session.maxRecursionDepth

        _uiState.update { it.copy(isLoading = true, images = emptyList(), statusMessage = "Scanning...") }

        // Repository handles the background threading and state updates
        try{
            imageRepository.loadImagesFromDirectory(directory, isRecursive, maxDepth)
        }catch (error: IOException){
            onError(error)
        }catch (error: Exception) {
            onError(error)
        }

    }

    // ==========================================================
    // SORTING & FILTERING
    // ==========================================================

    private fun applySort(list: List<ImageItem>, by: SortBy, order: SortOrder): List<ImageItem> {
        return when (order) {
            SortOrder.ASCENDING -> when (by) {
                SortBy.NAME -> list.sortedBy { it.name }
                SortBy.DATE -> list.sortedBy { it.metadata.lastModified }
                SortBy.SIZE -> list.sortedBy { it.metadata.fileSizeBytes }
            }

            SortOrder.DESCENDING -> when (by) {
                SortBy.NAME -> list.sortedByDescending { it.name }
                SortBy.DATE -> list.sortedByDescending { it.metadata.lastModified }
                SortBy.SIZE -> list.sortedByDescending { it.metadata.fileSizeBytes }
            }
        }
    }

    /**
     * The Central Logic: Raw Repo List + Query + Sort -> UI List
     */
    private fun updateUiList() {
        // 1. Get Master List directly from Repo Source of Truth
        val masterList = imageRepository.images.value

        // 2. Filter
        val filteredList = if (currentSearchQuery.isBlank()) {
            masterList
        } else {
            val lowerQuery = currentSearchQuery.lowercase()
            masterList.filter { item ->
                item.name.lowercase().contains(lowerQuery) ||
                        item.metadata.extension.lowercase().contains(lowerQuery) ||
                        item.metadata.tags.any { tag -> tag.name.lowercase().contains(lowerQuery) }
            }
        }

        // 3. Sort
        val sortedList = applySort(filteredList, _uiState.value.sortBy, _uiState.value.sortOrder)

        // 4. Push to UI
        _uiState.update { it.copy(images = sortedList, searchQuery = currentSearchQuery) }
    }

    // ==========================================================
    // SEARCH LOGIC
    // ==========================================================

    fun onSearch(query: String) {
        currentSearchQuery = query
        updateUiList()
    }

    fun clearGallery() {
        // Since we are driven by the repo, we should ideally clear the repo or just the current view.
        // For now, we clear the session directory which stops the repo from loading next time,
        // and we can ask the repo to clear.
        // NOTE: For now, we just reset UI state, but data persists in Repo until new load.
        lastSelectionAnchorId = null
        currentSearchQuery = ""

        settingsRepo.updateSettings { it.copy(session = it.session.copy(lastDirectory = null)) }

        imageRepository.clearImages()

        // Ideally: imageRepository.clear()
        _uiState.update {
            it.copy(
                selectedIds = emptySet(),
                focusedImageId = null,
                isLoading = false,
                statusMessage = null,
            )
        }
    }

    // ==========================================================
    // 2. SELECTION LOGIC
    // ==========================================================

    fun toggleSelection(id: String, isMultiSelect: Boolean, isRangeSelect: Boolean) {
        val currentIds = _uiState.value.selectedIds.toMutableSet()

        when {
            // CASE A: Shift+Click (Range Selection)
            isRangeSelect && lastSelectionAnchorId != null -> {
                val visibleImages = _uiState.value.images // Select from visible/sorted list
                val start = visibleImages.indexOfFirst { it.id == lastSelectionAnchorId }
                val end = visibleImages.indexOfFirst { it.id == id }

                if (start != -1 && end != -1) {
                    val range = if (start < end) start..end else end..start
                    for (i in range) {
                        currentIds.add(visibleImages[i].id)
                    }
                }
            }
            // CASE B: Ctrl+Click (Toggle One)
            isMultiSelect -> {
                if (currentIds.contains(id)) currentIds.remove(id) else currentIds.add(id)
                lastSelectionAnchorId = id
            }
            // CASE C: Single Click (Reset and Select One)
            else -> {
                currentIds.clear()
                lastSelectionAnchorId = id
            }
        }

        _uiState.update { it.copy(selectedIds = currentIds) }
        focusImage(id)
    }

    fun selectAll() {
        val allIds = _uiState.value.images.map { it.id }.toSet()
        _uiState.update { it.copy(selectedIds = allIds) }
    }

    fun invertSelection() {
        val allIds = _uiState.value.images.map { it.id }.toSet()
        val currentIds = _uiState.value.selectedIds
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
    }

    fun setViewMode(mode: GalleryMode) {
        viewModelScope.launch {
            settingsRepo.updateSettings { it.copy(session = it.session.copy(galleryViewMode = mode)) }
        }
    }

    fun onMove(from: Int, to: Int) {
        // Drag and drop is complex with a reactive source of truth.
        // Changing the order locally will be overwritten if the Repo updates.
        // For a file manager, drag-and-drop usually implies moving files or custom sort.
        // For now, we update the local visual list temporarily.
        _uiState.update { current ->
            val mutableImages = current.images.toMutableList()
            if (from in mutableImages.indices && to in mutableImages.indices) {
                val item = mutableImages.removeAt(from)
                mutableImages.add(to, item)
            }
            current.copy(images = mutableImages)
        }
        Logger.d(tag = "GalleryViewModel", messageString = "Visual move $from to $to")
    }

    fun openInExplorer(path: File?) {
        if (path == null) return
        try {
            viewModelScope.launch(Dispatchers.IO) {
                openFileInExplorer(path)
            }
        } catch (e: Exception) {
            onError(e)
        }

    }

    fun removeImage(id: String) {
        // NOTE: Since ImageRepository is the Source of Truth, we cannot just delete from memory locally.
        // We need to implement a delete function in ImageRepository/GalleryRepository that deletes the file,
        // and then the Repo will emit the new list.
        // For now, we will just deselect it in the UI.

        imageRepository.removeImage(id)

        _uiState.update { current ->
            current.copy(
                focusedImageId = if (current.focusedImageId == id) null else current.focusedImageId
            )
        }
    }

    fun removeSelectedImages() {
        // Similar to removeImage, this requires Repository support for deletion.
        imageRepository.removeImages(uiState.value.selectedIds)
        clearSelection()
    }

    // ==========================================================
    // SETTINGS & HELPERS
    // ==========================================================

    fun onSortByChange(sortBy: SortBy) {
        _uiState.update { it.copy(sortBy = sortBy) }
        updateUiList()
    }

    fun onSortOrderChange(sortOrder: SortOrder) {
        _uiState.update { it.copy(sortOrder = sortOrder) }
        updateUiList()
    }

    private fun setThumbnailSize(newSize: Float) {
        settingsRepo.updateSettings { it.copy(session = it.session.copy(minThumbnailSizeDp = newSize)) }
    }

    fun modifyThumbnailSize(increment: Boolean, byPercentage: Float = 0.1f) {
        val current = uiState.value.minThumbnailSizeDp
        val newSize = if (increment) current + (current * byPercentage) else current - (current * byPercentage)
        setThumbnailSize(newSize)
    }

    fun updateAdvanceFolderSettings(settings: DirectorySettings) {
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

    fun getSelectedImages(): List<ImageItem> {
        return _uiState.value.images.filter { it.id in _uiState.value.selectedIds }
    }

    fun onError(error: Exception) {
        _uiState.update { it.copy(error = error.message) }
    }

    fun onClearError() {
        _uiState.update { it.copy(error = null) }
    }

}