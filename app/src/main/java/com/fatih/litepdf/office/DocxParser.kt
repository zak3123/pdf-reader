package com.fatih.litepdf.office

import android.content.ContentResolver
import android.net.Uri
import org.xmlpull.v1.XmlPullParser

/**
 * Extracts readable content from a Word .docx package (OOXML).
 *
 * It walks word/document.xml, mapping:
 * - `w:p` paragraphs to [DocBlock.Heading] (when the paragraph style starts with "Heading"
 *   or "Title"), [DocBlock.Bullet] (list items), or [DocBlock.Paragraph].
 * - `w:tbl` tables to [DocBlock.Table].
 *
 * Legacy binary .doc (OLE2) is not an OOXML package and is not supported here.
 */
class DocxParser(private val contentResolver: ContentResolver) {

    fun parse(uri: Uri): TextDocumentContent {
        val blocks = mutableListOf<DocBlock>()
        var found = false
        OoxmlSupport.forEachEntry(
            contentResolver,
            uri,
            selector = { it == "word/document.xml" }
        ) { _, input ->
            found = true
            parseDocument(input, blocks)
        }
        if (!found) {
            throw java.io.IOException("word/document.xml missing; not a valid .docx")
        }
        return TextDocumentContent(blocks)
    }

    private fun parseDocument(input: java.io.InputStream, blocks: MutableList<DocBlock>) {
        val parser = OoxmlSupport.newPullParser()
        parser.setInput(input, null)

        // Paragraph accumulation state.
        val paraText = StringBuilder()
        var paraStyle: String? = null
        var paraIsList = false
        var paraIndent = 0
        var inParagraph = false
        var captureText = false

        // Table accumulation state. tableDepth tracks nesting so a nested w:tbl does not
        // prematurely end the outer table.
        var tableDepth = 0
        var tableRows = mutableListOf<List<String>>()
        var currentRow: MutableList<String>? = null
        val cellText = StringBuilder()
        var inCell = false

        fun flushParagraph() {
            if (blocks.size < MAX_BLOCKS) {
                val text = OoxmlSupport.normalizeOrNull(paraText.toString())
                if (text != null) {
                    val style = paraStyle?.lowercase().orEmpty()
                    val block = when {
                        style.startsWith("heading") || style.startsWith("title") -> {
                            val level = style.filter { it.isDigit() }.toIntOrNull() ?: 1
                            DocBlock.Heading(text, level.coerceIn(1, 6))
                        }
                        paraIsList -> DocBlock.Bullet(text, paraIndent.coerceIn(0, 6))
                        else -> DocBlock.Paragraph(text)
                    }
                    blocks.add(block)
                }
            }
            paraText.setLength(0)
            paraStyle = null
            paraIsList = false
            paraIndent = 0
            inParagraph = false
        }

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "w:tbl" -> {
                        if (tableDepth == 0) tableRows = mutableListOf()
                        tableDepth++
                    }
                    "w:tr" -> if (tableDepth > 0) currentRow = mutableListOf()
                    "w:tc" -> if (tableDepth > 0) { inCell = true; cellText.setLength(0) }
                    "w:p" -> if (tableDepth == 0) { inParagraph = true }
                    "w:pStyle" -> if (inParagraph) paraStyle = parser.getAttributeValue(null, "w:val")
                    "w:numPr" -> if (inParagraph) paraIsList = true
                    "w:ilvl" -> if (inParagraph) {
                        paraIndent = parser.getAttributeValue(null, "w:val")?.toIntOrNull() ?: 0
                    }
                    "w:t" -> captureText = true
                    "w:tab" -> {
                        if (inCell) cellText.append(' ') else if (inParagraph) paraText.append(' ')
                    }
                    "w:br", "w:cr" -> {
                        if (inCell) cellText.append(' ') else if (inParagraph) paraText.append('\n')
                    }
                }
                XmlPullParser.TEXT -> if (captureText) {
                    if (inCell) cellText.append(parser.text) else if (inParagraph) paraText.append(parser.text)
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "w:t" -> captureText = false
                    "w:tc" -> if (tableDepth > 0) {
                        currentRow?.let { row ->
                            if (row.size < MAX_COLUMNS) {
                                row.add(OoxmlSupport.normalizeOrNull(cellText.toString()).orEmpty())
                            }
                        }
                        inCell = false
                    }
                    "w:tr" -> if (tableDepth > 0) {
                        currentRow?.let { if (tableRows.size < MAX_ROWS) tableRows.add(it) }
                    }
                    "w:tbl" -> {
                        tableDepth--
                        if (tableDepth == 0) {
                            val nonEmpty = tableRows.filter { row -> row.any { it.isNotBlank() } }
                            if (nonEmpty.isNotEmpty() && blocks.size < MAX_BLOCKS) {
                                blocks.add(DocBlock.Table(nonEmpty, hasHeader = nonEmpty.size > 1))
                            }
                        }
                    }
                    "w:p" -> if (inParagraph) flushParagraph()
                }
            }
            event = parser.next()
        }
        if (inParagraph) flushParagraph()
    }

    private companion object {
        const val MAX_BLOCKS = 50_000
        const val MAX_ROWS = 5_000
        const val MAX_COLUMNS = 64
    }
}
