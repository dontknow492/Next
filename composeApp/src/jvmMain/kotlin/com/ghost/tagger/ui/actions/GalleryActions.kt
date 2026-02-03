package com.ghost.tagger.ui.actions

import com.ghost.tagger.data.enums.GalleryMode

// The Actions the UI can send to the ViewModel
interface GalleryActions {
    // 1. Navigation
    fun loadDirectory(path: String, recursive: Boolean)
    fun refresh() // Reload current dir

    // 2. View Modes
    fun setViewMode(mode: GalleryMode)

    // 3. Selection (The complex logic)
    fun toggleSelection(id: String, shiftPressed: Boolean)
    fun selectAll()
    fun invertSelection()
    fun clearSelection()

    // 4. Focus (Sidebar Update)
    fun focusImage(id: String)

    // 5. File Ops
    fun deleteSelectedImages() // Requires "Are you sure?" dialog
}

