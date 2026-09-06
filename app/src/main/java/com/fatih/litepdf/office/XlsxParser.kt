package com.fatih.litepdf.office

import android.content.ContentResolver
import android.net.Uri
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream

/**
 * Extracts readable content from an Excel .xlsx package (OOXML).
 *
 * Strings are stored once in xl/sharedStrings.xml; cells in xl/worksheets/sheetN.xml
 * reference them by index when the cell type is "s". Numeric/inline values are read
 * directly. Each worksheet becomes a [DocBlock.Table] preceded by a [DocBlock.Divider].
 *
 * Legacy binary .xls (OLE2) is not an OOXML package and is not supported here.
 */
class XlsxParser(private val contentResolver: ContentResolver) {

    private val sheetRegex = Regex("xl/worksheets/sheet(\\d+)\\.xml")
    private val maxColumns = 64
    private val maxRowsPerSheet = 2000

    fun parse(uri: Uri): TextDocumentContent {
        val entries = OoxmlSupport.readEntries(contentResolver, uri) { name ->
            name == "xl/sharedStrings.xml" || sheetRegex.matches(name)
        }
        val sheetEntries = entries.filterKeys { sheetRegex.matches(it) }
        if (sheetEntries.isEmpty()) {
            throw java.io.IOException("No worksheets found; not a valid .xlsx")
        }

        val sharedStrings = entries["xl/sharedStrings.xml"]?.let { parseSharedStrings(it) } ?: emptyList()

        val ordered = sheetEntries.entries.sortedBy { (name, _) ->
            sheetRegex.find(name)?.groupValues?.get(1)?.toIntOrNull() ?: Int.MAX_VALUE
        }

        val blocks = mutableListOf<DocBlock>()
        ordered.forEachIndexed { index, (_, bytes) ->
            val rows = parseSheet(bytes, sharedStrings)
            if (rows.isNotEmpty()) {
                blocks.add(DocBlock.Divider(label = "Sheet ${index + 1}"))
                blocks.add(DocBlock.Table(rows, hasHeader = rows.size > 1))
            }
        }
        if (blocks.isEmpty()) {
            // Valid workbook but every sheet was empty.
            blocks.add(DocBlock.Paragraph(""))
        }
        return TextDocumentContent(blocks)
    }

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val strings = mutableListOf<String>()
        val current = StringBuilder()
        var inItem = false
        var captureText = false

        val parser = OoxmlSupport.newPullParser()
        parser.setInput(ByteArrayInputStream(bytes), null)
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "si" -> { inItem = true; current.setLength(0) }
                    "t" -> if (inItem) captureText = true
                }
                XmlPullParser.TEXT -> if (captureText) current.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name) {
                    "t" -> captureText = false
                    "si" -> { strings.add(current.toString()); inItem = false }
                }
            }
            event = parser.next()
        }
        return strings
    }

    private fun parseSheet(bytes: ByteArray, sharedStrings: List<String>): List<List<String>> {
        val rows = mutableListOf<MutableList<String>>()
        var currentRow: MutableList<String>? = null
        var cellType: String? = null
        var cellRef: String? = null
        val cellValue = StringBuilder()
        var captureValue = false
        var isInlineString = false

        val parser = OoxmlSupport.newPullParser()
        parser.setInput(ByteArrayInputStream(bytes), null)
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "row" -> currentRow = mutableListOf()
                    "c" -> {
                        cellType = parser.getAttributeValue(null, "t")
                        cellRef = parser.getAttributeValue(null, "r")
                        cellValue.setLength(0)
                        isInlineString = cellType == "inlineStr"
                    }
                    "v" -> captureValue = true
                    "t" -> if (isInlineString) captureValue = true
                }
                XmlPullParser.TEXT -> if (captureValue) cellValue.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name) {
                    "v", "t" -> captureValue = false
                    "c" -> {
                        val row = currentRow
                        if (row != null && row.size < maxColumns) {
                            val resolved = resolveCell(cellType, cellValue.toString(), sharedStrings)
                            val colIndex = cellRef?.let { columnIndexOf(it) } ?: row.size
                            // Pad for gaps between explicit cell references.
                            while (row.size < colIndex && row.size < maxColumns) row.add("")
                            if (row.size < maxColumns) row.add(resolved)
                        }
                    }
                    "row" -> currentRow?.let { if (rows.size < maxRowsPerSheet) rows.add(it) }
                }
            }
            event = parser.next()
        }

        // Normalize ragged rows to the widest row and drop fully-empty trailing rows.
        val width = rows.maxOfOrNull { it.size } ?: 0
        val normalized = rows.map { row ->
            val copy = row.toMutableList()
            while (copy.size < width) copy.add("")
            copy as List<String>
        }
        return normalized.filter { row -> row.any { it.isNotBlank() } }
    }

    private fun resolveCell(type: String?, raw: String, sharedStrings: List<String>): String {
        return when (type) {
            "s" -> raw.trim().toIntOrNull()?.let { sharedStrings.getOrNull(it) }.orEmpty()
            else -> raw
        }
    }

    /** Convert a cell reference like "B7" to a zero-based column index (B -> 1). */
    private fun columnIndexOf(ref: String): Int {
        var index = 0
        for (ch in ref) {
            if (ch in 'A'..'Z') {
                index = index * 26 + (ch - 'A' + 1)
            } else if (ch in 'a'..'z') {
                index = index * 26 + (ch - 'a' + 1)
            } else {
                break
            }
        }
        return (index - 1).coerceAtLeast(0)
    }
}
