package com.fatih.litepdf.ui.textreader

import com.fatih.litepdf.domain.model.RecentDocument
import com.fatih.litepdf.office.TextDocumentContent

/** The UI state for an Office/Text document reader that shows extracted content. */
data class TextReaderUiState(
    val isLoading: Boolean = true,
    val displayName: String? = null,
    val content: TextDocumentContent? = null,
    /** Error message string resource ID when content extraction fails or access is lost. */
    val openError: Boolean = false,
    val openErrorMessage: Int? = null,
    // --- Search & Navigation ---
    val searchQuery: String = "",
    val searchResults: List<SearchMatch> = emptyList(),
    val currentMatchIndex: Int = -1,
    val showSearchBar: Boolean = false,
    val currentPositionLabel: String? = null,
    val currentBlockIndex: Int = -1,
    val totalBlocks: Int = 0
)

/** A single match within a block, identified by its block index. */
data class SearchMatch(
    val blockIndex: Int,
    val searchText: String,
    val highlightStart: Int? = null, // in plain-text blocks only
    val highlightEnd: Int? = null,   // null for tables or when not applicable
    val label: String? = null        // optional section header above this block
)
