package com.ghost.tagger.core

import co.touchlab.kermit.Logger
import com.adobe.internal.xmp.XMPMetaFactory
import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.exif.ExifSubIFDDirectory
import com.drew.metadata.iptc.IptcDirectory
import com.drew.metadata.jpeg.JpegDirectory
import com.drew.metadata.png.PngDirectory
import com.drew.metadata.xmp.XmpDirectory
import com.ghost.tagger.data.enums.TagSource
import com.ghost.tagger.data.models.ImageMetadata
import com.ghost.tagger.data.models.ImageTag
import java.io.File

object MetadataReader {

    private val SUPPORTED_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "bmp", "tiff", "heic", "avif", "jxl")

    fun isSupported(file: File): Boolean {
        return file.extension.lowercase() in SUPPORTED_EXTENSIONS
    }

    fun read(file: File): ImageMetadata {
        // 1. Basic File System Info (Always available)
        val defaultMeta = ImageMetadata(
            path = file,
            name = file.name,
            extension = file.extension,
            fileSizeBytes = file.length(),
            lastModified = file.lastModified()
        )

        return try {
            // --- Step A: Read Embedded Metadata ---
            val embeddedMetadata = try {
                ImageMetadataReader.readMetadata(file)
            } catch (e: Exception) {
                null
            }

            // --- Step B: Read Sidecar Metadata (XMP) ---
            // We check for image.jpg.xmp AND image.xmp
            val sidecarFile = findSidecarFile(file)
            val sidecarMetadata = if (sidecarFile != null) {
                try {
                    // Try standard detection first
                    ImageMetadataReader.readMetadata(sidecarFile)
                } catch (e: Exception) {
                    // Fallback: Manually parse XMP if detector fails (common for standalone .xmp)
                    // The library throws "File format could not be determined" for raw XMP text files.
                    try {
                        val xmpMeta = XMPMetaFactory.parseFromBuffer(sidecarFile.readBytes())
                        val manualMeta = com.drew.metadata.Metadata()
                        val dir = XmpDirectory()
                        dir.setXMPMeta(xmpMeta)
                        manualMeta.addDirectory(dir)
                        manualMeta
                    } catch (xmpEx: Exception) {
                        Logger.e("Failed to read sidecar ${sidecarFile.name}: ${e.message} / ${xmpEx.message}")
                        null
                    }
                }
            } else null

            // --- Step C: Extract & Merge ---

            // 1. Dimensions (Only exists in image file)
            val (width, height) = if (embeddedMetadata != null) {
                extractDimensions(embeddedMetadata)
            } else Pair(0, 0)

            // 2. Date Taken (Prioritize Embedded EXIF, fallback to File Modified)
            val dateTaken = if (embeddedMetadata != null) {
                embeddedMetadata.getFirstDirectoryOfType(ExifSubIFDDirectory::class.java)
                    ?.getDate(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL)
                    ?.time ?: file.lastModified()
            } else file.lastModified()

            // 3. Content (Description & Tags) - THE MERGE LOGIC
            // Strategy: Sidecar overrides Image if present.
            val (embDesc, embTags) = if (embeddedMetadata != null) extractContentMetadata(embeddedMetadata) else Pair(null, emptyList())
            val (sideDesc, sideTags) = if (sidecarMetadata != null) extractContentMetadata(sidecarMetadata) else Pair(null, emptyList())

            // Description: Sidecar > Embedded
            val finalDescription = if (!sideDesc.isNullOrBlank()) sideDesc else embDesc

            // Tags: Merge unique tags from both sources
            val mergedTags = (embTags + sideTags).distinctBy { it.name }

            defaultMeta.copy(
                width = width,
                height = height,
                dateTaken = dateTaken,
                description = finalDescription,
                tags = mergedTags
            )

        } catch (e: Exception) {
            // Log error in production
            Logger.e("Metadata Critical Error for ${file.name}: ${e.message}")
            defaultMeta
        }
    }

    // --- Helpers ---

    private fun findSidecarFile(imageFile: File): File? {
        val candidates = listOf(
            File(imageFile.parent, "${imageFile.name}.xmp"),            // image.jpg.xmp (Darktable/Adobe style)
            File(imageFile.parent, "${imageFile.nameWithoutExtension}.xmp") // image.xmp (Windows/Standard style)
        )
        return candidates.firstOrNull { it.exists() }
    }

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
        val tags = mutableSetOf<String>()

        // A. Try XMP (The Modern Standard)
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

        // B. Try IPTC (Legacy Fallback) - Only if XMP failed
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