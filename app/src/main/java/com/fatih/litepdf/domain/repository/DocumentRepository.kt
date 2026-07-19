package com.fatih.litepdf.domain.repository

import android.net.Uri
import com.fatih.litepdf.domain.model.Bookmark
import com.fatih.litepdf.domain.model.RecentDocument
import kotlinx.coroutines.flow.Flow

interface DocumentRepository {
    val recentDocuments: Flow<List<RecentDocument>>

    suspend fun openDocument(uri: Uri, replaceDocumentId: String? = null): OpenDocumentResult
    suspend fun reopenDocument(documentId: String): OpenDocumentResult
    suspend fun removeRecent(documentId: String)
    suspend fun clearRecents()
    suspend fun updateLastViewedPage(documentId: String, pageIndex: Int)
    fun observeBookmarks(documentId: String): Flow<List<Bookmark>>
    suspend fun bookmarkPage(documentId: String, pageIndex: Int)
    suspend fun removeBookmark(documentId: String, pageIndex: Int)
}

sealed interface OpenDocumentResult {
    data class Success(val document: RecentDocument) : OpenDocumentResult
    data class Failure(val reason: OpenFailureReason) : OpenDocumentResult
}

enum class OpenFailureReason {
    AccessUnavailable,
    EmptyFile,
    UnsupportedOrCorrupt,
    ProviderError
}
