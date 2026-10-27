package com.ninjavault.app.vault

import com.ninjavault.app.data.VaultFileEntity
import com.ninjavault.app.data.VaultFileType

/**
 * Helpers for the app's custom ".adx" naming, and for working out what a file really is
 * when the document picker only reports a generic MIME type (common for mkv, heic,
 * re-imported .adx files and anything with an unregistered extension).
 *
 * Pure Kotlin (no Android classes) so it can be unit-tested on the plain JVM.
 */
object VaultNames {
    const val ADX_EXTENSION = "adx"
    private const val ADX_SUFFIX = ".adx"
    const val GENERIC_MIME = "application/octet-stream"

    private val GENERIC_MIMES = setOf(GENERIC_MIME, "binary/octet-stream", "application/x-binary", "*/*", "")

    // Insertion order matters for reverse lookups: the first extension listed for a MIME type wins.
    private val mimeByExtension: Map<String, String> = linkedMapOf(
        "jpg" to "image/jpeg", "jpeg" to "image/jpeg", "png" to "image/png", "gif" to "image/gif",
        "webp" to "image/webp", "bmp" to "image/bmp", "heic" to "image/heic", "heif" to "image/heif",
        "avif" to "image/avif",
        "mp4" to "video/mp4", "m4v" to "video/mp4", "mkv" to "video/x-matroska", "webm" to "video/webm",
        "mov" to "video/quicktime", "3gp" to "video/3gpp", "avi" to "video/x-msvideo",
        "mp3" to "audio/mpeg", "m4a" to "audio/mp4", "aac" to "audio/aac", "wav" to "audio/wav",
        "ogg" to "audio/ogg", "oga" to "audio/ogg", "opus" to "audio/opus", "flac" to "audio/flac",
        "pdf" to "application/pdf", "zip" to "application/zip",
        "html" to "text/html", "htm" to "text/html",
        "txt" to "text/plain", "doc" to "application/msword",
        "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "json" to "application/json", "xml" to "text/xml", "csv" to "text/csv", "md" to "text/markdown",
        "js" to "text/javascript", "py" to "text/x-python", "exe" to "application/x-msdownload"
    )

    // Extensions that are plain text even when the picker says application/octet-stream.
    private val TEXT_EXTENSIONS = setOf(
        "txt", "json", "js", "mjs", "ts", "tsx", "jsx", "py", "md", "csv", "tsv", "xml", "yml", "yaml",
        "log", "ini", "cfg", "conf", "toml", "kt", "java", "c", "cpp", "h", "sh", "bat", "ps1", "css",
        "sql", "rb", "go", "rs", "php", "srt"
    )
    private val TEXT_MIMES = setOf(
        "application/json", "application/xml", "application/javascript", "application/x-javascript",
        "application/x-yaml", "application/x-sh", "application/x-python-code"
    )

    /** The name shown in the vault list: the original base name with the app's .adx extension. */
    fun adxDisplayName(original: String): String {
        val trimmed = original.trim()
        val dot = trimmed.lastIndexOf('.')
        // dot > 0 so dot-files like ".hidden" keep their whole name instead of becoming ".adx".
        val base = if (dot > 0) trimmed.substring(0, dot) else trimmed
        return (base.ifEmpty { "file" }) + ADX_SUFFIX
    }

    /** Base name shown in the rename field; the real extension is preserved separately. */
    fun nameWithoutExtension(name: String): String {
        val dot = name.lastIndexOf('.')
        return if (dot > 0) name.substring(0, dot) else name
    }

    /** Rename only the base name so previews keep the original format and MIME type. */
    fun renamedDisplayName(original: String, requestedBase: String): String {
        val trimmed = requestedBase.trim()
        val dot = original.lastIndexOf('.')
        val suffix = if (dot > 0) original.substring(dot) else ""
        val base = if (suffix.isNotEmpty() && trimmed.endsWith(suffix, ignoreCase = true)) {
            trimmed.dropLast(suffix.length)
        } else trimmed
        return base.trimEnd('.').ifBlank { "file" } + suffix
    }

    /** Suggested name for an exported key file: "photo.jpg" -> "photo.adxkey". */
    fun keyFileName(original: String): String = adxDisplayName(original).dropLast(ADX_SUFFIX.length) + ".adxkey"

    /** Removes a trailing ".adx" so a search for "photo.adx" still finds the stored "photo.jpg". */
    fun stripAdxSuffix(text: String): String =
        if (text.endsWith(ADX_SUFFIX, ignoreCase = true)) text.dropLast(ADX_SUFFIX.length) else text

    /** Lower-case, sanitised extension (letters/digits only, max 8 chars); empty if none. */
    fun extensionOf(name: String): String {
        val dot = name.lastIndexOf('.')
        if (dot <= 0 || dot == name.length - 1) return ""
        return name.substring(dot + 1).lowercase().filter { it.isLetterOrDigit() }.take(8)
    }

    fun mimeForExtension(ext: String): String? = mimeByExtension[ext.lowercase()]

    private fun extensionForMime(mime: String): String? =
        mimeByExtension.entries.firstOrNull { it.value == mime }?.key

    /** Prefers a specific MIME type from the picker, otherwise infers one from the file name. */
    fun resolveMime(declared: String?, name: String): String {
        val d = declared?.substringBefore(';')?.trim()?.lowercase().orEmpty()
        if (d !in GENERIC_MIMES) return d
        return mimeForExtension(extensionOf(name)) ?: GENERIC_MIME
    }

    /** Plain-text-like files (txt, json, py, js, ...) that the in-app viewer can show. HTML has its own viewer. */
    fun isText(mimeType: String, name: String): Boolean {
        val mime = resolveMime(mimeType, name)
        if (mime == "text/html" || mime == "application/xhtml+xml") return false
        return extensionOf(name) in TEXT_EXTENSIONS || mime.startsWith("text/") || mime in TEXT_MIMES
    }

    fun isHtml(mimeType: String, name: String): Boolean {
        val mime = resolveMime(mimeType, name)
        return mime == "text/html" || mime == "application/xhtml+xml"
    }

    fun typeFor(mimeType: String, name: String): VaultFileType {
        val mime = resolveMime(mimeType, name)
        return when {
            mime.startsWith("image/") -> VaultFileType.IMAGE
            mime.startsWith("video/") -> VaultFileType.VIDEO
            mime.startsWith("audio/") -> VaultFileType.AUDIO
            mime == "application/pdf" -> VaultFileType.PDF
            mime == "application/zip" || mime == "application/x-zip-compressed" -> VaultFileType.ZIP
            isText(mime, name) || mime == "application/msword" ||
                mime.startsWith("application/vnd.openxmlformats-officedocument.wordprocessingml") -> VaultFileType.DOCUMENT
            else -> VaultFileType.OTHER
        }
    }

    /**
     * Extension for the decrypted playback temp file. Never ".adx" (the player can't use it to
     * pick a decoder), so a file renamed to "clip.adx" still falls back to its MIME type.
     */
    fun playbackExtension(mimeType: String, name: String): String {
        val ext = extensionOf(name)
        if (ext.isNotEmpty() && ext != ADX_EXTENSION) return ext
        return extensionForMime(resolveMime(mimeType, name)).orEmpty()
    }
}

/** Name as displayed in the vault list (original base name + .adx). */
fun VaultFileEntity.listName(): String = VaultNames.adxDisplayName(displayName)

/**
 * The stored type, re-derived for rows imported before MIME inference existed
 * (those were saved as OTHER when the picker reported application/octet-stream).
 */
fun VaultFileEntity.effectiveType(): VaultFileType =
    if (fileType != VaultFileType.OTHER) fileType else VaultNames.typeFor(mimeType, displayName)
