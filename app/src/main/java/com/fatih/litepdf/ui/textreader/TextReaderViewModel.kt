package com.fatih.litepdf.ui.textreader

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.fatih.litepdf.R
import com.fatih.litepdf.domain.repository.DocumentRepository
import com.fatih.litepdf.domain.repository.OpenDocumentResult
import com.fatih.litepdf.office.DocBlock
import com.fatih.litepdf.office.TextDocumentContent
import com.fatih.litepdf.office.TextDocumentEngine
import com.fatih.litepdf.office.TextExtractionFailure
import com.fatih.litepdf.office.TextExtractionResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TextReaderViewModel(
    private val documentId: String,
    private val repository: DocumentRepository,
    private val textDocumentEngine: TextDocumentEngine
) : ViewModel() {
    private val _uiState = MutableStateFlow(TextReaderUiState())
    val uiState: StateFlow<TextReaderUiState> = _uiState.asStateFlow()

    init {
        open()
    }

    /** Start or clear search query; returns false if nothing found immediately. */
    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        if (query.length < 2) {
            _uiState.update { it.copy(searchResults = emptyList(), currentMatchIndex = -1) }
            return
        }
        // Compute matches on background dispatcher
        viewModelScope.launch {
            val content = _uiState.value.content ?: return@launch
            val results = computeSearchResults(content, query)
            _uiState.update {
                it.copy(
                    searchResults = results,
                    currentMatchIndex = if (results.isEmpty()) -1 else 0,
                    currentPositionLabel = null // reset until scrolling
                )
            }
        }
    }

    private fun computeSearchResults(content: TextDocumentContent, query: String): List<SearchMatch> {
        val q = query.trim().ifEmpty { return emptyList() }
        val matches = mutableListOf<SearchMatch>()
        val blocks = content.blocks
        var sectionLabel: String? = null

        for ((index, block) in blocks.withIndex()) {
            val label = when (block) {
                is DocBlock.Divider -> block.label.orEmpty()
                else -> sectionLabel.takeIf { it?.isNotBlank() == true }
            }.takeIf { !it.isNullOrBlank() }

            val textLines = extractTextLines(block)
            for (line in textLines) {
                val lower = line.lowercase()
                var pos = 0
                while (true) {
                    val idx = lower.indexOf(q, pos)
                    if (idx < 0) break
                    val start = idx
                    val end = start + q.length
                    // Use case-insensitive char positions
                    val actualStart = findCharPos(line, q, start)
                    val match = SearchMatch(
                        blockIndex = index,
                        searchText = line.substring(actualStart, actualStart + q.length),
                        highlightStart = actualStart,
                        highlightEnd = actualStart + q.length,
                        label = if (!label.isNullOrBlank()) label else findClosestHeadingLabel(blocks, index)
                    )
                    matches.add(match)
                    // Prevent infinite loop
                    pos = maxOf(start + 1, end)
                }
            }
            // Update label for following blocks
            val dividerLabel = when (block) {
                is DocBlock.Divider -> block.label.orEmpty()
                else -> null
            }
            if (!dividerLabel.isNullOrBlank()) sectionLabel = dividerLabel
        }
        return matches
    }

    private fun extractTextLines(block: DocBlock): List<String> = when (block) {
        is DocBlock.Heading -> listOf(block.text)
        is DocBlock.Paragraph -> listOf(block.text)
        is DocBlock.Bullet -> listOf(block.text)
        is DocBlock.Table -> {
            val rows = block.rows
            if (rows.isEmpty()) emptyList()
            else rows.map { it.joinToString(" ") }
        }
        is DocBlock.Divider -> emptyList()
    }

    private fun findCharPos(text: String, pattern: String, startIndex: Int): Int = startIndex

    private fun findClosestHeadingLabel(blocks: List<DocBlock>, afterIndex: Int): String? {
        var i = afterIndex - 1
        while (i >= 0) {
            when (val b = blocks[i]) {
                is DocBlock.Heading -> return b.text
                is DocBlock.Divider -> break
                else -> {}
            }
            i--
        }
        return null
    }

    fun goNextMatch() {
        val state = _uiState.value
        val total = state.searchResults.size
        if (total <= 0) return
        val next = (state.currentMatchIndex + 1).coerceAtMost(total - 1)
        _uiState.update { it.copy(currentMatchIndex = next) }
    }

    fun goPrevMatch() {
        val state = _uiState.value
        val total = state.searchResults.size
        if (total <= 0) return
        val prev = if (state.currentMatchIndex <= 0) total - 1 else state.currentMatchIndex - 1
        _uiState.update { it.copy(currentMatchIndex = prev) }
    }

    fun toggleSearchBar(show: Boolean) {
        if (!show) _uiState.update { it.copy(showSearchBar = false, searchQuery = "") }
        else _uiState.update { it.copy(showSearchBar = true) }
    }


    private fun open() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, openError = false, openErrorMessage = null) }
            when (val result = repository.reopenDocument(documentId)) {
                is OpenDocumentResult.Success -> {
                    val document = result.document
                    _uiState.update { it.copy(displayName = document.displayName) }
                    val extraction = textDocumentEngine.extract(
                        uri = Uri.parse(document.uriString),
                        kind = document.kind,
                        displayName = document.displayName
                    )
                    when (extraction) {
                        is TextExtractionResult.Success -> _uiState.update {
                            it.copy(isLoading = false, content = extraction.content, openError = false)
                        }
                        is TextExtractionResult.Failure -> _uiState.update {
                            it.copy(
                                isLoading = false,
                                openError = true,
                                openErrorMessage = extraction.reason.toMessageRes()
                            )
                        }
                    }
                }
                is OpenDocumentResult.Failure -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        openError = true,
                        openErrorMessage = R.string.access_lost_title
                    )
                }
            }
        }
    }

    fun retry() = open()

    private fun TextExtractionFailure.toMessageRes(): Int = when (this) {
        TextExtractionFailure.UnsupportedFormat -> R.string.unsupported_office_legacy
        TextExtractionFailure.CorruptOrInvalid -> R.string.unsupported_office_corrupt
        TextExtractionFailure.ProviderError -> R.string.provider_error
        TextExtractionFailure.Empty -> R.string.office_empty
    }

    class Factory(
        private val documentId: String,
        private val repository: DocumentRepository,
        private val textDocumentEngine: TextDocumentEngine
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            TextReaderViewModel(documentId, repository, textDocumentEngine) as T
    }
}
