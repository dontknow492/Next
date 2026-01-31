package com.ghost.tagger.data.models

data class SaveOptions(
    val embedInFile: Boolean = true,  // "Save to EXIF/Header"
    val createSidecar: Boolean = false, // "Save to .xmp file"
    val strictFallback: Boolean = true // If embedding fails, force create sidecar?
)