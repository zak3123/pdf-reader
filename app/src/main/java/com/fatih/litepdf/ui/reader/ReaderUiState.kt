package com.fatih.litepdf.ui.reader

import android.graphics.Bitmap
import com.fatih.litepdf.domain.model.Bookmark
import com.fatih.litepdf.domain.model.RecentDocument
import com.fatih.litepdf.pdf.PdfRenderFailure
import com.fatih.litepdf.pdf.PdfSearchFailure
import com.fatih.litepdf.pdf.PdfSearchHit

data class ReaderUiState(
    val document: RecentDocument? = null,
    val pageCount: Int = 0,
    val currentPage: Int = 0,
    val toolbarVisible: Boolean = true,
    val isOpening: Boolean = true,
    val openError: Boolean = false,
    val pageStates: Map<Int, PageRenderState> = emptyMap(),
    val bookmarks: List<Bookmark> = emptyList(),
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val searchHits: List<PdfSearchHit> = emptyList(),
    val searchCompleted: Boolean = false,
    val searchFailure: PdfSearchFailure? = null
) {
    val currentPageBookmarked: Boolean = bookmarks.any { it.pageIndex == currentPage }
}

sealed interface PageRenderState {
    data object Loading : PageRenderState
    data class Ready(val bitmap: Bitmap) : PageRenderState
    data class Failed(val reason: PdfRenderFailure) : PageRenderState
}
