package com.fatih.litepdf.pdf

import android.content.ContentResolver
import android.net.Uri
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionGoTo
import com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionURI
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PdfDocumentStructure(
    val outline: List<PdfOutlineItem> = emptyList(),
    val linksByPage: Map<Int, List<PdfPageLink>> = emptyMap()
)

data class PdfOutlineItem(
    val title: String,
    val pageIndex: Int,
    val depth: Int
)

data class PdfPageLink(
    val bounds: PdfWordHighlight,
    val targetPageIndex: Int? = null,
    val uri: String? = null
)

class PdfDocumentStructureReader(
    private val contentResolver: ContentResolver
) {
    suspend fun read(uri: Uri): PdfDocumentStructure = withContext(Dispatchers.IO) {
        val inputStream = contentResolver.openInputStream(uri) ?: return@withContext PdfDocumentStructure()
        try {
            inputStream.use {
                PDDocument.load(it).use { document ->
                    if (document.isEncrypted) return@withContext PdfDocumentStructure()
                    PdfDocumentStructure(
                        outline = document.readOutlineItems(),
                        linksByPage = document.readLinksByPage()
                    )
                }
            }
        } catch (_: Throwable) {
            PdfDocumentStructure()
        }
    }

    private fun PDDocument.readOutlineItems(): List<PdfOutlineItem> {
        val root = documentCatalog.documentOutline ?: return emptyList()
        val items = mutableListOf<PdfOutlineItem>()
        fun visit(item: PDOutlineItem?, depth: Int) {
            var current = item
            while (current != null) {
                val pageIndex = runCatching {
                    getPages().indexOf(current.findDestinationPage(this))
                }.getOrDefault(-1)
                val title = current.title.orEmpty().trim()
                if (title.isNotEmpty() && pageIndex >= 0) {
                    items += PdfOutlineItem(title = title, pageIndex = pageIndex, depth = depth)
                }
                visit(current.firstChild, depth + 1)
                current = current.nextSibling
            }
        }
        visit(root.firstChild, depth = 0)
        return items
    }

    private fun PDDocument.readLinksByPage(): Map<Int, List<PdfPageLink>> {
        val pages = getPages()
        val result = mutableMapOf<Int, List<PdfPageLink>>()
        for (pageIndex in 0 until numberOfPages) {
            val page = getPage(pageIndex)
            val links = page.annotations
                .filterIsInstance<PDAnnotationLink>()
                .mapNotNull { annotation -> annotation.toPageLink(this, page) }
            if (links.isNotEmpty()) result[pageIndex] = links
        }
        return result
    }

    private fun PDAnnotationLink.toPageLink(document: PDDocument, page: PDPage): PdfPageLink? {
        val destination = destination ?: (action as? PDActionGoTo)?.destination
        val targetPageIndex = destination?.toPageIndex(document)
        val uri = (action as? PDActionURI)?.uri
        if (targetPageIndex == null && uri.isNullOrBlank()) return null
        val bounds = rectangle?.toNormalizedBounds(page.cropBox ?: page.mediaBox, page.rotation)
            ?: return null
        return PdfPageLink(bounds = bounds, targetPageIndex = targetPageIndex, uri = uri)
    }

    private fun PDDestination.toPageIndex(document: PDDocument): Int? {
        val destination = this as? PDPageDestination ?: return null
        val pageNumber = destination.pageNumber
        if (pageNumber >= 0) return pageNumber
        val page = destination.page ?: return null
        return document.getPages().indexOf(page).takeIf { it >= 0 }
    }
}

private fun PDRectangle.toNormalizedBounds(pageBox: PDRectangle, rotation: Int): PdfWordHighlight {
    val pageWidth = pageBox.width.takeIf { it > 0f } ?: 1f
    val pageHeight = pageBox.height.takeIf { it > 0f } ?: 1f
    val left = (lowerLeftX - pageBox.lowerLeftX) / pageWidth
    val top = 1f - ((upperRightY - pageBox.lowerLeftY) / pageHeight)
    val right = (upperRightX - pageBox.lowerLeftX) / pageWidth
    val bottom = 1f - ((lowerLeftY - pageBox.lowerLeftY) / pageHeight)
    return PdfWordHighlight(
        normX = left.coerceIn(0f, 1f),
        normY = top.coerceIn(0f, 1f),
        normW = (right - left).coerceAtLeast(0f).coerceAtMost(1f),
        normH = (bottom - top).coerceAtLeast(0f).coerceAtMost(1f)
    ).rotateForStructure(rotation)
}

private fun PdfWordHighlight.rotateForStructure(rotation: Int): PdfWordHighlight =
    when (((rotation % 360) + 360) % 360) {
        90 -> PdfWordHighlight(1f - (normY + normH), normX, normH, normW)
        180 -> PdfWordHighlight(1f - (normX + normW), 1f - (normY + normH), normW, normH)
        270 -> PdfWordHighlight(normY, 1f - (normX + normW), normH, normW)
        else -> this
    }
