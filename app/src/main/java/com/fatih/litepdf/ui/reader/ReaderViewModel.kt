package com.fatih.litepdf.ui.reader

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.fatih.litepdf.domain.repository.DocumentRepository
import com.fatih.litepdf.domain.repository.OpenDocumentResult
import com.fatih.litepdf.domain.repository.SettingsRepository
import com.fatih.litepdf.pdf.PdfBitmapCache
import com.fatih.litepdf.pdf.PdfDocumentStructureReader
import com.fatih.litepdf.pdf.PdfDocumentSession
import com.fatih.litepdf.pdf.PdfEngine
import com.fatih.litepdf.pdf.PdfRenderFailure
import com.fatih.litepdf.pdf.PdfRenderResult
import com.fatih.litepdf.pdf.PdfSearchResult
import com.fatih.litepdf.pdf.PdfTextSearchEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ReaderViewModel(
    private val documentId: String,
    private val repository: DocumentRepository,
    private val settingsRepository: SettingsRepository,
    private val pdfEngine: PdfEngine,
    private val textSearchEngine: PdfTextSearchEngine,
    private val bitmapCache: PdfBitmapCache,
    private val thumbnailCache: PdfBitmapCache,
    private val documentStructureReader: PdfDocumentStructureReader
) : ViewModel() {
    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private var session: PdfDocumentSession? = null
    private val renderJobs = mutableMapOf<Int, Job>()
    private val thumbnailJobs = mutableMapOf<Int, Job>()
    private var cacheTrimJob: Job? = null
    private var searchJob: Job? = null
    private var structureJob: Job? = null
    private var lastTargetWidthPx = 0
    private var rememberLastPage = true

    init {
        open()
        viewModelScope.launch {
            settingsRepository.settings.collectLatest { settings ->
                rememberLastPage = settings.rememberLastPage
            }
        }
        viewModelScope.launch {
            repository.observeBookmarks(documentId).collectLatest { bookmarks ->
                _uiState.update { it.copy(bookmarks = bookmarks) }
            }
        }
    }

    private fun open() {
        viewModelScope.launch {
            _uiState.update { it.copy(isOpening = true, openError = false) }
            when (val result = repository.reopenDocument(documentId)) {
                is OpenDocumentResult.Success -> {
                    try {
                        val openedSession = pdfEngine.open(Uri.parse(result.document.uriString))
                        session = openedSession
                        _uiState.update {
                            it.copy(
                                document = result.document,
                                pageCount = openedSession.info.pageCount,
                                currentPage = result.document.lastViewedPage.coerceIn(0, openedSession.info.pageCount - 1),
                                isOpening = false,
                                openError = false
                            )
                        }
                        loadDocumentStructure(Uri.parse(result.document.uriString))
                    } catch (throwable: Throwable) {
                        _uiState.update { it.copy(isOpening = false, openError = true) }
                    }
                }
                is OpenDocumentResult.Failure -> {
                    _uiState.update { it.copy(isOpening = false, openError = true) }
                }
            }
        }
    }

    fun renderPage(pageIndex: Int, targetWidthPx: Int) {
        val document = _uiState.value.document ?: return
        if (pageIndex !in 0 until _uiState.value.pageCount || targetWidthPx <= 0) return
        lastTargetWidthPx = targetWidthPx
        if (kotlin.math.abs(pageIndex - _uiState.value.currentPage) > RENDER_RADIUS) return
        enqueueRender(document.id, pageIndex, targetWidthPx, showLoading = true)
        prefetchAdjacentPages(document.id, targetWidthPx)
    }

    private fun prefetchAdjacentPages(documentId: String, targetWidthPx: Int) {
        val state = _uiState.value
        listOf(state.currentPage + 1, state.currentPage - 1)
            .filter { it in 0 until state.pageCount }
            .forEach { pageIndex ->
                enqueueRender(documentId, pageIndex, targetWidthPx, showLoading = false)
            }
    }

    private fun enqueueRender(
        documentId: String,
        pageIndex: Int,
        targetWidthPx: Int,
        showLoading: Boolean
    ) {
        bitmapCache.get(documentId, pageIndex, targetWidthPx)?.let { bitmap ->
            _uiState.update { state ->
                state.copy(
                    pageStates = state.pageStates + (pageIndex to PageRenderState.Ready(bitmap)),
                    pageAspectRatios = state.pageAspectRatios + (
                        pageIndex to bitmap.width.toFloat() / bitmap.height.toFloat()
                    )
                )
            }
            return
        }
        if (renderJobs[pageIndex]?.isActive == true) return

        if (showLoading) {
            _uiState.update { state ->
                state.copy(pageStates = state.pageStates + (pageIndex to PageRenderState.Loading))
            }
        }
        renderJobs[pageIndex] = viewModelScope.launch {
            try {
                when (val result = session?.renderPage(pageIndex, targetWidthPx)) {
                    is PdfRenderResult.Success -> {
                        if (kotlin.math.abs(pageIndex - _uiState.value.currentPage) > RENDER_RADIUS) {
                            result.page.bitmap.recycle()
                            return@launch
                        }
                        bitmapCache.put(documentId, pageIndex, targetWidthPx, result.page.bitmap)
                        if (result.page.bitmap.isRecycled) {
                            _uiState.update { state ->
                                state.copy(
                                    pageStates = state.pageStates + (
                                        pageIndex to PageRenderState.Failed(PdfRenderFailure.TooLarge)
                                    )
                                )
                            }
                            return@launch
                        }
                        _uiState.update { state ->
                            state.copy(
                                pageStates = state.pageStates + (
                                    pageIndex to PageRenderState.Ready(result.page.bitmap)
                                ),
                                pageAspectRatios = state.pageAspectRatios + (
                                    pageIndex to result.page.bitmap.width.toFloat() /
                                        result.page.bitmap.height.toFloat()
                                )
                            )
                        }
                    }
                    is PdfRenderResult.Failure -> {
                        _uiState.update { state ->
                            state.copy(
                                pageStates = state.pageStates + (
                                    pageIndex to PageRenderState.Failed(result.reason)
                                )
                            )
                        }
                    }
                    null -> Unit
                }
            } finally {
                val currentJob = currentCoroutineContext()[Job]
                if (renderJobs[pageIndex] === currentJob) {
                    renderJobs.remove(pageIndex)
                }
            }
        }
    }

    private fun loadDocumentStructure(uri: Uri) {
        structureJob?.cancel()
        structureJob = viewModelScope.launch {
            val structure = documentStructureReader.read(uri)
            _uiState.update {
                it.copy(outline = structure.outline, linksByPage = structure.linksByPage)
            }
        }
    }

    fun renderThumbnail(pageIndex: Int) {
        val document = _uiState.value.document ?: return
        if (pageIndex !in 0 until _uiState.value.pageCount) return
        thumbnailCache.get(document.id, pageIndex, THUMBNAIL_WIDTH_PX)?.let { bitmap ->
            _uiState.update { state ->
                state.copy(pageThumbnails = state.pageThumbnails + (pageIndex to bitmap))
            }
            return
        }
        if (thumbnailJobs[pageIndex]?.isActive == true) return
        thumbnailJobs[pageIndex] = viewModelScope.launch {
            try {
                when (val result = session?.renderPage(pageIndex, THUMBNAIL_WIDTH_PX)) {
                    is PdfRenderResult.Success -> {
                        thumbnailCache.put(document.id, pageIndex, THUMBNAIL_WIDTH_PX, result.page.bitmap)
                        if (!result.page.bitmap.isRecycled) {
                            _uiState.update { state ->
                                state.copy(
                                    pageThumbnails = state.pageThumbnails + (pageIndex to result.page.bitmap)
                                )
                            }
                        }
                    }
                    else -> Unit
                }
            } finally {
                val currentJob = currentCoroutineContext()[Job]
                if (thumbnailJobs[pageIndex] === currentJob) {
                    thumbnailJobs.remove(pageIndex)
                }
            }
        }
    }

    fun onVisiblePageChanged(pageIndex: Int) {
        if (pageIndex !in 0 until _uiState.value.pageCount) return
        renderJobs
            .filterKeys { kotlin.math.abs(it - pageIndex) > RENDER_RADIUS }
            .toList()
            .forEach { (index, job) ->
                job.cancel()
                renderJobs.remove(index)
            }
        _uiState.update { state ->
            state.copy(
                currentPage = pageIndex,
                pageStates = state.pageStates.filterKeys {
                    kotlin.math.abs(it - pageIndex) <= bitmapCache.retentionRadius
                }
            )
        }
        _uiState.value.document?.let { document ->
            cacheTrimJob?.cancel()
            cacheTrimJob = viewModelScope.launch {
                delay(CACHE_TRIM_DELAY_MS)
                bitmapCache.trimToVisibleRange(document.id, pageIndex)
            }
            if (rememberLastPage) {
                viewModelScope.launch { repository.updateLastViewedPage(document.id, pageIndex) }
            }
            if (lastTargetWidthPx > 0) {
                prefetchAdjacentPages(document.id, lastTargetWidthPx)
            }
        }
    }

    fun toggleToolbar() {
        _uiState.update { it.copy(toolbarVisible = !it.toolbarVisible) }
    }

    fun bookmarkCurrentPage() {
        val state = _uiState.value
        val document = state.document ?: return
        viewModelScope.launch {
            if (state.currentPageBookmarked) {
                repository.removeBookmark(document.id, state.currentPage)
            } else {
                repository.bookmarkPage(document.id, state.currentPage)
            }
        }
    }

    fun removeBookmark(pageIndex: Int) {
        val document = _uiState.value.document ?: return
        viewModelScope.launch { repository.removeBookmark(document.id, pageIndex) }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update {
            it.copy(
                searchQuery = query,
                activeSearchPageIndex = null,
                searchFailure = null,
                searchCompleted = false
            )
        }
    }

    fun searchText() {
        val document = _uiState.value.document ?: return
        val query = _uiState.value.searchQuery
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSearching = true,
                    searchFailure = null,
                    searchCompleted = false,
                    searchHits = emptyList(),
                    activeSearchPageIndex = null
                )
            }
            when (val result = textSearchEngine.search(Uri.parse(document.uriString), query)) {
                is PdfSearchResult.Success -> {
                    _uiState.update {
                        it.copy(isSearching = false, searchHits = result.hits, searchCompleted = true, searchFailure = null)
                    }
                }
                is PdfSearchResult.Failure -> {
                    _uiState.update {
                        it.copy(isSearching = false, searchHits = emptyList(), searchCompleted = true, searchFailure = result.reason)
                    }
                }
            }
        }
    }

    fun selectSearchHit(pageIndex: Int) {
        if (_uiState.value.searchHits.none { it.pageIndex == pageIndex }) return
        _uiState.update { it.copy(activeSearchPageIndex = pageIndex) }
    }

    override fun onCleared() {
        renderJobs.values.forEach { it.cancel() }
        renderJobs.clear()
        thumbnailJobs.values.forEach { it.cancel() }
        thumbnailJobs.clear()
        cacheTrimJob?.cancel()
        searchJob?.cancel()
        structureJob?.cancel()
        _uiState.value.document?.let { document ->
            textSearchEngine.clearCache(Uri.parse(document.uriString))
        }
        session?.close()
        session = null
        bitmapCache.clear()
        thumbnailCache.clear()
        super.onCleared()
    }

    class Factory(
        private val documentId: String,
        private val repository: DocumentRepository,
        private val settingsRepository: SettingsRepository,
        private val pdfEngine: PdfEngine,
        private val textSearchEngine: PdfTextSearchEngine,
        private val bitmapCache: PdfBitmapCache,
        private val thumbnailCache: PdfBitmapCache,
        private val documentStructureReader: PdfDocumentStructureReader
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ReaderViewModel(
                documentId,
                repository,
                settingsRepository,
                pdfEngine,
                textSearchEngine,
                bitmapCache,
                thumbnailCache,
                documentStructureReader
            ) as T
    }

    private companion object {
        const val RENDER_RADIUS = 1
        const val CACHE_TRIM_DELAY_MS = 50L
        const val THUMBNAIL_WIDTH_PX = 150
    }
}
