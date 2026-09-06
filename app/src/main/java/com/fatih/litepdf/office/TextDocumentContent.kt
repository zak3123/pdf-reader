package com.fatih.litepdf.office

/**
 * A lightweight, engine-agnostic representation of an extracted office/text document.
 *
 * LitePDF renders these blocks in a simple, readable vertical list. It intentionally
 * does not attempt pixel-perfect layout reproduction, which would require a heavy
 * office engine and break the app's lightweight, offline design.
 */
data class TextDocumentContent(
    val blocks: List<DocBlock>
) {
    val isEmpty: Boolean get() = blocks.isEmpty() ||
        blocks.all { it is DocBlock.Paragraph && it.text.isBlank() }
}

sealed interface DocBlock {
    /** A section title, e.g. a Word heading or a slide title. */
    data class Heading(val text: String, val level: Int = 1) : DocBlock

    /** A normal paragraph of body text. */
    data class Paragraph(val text: String) : DocBlock

    /** A single bullet/list item. */
    data class Bullet(val text: String, val indent: Int = 0) : DocBlock

    /** A table: the first row is treated as a header when [hasHeader] is true. */
    data class Table(val rows: List<List<String>>, val hasHeader: Boolean = true) : DocBlock

    /** A visual separator between logical sections (e.g. between slides or sheets). */
    data class Divider(val label: String? = null) : DocBlock
}
