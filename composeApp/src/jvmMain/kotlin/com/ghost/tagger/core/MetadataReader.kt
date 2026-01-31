package com.ghost.tagger.core


import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.exif.ExifSubIFDDirectory
import com.drew.metadata.iptc.IptcDirectory
import com.drew.metadata.jpeg.JpegDirectory
import com.drew.metadata.png.PngDirectory
import com.drew.metadata.xmp.XmpDirectory
import com.ghost.tagger.TagSource
import com.ghost.tagger.data.models.ImageMetadata
import com.ghost.tagger.data.models.ImageTag
import java.io.File

object MetadataReader {

    private val SUPPORTED_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "bmp")

    fun isSupported(file: File): Boolean {
        return file.extension.lowercase() in SUPPORTED_EXTENSIONS
    }

    fun read(file: File): ImageMetadata {
        // 1. Basic File System Info (Always available)
        val defaultMeta = ImageMetadata(
            path = file.absolutePath,
            name = file.name,
            extension = file.extension,
            fileSizeBytes = file.length(),
            lastModified = file.lastModified()
        )

        return try {
            val metadata = ImageMetadataReader.readMetadata(file)

            // 2. Extract Dimensions (Width x Height)
            val (width, height) = extractDimensions(metadata)

            // 3. Extract Date Taken (EXIF preference)
            val dateTaken = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory::class.java)
                ?.getDate(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL)
                ?.time ?: file.lastModified()

            // 4. Extract Description & Tags (XMP > IPTC)
            // This is crucial for your AI logic.
            val (desc, tags) = extractContentMetadata(metadata)

            defaultMeta.copy(
                width = width,
                height = height,
                dateTaken = dateTaken,
                description = desc,
                tags = tags
            )

        } catch (e: Exception) {
            // Log error in production
            println("Metadata Error for ${file.name}: ${e.message}")
            defaultMeta
        }
    }

    // --- Helpers ---

    private fun extractDimensions(metadata: com.drew.metadata.Metadata): Pair<Int, Int> {
        // Try JPEG
        metadata.getFirstDirectoryOfType(JpegDirectory::class.java)?.let { dir ->
            return Pair(dir.getInt(JpegDirectory.TAG_IMAGE_WIDTH), dir.getInt(JpegDirectory.TAG_IMAGE_HEIGHT))
        }
        // Try PNG
        metadata.getFirstDirectoryOfType(PngDirectory::class.java)?.let { dir ->
            return Pair(dir.getInt(PngDirectory.TAG_IMAGE_WIDTH), dir.getInt(PngDirectory.TAG_IMAGE_HEIGHT))
        }
        return Pair(0, 0)
    }

    private fun extractContentMetadata(metadata: com.drew.metadata.Metadata): Pair<String?, List<ImageTag>> {
        var description: String? = null
        val tags = mutableSetOf<String>() // Set to avoid duplicates

        // A. Try XMP (The Modern Standard)
        // XMP stores data as XML. We access the XMPMeta object directly.
        val xmpDirs = metadata.getDirectoriesOfType(XmpDirectory::class.java)
        for (dir in xmpDirs) {
            val xmpMeta = dir.xmpMeta
            try {
                // 1. Get Description (dc:description)
                if (description == null && xmpMeta.doesPropertyExist("http://purl.org/dc/elements/1.1/", "description")) {
                    val item = xmpMeta.getLocalizedText("http://purl.org/dc/elements/1.1/", "description", null, "x-default")
                    if (!item.value.isNullOrBlank()) description = item.value
                }

                // 2. Get Keywords/Tags (dc:subject)
                if (xmpMeta.doesPropertyExist("http://purl.org/dc/elements/1.1/", "subject")) {
                    val count = xmpMeta.countArrayItems("http://purl.org/dc/elements/1.1/", "subject")
                    for (i in 1..count) {
                        val tagItem = xmpMeta.getArrayItem("http://purl.org/dc/elements/1.1/", "subject", i)
                        if (!tagItem.value.isNullOrBlank()) tags.add(tagItem.value)
                    }
                }
            } catch (e: Exception) {
                // XMP parsing weirdness
            }
        }

        // B. Try IPTC (Legacy Fallback)
        // Only if we missed data in XMP
        if (description == null || tags.isEmpty()) {
            val iptcDir = metadata.getFirstDirectoryOfType(IptcDirectory::class.java)
            if (iptcDir != null) {
                if (description == null) {
                    description = iptcDir.getString(IptcDirectory.TAG_CAPTION)
                }
                val iptcKeywords = iptcDir.getStringArray(IptcDirectory.TAG_KEYWORDS)
                if (iptcKeywords != null) {
                    tags.addAll(iptcKeywords)
                }
            }
        }

        // Convert Strings to ImageTags
        val imageTags = tags.map {
            ImageTag(name = it, confidence = 1.0, source = TagSource.FILE)
        }

        return Pair(description, imageTags)
    }
}