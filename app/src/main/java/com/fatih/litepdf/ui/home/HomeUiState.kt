package com.fatih.litepdf.ui.home

import com.fatih.litepdf.domain.model.RecentDocument

data class HomeUiState(
    val recentDocuments: List<RecentDocument> = emptyList(),
    val isOpening: Boolean = false,
    val pendingRelocateDocumentId: String? = null,
    val openError: HomeOpenError? = null
)

enum class HomeOpenError {
    AccessUnavailable,
    EmptyFile,
    UnsupportedOrCorrupt,
    ProviderError
}
