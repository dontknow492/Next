package com.ghost.tagger.data.models.settings

data class DirectorySettings(
    val isRecursive: Boolean = false,
    val maxDepth: Int = 2, // 1 = Only top level, 2 = Subfolders, etc.
    val includeHiddenFiles: Boolean = false
)