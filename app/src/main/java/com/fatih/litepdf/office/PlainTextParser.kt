package com.fatih.litepdf.office

import android.content.ContentResolver
import android.net.Uri
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

/**
 * Reads plain-text and CSV files into readable blocks.
 *
 * - .csv is rendered as a [DocBlock.Table].
 * - other text is split into [DocBlock.Paragraph] blocks on blank lines.
 */
class PlainTextParser(private val contentResolver: ContentResolver) {

    private val maxBytes = 8L * 1024 * 1024

    fun parse(uri: Uri, isCsv: Boolean): TextDocumentContent {
        val text = readText(uri)
        val blocks = if (isCsv) parseCsv(text) else parseText(text)
        return TextDocumentContent(blocks)
    }

    private fun readText(uri: Uri): String {
        val stream = OoxmlSupport.openStream(contentResolver, uri)
        BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { reader ->
            val sb = StringBuilder()
            val buffer = CharArray(16 * 1024)
            var total = 0L
            var read: Int
            while (reader.read(buffer).also { read = it } != -1) {
                total += read
                if (total > maxBytes) {
                    sb.append(buffer, 0, read)
                    break
                }
                sb.append(buffer, 0, read)
            }
            // Strip a UTF-8 BOM if present.
            if (sb.isNotEmpty() && sb[0] == '\uFEFF') sb.deleteCharAt(0)
            return sb.toString()
        }
    }

    private fun parseText(text: String): List<DocBlock> {
        val blocks = mutableListOf<DocBlock>()
        val paragraphs = text.replace("\r\n", "\n").split(Regex("\n[ \\t]*\n"))
        for (para in paragraphs) {
            val normalized = para.trim('\n', '\r')
            if (normalized.isBlank()) continue
            blocks.add(DocBlock.Paragraph(normalized.trimEnd()))
        }
        if (blocks.isEmpty()) blocks.add(DocBlock.Paragraph(""))
        return blocks
    }

    private fun parseCsv(text: String): List<DocBlock> {
        val rows = parseCsvRows(text)
        val nonEmpty = rows.filter { row -> row.any { it.isNotBlank() } }
        if (nonEmpty.isEmpty()) return listOf(DocBlock.Paragraph(""))
        return listOf(DocBlock.Table(nonEmpty, hasHeader = nonEmpty.size > 1))
    }

    /** Minimal RFC-4180-style CSV parser supporting quoted fields and embedded commas/newlines. */
    private fun parseCsvRows(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        val s = text.replace("\r\n", "\n").replace('\r', '\n')
        while (i < s.length) {
            val c = s[i]
            when {
                inQuotes -> when {
                    c == '"' && i + 1 < s.length && s[i + 1] == '"' -> { field.append('"'); i++ }
                    c == '"' -> inQuotes = false
                    else -> field.append(c)
                }
                c == '"' -> inQuotes = true
                c == ',' -> { row.add(field.toString()); field.setLength(0) }
                c == '\n' -> {
                    row.add(field.toString()); field.setLength(0)
                    rows.add(row); row = mutableListOf()
                }
                else -> field.append(c)
            }
            i++
        }
        // Flush the final field/row if the file did not end with a newline.
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row.add(field.toString())
            rows.add(row)
        }
        return rows
    }
}
