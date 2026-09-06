package com.fatih.litepdf.office

import android.content.ContentResolver
import android.net.Uri
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream

/**
 * Extracts readable content from a PowerPoint .pptx package (OOXML).
 *
 * Each slide lives in ppt/slides/slideN.xml. Text is stored in `a:t` elements grouped by
 * `a:p` paragraphs inside shapes (`p:sp`). The first non-empty paragraph of a slide is
 * treated as its title heading; the rest become bullets. Slides are separated by dividers.
 *
 * Legacy binary .ppt (OLE2) is not an OOXML package and is not supported here.
 */
class PptxParser(private val contentResolver: ContentResolver) {

    private val slideRegex = Regex("ppt/slides/slide(\\d+)\\.xml")

    fun parse(uri: Uri): TextDocumentContent {
        val entries = OoxmlSupport.readEntries(contentResolver, uri) { name ->
            slideRegex.matches(name)
        }
        if (entries.isEmpty()) {
            throw java.io.IOException("No slides found; not a valid .pptx")
        }

        // Sort slides in natural numeric order (slide2 before slide10).
        val ordered = entries.entries.sortedBy { (name, _) ->
            slideRegex.find(name)?.groupValues?.get(1)?.toIntOrNull() ?: Int.MAX_VALUE
        }

        val blocks = mutableListOf<DocBlock>()
        ordered.forEachIndexed { index, (_, bytes) ->
            val paragraphs = parseSlideParagraphs(bytes)
            blocks.add(DocBlock.Divider(label = "Slide ${index + 1}"))
            var titleAssigned = false
            for (para in paragraphs) {
                val text = OoxmlSupport.normalizeOrNull(para) ?: continue
                if (!titleAssigned) {
                    blocks.add(DocBlock.Heading(text, level = 2))
                    titleAssigned = true
                } else {
                    blocks.add(DocBlock.Bullet(text))
                }
            }
        }
        return TextDocumentContent(blocks)
    }

    /** Return one string per `a:p` paragraph on the slide. */
    private fun parseSlideParagraphs(bytes: ByteArray): List<String> {
        val paragraphs = mutableListOf<String>()
        val current = StringBuilder()
        var captureText = false

        val parser = OoxmlSupport.newPullParser()
        parser.setInput(ByteArrayInputStream(bytes), null)
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "a:p" -> current.setLength(0)
                    "a:t" -> captureText = true
                    "a:br" -> current.append('\n')
                }
                XmlPullParser.TEXT -> if (captureText) current.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name) {
                    "a:t" -> captureText = false
                    "a:p" -> paragraphs.add(current.toString())
                }
            }
            event = parser.next()
        }
        return paragraphs
    }
}
