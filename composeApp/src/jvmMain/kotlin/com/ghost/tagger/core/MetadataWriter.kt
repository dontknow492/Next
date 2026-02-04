package com.ghost.tagger.core

import co.touchlab.kermit.Logger
import com.drew.metadata.xmp.XmpWriter
import com.ghost.tagger.data.models.ImageMetadata
import com.ghost.tagger.data.models.SaveOptions
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
                    Logger.e("Failed to embed: ${e.message}")
                    if (!effectiveOptions.strictFallback) {
                        onError(Exception("Failed to embed: ${e.message}"))
                    }
                }
            } else {
                // If specific format embedding isn't supported (e.g. PNG), we don't crash.
                // We just log/notify and let the Sidecar logic below take over if allowed.
                if (!effectiveOptions.strictFallback) {
                    onError(Exception("File type ${file.extension} does not support embedding (Only JPEG supported)."))
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
        // We'll stick to replacing extension for cleaner Windows usage if strictly sidecar,
        // but appending .xmp is safer for avoiding name collisions.
        // Let's use append (.jpg.xmp) as it's the safest 'non-destructive' way.
        val sidecar = File(file.parent, "${file.name}.xmp")
        sidecar.writeText(xmpXml)
    }

    private fun canEmbed(file: File): Boolean {
        // Apache Commons Imaging's JpegXmpRewriter ONLY supports JPEGs.
        // Other formats (PNG, TIFF) require full re-encoding or different logic,
        // so we strictly limit embedding to JPEGs for now.
        return file.extension.lowercase() in setOf("jpg", "jpeg")
    }

    private fun embedXmp(file: File, xmpXml: String) {
        val tempFile = File(file.parent, "${file.name}.tmp")
        BufferedOutputStream(FileOutputStream(tempFile)).use { bos ->
            // This class is specifically for JPEG structure rewriting
            val rewriter = JpegXmpRewriter()
            rewriter.updateXmpXml(file, bos, xmpXml)
        }

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

    // --- 1. XMP Generation (Robust & Standard Compliant) ---
    private fun generateXmpXml(meta: ImageMetadata): String {
        val sb = StringBuilder()

        // 1. Processing Instruction (Packet Wrapper)
        // Critical for Sidecar files to be recognized by Adobe/Windows
        sb.append("<?xpacket begin=\"\uFEFF\" id=\"W5M0MpCehiHzreSzNTczkc9d\"?>")

        // 2. XMP Header
        sb.append("""<x:xmpmeta xmlns:x="adobe:ns:meta/" x:xmptk="Ghost AI Tagger">""")
        sb.append("""<rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">""")

        // 3. Main Description Block
        // Note: We add 'exif' namespace for UserComment compatibility
        sb.append("""<rdf:Description rdf:about="" 
            xmlns:dc="http://purl.org/dc/elements/1.1/" 
            xmlns:exif="http://ns.adobe.com/exif/1.0/"
            xmlns:ghost="http://ghost.tagger/1.0/">""")

        // A. Description (dc:description) - Standard XMP
        if (!meta.description.isNullOrBlank()) {
            sb.append("<dc:description><rdf:Alt>")
            sb.append("<rdf:li xml:lang=\"x-default\">${escapeXml(meta.description.trim())}</rdf:li>")
            sb.append("</rdf:Alt></dc:description>")

            // A2. Description Fallback (exif:UserComment) - BETTER COMPATIBILITY
            // Windows Explorer often looks here for "Comments" if it ignores dc:description
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