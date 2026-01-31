package com.ghost.tagger.core

import com.ghost.tagger.data.models.ImageMetadata
import com.ghost.tagger.data.models.SaveOptions
//import com.ghost.tagger.data.models.TagSource
import org.apache.commons.imaging.formats.jpeg.xmp.JpegXmpRewriter
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream


object MetadataWriter {

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
            if (canEmbed(file)) {
                try {
                    embedXmp(file, xmpXml)
                    embedSuccess = true
                    println("Success: Embedded metadata in ${file.name}")
                } catch (e: Exception) {
                    println("Embedding Failed: ${e.message}")
                    // If strict fallback is OFF, we report error immediately.
                    // If ON, we swallow error and let the Fallback logic handle it below.
                    if (!effectiveOptions.strictFallback) {
                        onError(Exception("Failed to embed: ${e.message}"))
                    }
                }
            } else {
                if (!effectiveOptions.strictFallback) {
                    onError(Exception("File type ${file.extension} does not support embedding."))
                }
            }
        }

        // STEP B: Sidecar Logic
        // Run if:
        // 1. User specifically asked for Sidecar
        // 2. OR: User asked for Embedding, it FAILED, and Strict Fallback is ON
        val shouldWriteSidecar = effectiveOptions.createSidecar ||
                                 (effectiveOptions.embedInFile && !embedSuccess && effectiveOptions.strictFallback)

        if (shouldWriteSidecar) {
            try {
                saveSidecar(file, xmpXml)
                println("Success: Saved sidecar for ${file.name}")
            } catch (e: Exception) {
                // If even the sidecar fails (e.g. disk full), this is a critical error
                onError(Exception("Critical: Failed to save sidecar. Data not saved. ${e.message}"))
            }
        }
    }

    // --- Helper Methods (Same as before) ---

    private fun saveSidecar(file: File, xmpXml: String) {
        val sidecar = File(file.parent, "${file.name}.xmp")
        sidecar.writeText(xmpXml)
    }

    private fun canEmbed(file: File): Boolean {
        // Only attempt embedding for formats we are confident in
        return file.extension.lowercase() in setOf("jpg", "jpeg", "png", "gif", "tiff")
    }

    // (Using Apache Commons Imaging or similar library)
    private fun embedXmp(file: File, xmpXml: String) {
        // 1. Create Temp File
        val tempFile = File(file.parent, "${file.name}.tmp")

        // 2. Use the Safe Rewriter (Does NOT re-encode image pixels)
        BufferedOutputStream(FileOutputStream(tempFile)).use { bos ->
            val rewriter = JpegXmpRewriter()
            rewriter.updateXmpXml(file, bos, xmpXml)
        }

        // 3. Swap Files (Atomic-ish replacement)
        if (tempFile.exists() && tempFile.length() > 0) {
            if (file.exists() && !file.delete()) {
                throw Exception("Could not delete original file to swap.")
            }
            if (!tempFile.renameTo(file)) {
                throw Exception("Could not rename temp file to original name.")
            }
        } else {
            throw Exception("Temp file write failed or was empty.")
        }
    }

    // --- 1. XMP Generation (The XML logic) ---
    // We construct the XML manually to avoid huge heavy libraries.
    // This creates standard "dc:subject" (Tags) and "dc:description".
    private fun generateXmpXml(meta: ImageMetadata): String {
        val sb = StringBuilder()

        // Header
        sb.append("""<x:xmpmeta xmlns:x="adobe:ns:meta/" x:xmptk="Ghost AI Tagger">""")
        sb.append("""<rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">""")

        // Description Block
        sb.append("""<rdf:Description rdf:about="" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:ghost="http://ghost.tagger/1.0/">""")

        // A. Description
        if (!meta.description.isNullOrBlank()) {
            sb.append("<dc:description><rdf:Alt>")
            sb.append("<rdf:li xml:lang=\"x-default\">${escapeXml(meta.description)}</rdf:li>")
            sb.append("</rdf:Alt></dc:description>")
        }

        // B. Tags (Standard Viewers read this)
        if (meta.tags.isNotEmpty()) {
            sb.append("<dc:subject><rdf:Bag>")
            meta.tags.forEach { tag ->
                sb.append("<rdf:li>${escapeXml(tag.name)}</rdf:li>")
            }
            sb.append("</rdf:Bag></dc:subject>")
        }

        // C. "More" - Rich AI Data (Custom Namespace)
        // This saves the Confidence and Source so your app can reload it later.
        // Other apps (Explorer, Lightroom) will safely ignore this block.
        if (meta.tags.isNotEmpty()) {
            sb.append("<ghost:extended_tags><rdf:Seq>")
            meta.tags.forEach { tag ->
                // We save attributes: name, confidence, source
                sb.append("""<rdf:li ghost:name="${escapeXml(tag.name)}" ghost:confidence="${tag.confidence}" ghost:source="${tag.source}"/>""")
            }
            sb.append("</rdf:Seq></ghost:extended_tags>")
        }

        sb.append("</rdf:Description>")
        sb.append("</rdf:RDF></x:xmpmeta>")

        return sb.toString()
    }


    // Helper to prevent XML breaking on special chars like "&" or "<"
    private fun escapeXml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}