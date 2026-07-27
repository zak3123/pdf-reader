package com.fatih.litepdf.ui.reader

import android.graphics.Bitmap
import com.fatih.litepdf.domain.model.Bookmark
import com.fatih.litepdf.domain.model.RecentDocument
import com.fatih.litepdf.pdf.PdfOutlineItem
import com.fatih.litepdf.pdf.PdfPageLink
import com.fatih.litepdf.pdf.PdfRenderFailure
import com.fatih.litepdf.pdf.PdfSearchFailure
import com.fatih.litepdf.pdf.PdfSearchHit
import com.fatih.litepdf.pdf.PdfWordHighlight

data class ReaderUiState(
    val document: RecentDocument? = null,
    val pageCount: Int = 0,
    val currentPage: Int = 0,
    val toolbarVisible: Boolean = true,
    val isOpening: Boolean = true,
    val openError: Boolean = false,
    val pageStates: Map<Int, PageRenderState> = emptyMap(),
    val pageAspectRatios: Map<Int, Float> = emptyMap(),
    val pageThumbnails: Map<Int, Bitmap> = emptyMap(),
    val outline: List<PdfOutlineItem> = emptyList(),
    val linksByPage: Map<Int, List<PdfPageLink>> = emptyMap(),
    val bookmarks: List<Bookmark> = emptyList(),
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val searchHits: List<PdfSearchHit> = emptyList(),
    val activeSearchPageIndex: Int? = null,
    val searchCompleted: Boolean = false,
    val searchFailure: PdfSearchFailure? = null
) {
    val currentPageBookmarked: Boolean = bookmarks.any { it.pageIndex == currentPage }
    val currentSearchHighlights: List<PdfWordHighlight>
        get() = searchHighlightsForPage(currentPage)

    fun searchHighlightsForPage(pageIndex: Int): List<PdfWordHighlight> =
        if (activeSearchPageIndex == pageIndex) {
            searchHits.firstOrNull { it.pageIndex == pageIndex }?.highlights.orEmpty()
        } else {
            emptyList()
        }
}

sealed interface PageRenderState {
    data object Loading : PageRenderState
    data class Ready(val bitmap: Bitmap) : PageRenderState
    data class Failed(val reason: PdfRenderFailure) : PageRenderState
}
