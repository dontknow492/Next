package com.ghost.tagger.core


import co.touchlab.kermit.Logger
import com.ashampoo.kim.Kim
import com.ashampoo.kim.model.ImageFormat
import com.ashampoo.kim.model.MetadataUpdate
import com.ghost.tagger.data.models.ImageMetadata
import com.ghost.tagger.data.models.SaveOptions
import java.io.File
import java.io.FileOutputStream

object MetadataWriterV2 {

    /**
     * The Main Function: Decides whether to Embed or use Sidecar
     */
    fun save(
        file: File,
        metadata: ImageMetadata,
        options: SaveOptions,
        onError: (Exception) -> Unit
    ) {

        val xmpXml = generateXmpXml(metadata)
        var embedSuccess = false

        // RULE 1: "If both false, save to xmp" (Safety Net)
        val effectiveOptions = if (!options.embedInFile && !options.createSidecar) {
            options.copy(createSidecar = true)
        } else {
            options
        }

        // STEP A: Try Embedding (if requested)
        if (effectiveOptions.embedInFile) {
            // Check if format is supported by Kim
            if (canEmbed(file)) {
                try {
                    embedWithKim(file, metadata)
                    embedSuccess = true
                    Logger.i("Success: Embedded metadata in ${file.name}")
                } catch (e: Exception) {
                    Logger.e("Failed to embed: ${e.message}")
                    if (!effectiveOptions.strictFallback) {
                        onError(Exception("Failed to embed: ${e.message}"))
                    }
                }
            } else {
                // If format isn't supported for embedding, log it and possibly fallback
                Logger.w("Embedding skipped: Format not supported or file unreadable for ${file.name}")
                if (!effectiveOptions.strictFallback) {
                    onError(Exception("File type ${file.extension} does not support embedding."))
                }
            }
        }

        // STEP B: Sidecar Logic
        val shouldWriteSidecar = effectiveOptions.createSidecar ||
                (effectiveOptions.embedInFile && !embedSuccess && effectiveOptions.strictFallback)

        if (shouldWriteSidecar) {
            try {
                saveSidecar(file, xmpXml)
                Logger.i("Success: Saved sidecar for ${file.name}")
            } catch (e: Exception) {
                onError(Exception("Critical: Failed to save sidecar. Data not saved. ${e.message}"))
            }
        }
    }

    // --- Helper Methods ---

    private fun saveSidecar(file: File, xmpXml: String) {
        // Standard practice: "image.xmp" (Windows) or "image.jpg.xmp" (Darktable)
        val sidecar = File(file.parent, "${file.name}.xmp")
        sidecar.writeText(xmpXml)
    }

    private fun canEmbed(file: File): Boolean {
        return try {
            // Kim.readMetadata expects ByteArray or ByteReader. Passing 'File' directly causes a mismatch.
            // Using file.readBytes() solves the "Argument type mismatch: actual type is 'File', but 'ByteArray' was expected" error.
            val metadata = Kim.readMetadata(file.readBytes())
            val format = metadata?.imageFormat

            // Explicitly whitelist the formats Kim supports writing XMP to
            format != null && format in setOf(
                ImageFormat.JPEG,
                ImageFormat.PNG,
                ImageFormat.WEBP,
                ImageFormat.TIFF,
                ImageFormat.HEIC, // Include if your version of Kim supports it
                ImageFormat.AVIF  // Include if your version of Kim supports it
            )
        } catch (e: Exception) {
            // If we can't read it, we definitely can't write to it
            false
        }
    }

    private fun embedWithKim(file: File, metadata: ImageMetadata) {
        val tempFile = File(file.parent, "${file.name}.tmp")
        try {
            // We start with the original file data
            var currentBytes = file.readBytes()

            // 1. Apply Description update if it exists
            metadata.description?.let { desc ->
                // We overwrite currentBytes with the updated version
                currentBytes = Kim.update(currentBytes, MetadataUpdate.Description(desc))
            }

            // 2. Apply Keywords (Tags) update if they exist
            if (metadata.tags.isNotEmpty()) {
                val keywordSet = metadata.tags.map { it.name }.toSet()
                // Again, we update the latest version of the bytes
                currentBytes = Kim.update(currentBytes, MetadataUpdate.Keywords(keywordSet))
            }

            // 3. Write the final 'chained' bytes to the temp file
            FileOutputStream(tempFile).use { it.write(currentBytes) }

            // Swap the temp file with the original (Standard safe-save pattern)
            if (tempFile.exists() && tempFile.length() > 0) {
                if (file.exists() && !file.delete()) throw Exception("Could not delete original file.")
                if (!tempFile.renameTo(file)) throw Exception("Could not rename temp file.")
            }
        } catch (e: Exception) {
            if (tempFile.exists()) tempFile.delete()
            throw e
        }
    }

    // --- 1. XMP Generation (Robust & Standard Compliant) ---
    private fun generateXmpXml(meta: ImageMetadata): String {
        val sb = StringBuilder()

        // 1. Processing Instruction (Packet Wrapper)
        sb.append("<?xpacket begin=\"\uFEFF\" id=\"W5M0MpCehiHzreSzNTczkc9d\"?>")

        // 2. XMP Header
        sb.append("""<x:xmpmeta xmlns:x="adobe:ns:meta/" x:xmptk="Ghost AI Tagger">""")
        sb.append("""<rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">""")

        // 3. Main Description Block
        sb.append(
            """<rdf:Description rdf:about="" 
            xmlns:dc="http://purl.org/dc/elements/1.1/" 
            xmlns:exif="http://ns.adobe.com/exif/1.0/"
            xmlns:ghost="http://ghost.tagger/1.0/">"""
        )

        // A. Description (dc:description)
        if (!meta.description.isNullOrBlank()) {
            sb.append("<dc:description><rdf:Alt>")
            sb.append("<rdf:li xml:lang=\"x-default\">${escapeXml(meta.description.trim())}</rdf:li>")
            sb.append("</rdf:Alt></dc:description>")

            // A2. Description Fallback (exif:UserComment)
            sb.append("<exif:UserComment><rdf:Alt>")
            sb.append("<rdf:li xml:lang=\"x-default\">${escapeXml(meta.description.trim())}</rdf:li>")
            sb.append("</rdf:Alt></exif:UserComment>")
        }

        // B. Tags (dc:subject)
        if (meta.tags.isNotEmpty()) {
            sb.append("<dc:subject><rdf:Bag>")
            meta.tags.forEach { tag ->
                sb.append("<rdf:li>${escapeXml(tag.name.trim())}</rdf:li>")
            }
            sb.append("</rdf:Bag></dc:subject>")
        }

        // C. Extended Data (Custom Namespace)
        if (meta.tags.isNotEmpty()) {
            sb.append("<ghost:extended_tags><rdf:Seq>")
            meta.tags.forEach { tag ->
                sb.append("""<rdf:li ghost:name="${escapeXml(tag.name)}" ghost:confidence="${tag.confidence}" ghost:source="${tag.source}"/>""")
            }
            sb.append("</rdf:Seq></ghost:extended_tags>")
        }

        // Close Tags
        sb.append("</rdf:Description>")
        sb.append("</rdf:RDF></x:xmpmeta>")

        // Close Packet
        sb.append("<?xpacket end=\"w\"?>")

        return sb.toString()
    }

    private fun escapeXml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}