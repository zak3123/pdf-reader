package com.fatih.litepdf.office

import android.content.ContentResolver
import android.net.Uri
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.BufferedInputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Shared low-level helpers for reading OOXML (docx/pptx/xlsx) packages, which are
 * ordinary ZIP archives containing XML parts. Uses only [java.util.zip] and the
 * platform [XmlPullParser], so no external office library is needed.
 */
internal object OoxmlSupport {

    /** Guard against pathological or hostile files exhausting memory. */
    const val MAX_ENTRY_BYTES: Long = 24L * 1024 * 1024
    const val MAX_TOTAL_BYTES: Long = 64L * 1024 * 1024

    fun newPullParser(): XmlPullParser {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = false
        return factory.newPullParser()
    }

    /**
     * Read selected entries fully into memory in a single ZIP pass. Prefer [forEachEntry]
     * for single-part formats; use this when a parser needs random access across parts
     * (e.g. xlsx sharedStrings resolved while iterating sheets) or deterministic ordering.
     *
     * @param selector returns true for entry names whose bytes should be collected.
     * @return a map of entry name -> raw bytes for the entries that matched.
     */
    fun readEntries(
        contentResolver: ContentResolver,
        uri: Uri,
        selector: (String) -> Boolean
    ): Map<String, ByteArray> {
        val result = LinkedHashMap<String, ByteArray>()
        var totalRead = 0L
        openStream(contentResolver, uri).use { raw ->
            ZipInputStream(BufferedInputStream(raw)).use { zip ->
                var entry: ZipEntry? = zip.nextEntry
                while (entry != null) {
                    val name = entry.name
                    if (!entry.isDirectory && selector(name)) {
                        val bytes = readCurrentEntry(zip)
                        totalRead += bytes.size
                        if (totalRead > MAX_TOTAL_BYTES) {
                            throw java.io.IOException("Document content exceeds supported size")
                        }
                        result[name] = bytes
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
        return result
    }

    private fun readCurrentEntry(zip: ZipInputStream): ByteArray {
        val buffer = ByteArray(16 * 1024)
        val out = java.io.ByteArrayOutputStream()
        var read: Int
        var total = 0L
        while (zip.read(buffer).also { read = it } != -1) {
            total += read
            if (total > MAX_ENTRY_BYTES) {
                throw java.io.IOException("Document part exceeds supported size")
            }
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    /**
     * Stream selected entries one at a time, invoking [action] with the entry name and a
     * live, size-guarded [InputStream] positioned at that entry.
     *
     * Unlike buffering every part into memory, this holds only the current entry's stream,
     * so callers that parse per-part (e.g. one slide/sheet) keep a bounded memory footprint
     * regardless of how many parts the package contains. The stream passed to [action] must
     * be consumed within the callback and must not be retained afterwards.
     *
     * @param selector returns true for entry names that should be streamed to [action].
     */
    fun forEachEntry(
        contentResolver: ContentResolver,
        uri: Uri,
        selector: (String) -> Boolean,
        action: (name: String, input: InputStream) -> Unit
    ) {
        var totalRead = 0L
        openStream(contentResolver, uri).use { raw ->
            ZipInputStream(BufferedInputStream(raw)).use { zip ->
                var entry: ZipEntry? = zip.nextEntry
                while (entry != null) {
                    val name = entry.name
                    if (!entry.isDirectory && selector(name)) {
                        val guarded = CountingLimitStream(zip, MAX_ENTRY_BYTES)
                        action(name, guarded)
                        totalRead += guarded.bytesRead
                        if (totalRead > MAX_TOTAL_BYTES) {
                            throw java.io.IOException("Document content exceeds supported size")
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
    }

    fun openStream(contentResolver: ContentResolver, uri: Uri): InputStream =
        contentResolver.openInputStream(uri)
            ?: throw java.io.FileNotFoundException("Provider returned no stream for $uri")

    /** Collapse runs of whitespace and trim, returning null when the result is blank. */
    fun normalizeOrNull(text: String): String? {
        val cleaned = text.replace('\u00A0', ' ')
            .replace(TAB_LIKE_WHITESPACE, " ")
            .replace(DOUBLE_SPACES, " ")
            .trim()
        return cleaned.ifBlank { null }
    }

    private val TAB_LIKE_WHITESPACE = Regex("[\\t\\r\\f]+")
    private val DOUBLE_SPACES = Regex(" {2,}")

    /**
     * Wraps a shared [ZipInputStream] to count bytes and enforce the per-entry limit without
     * closing the underlying archive (the ZIP stream is reused for subsequent entries).
     */
    private class CountingLimitStream(
        private val delegate: InputStream,
        private val maxBytes: Long
    ) : InputStream() {
        var bytesRead: Long = 0L
            private set

        override fun read(): Int {
            val b = delegate.read()
            if (b != -1) {
                bytesRead++
                enforce()
            }
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val n = delegate.read(b, off, len)
            if (n > 0) {
                bytesRead += n
                enforce()
            }
            return n
        }

        override fun available(): Int = delegate.available()

        /** No-op: the shared ZipInputStream is closed by [forEachEntry], not by consumers. */
        override fun close() {}

        private fun enforce() {
            if (bytesRead > maxBytes) {
                throw java.io.IOException("Document part exceeds supported size")
            }
        }
    }
}
