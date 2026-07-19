package com.fatih.litepdf.data.repository

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import com.fatih.litepdf.data.database.BookmarkDao
import com.fatih.litepdf.data.database.RecentDocumentDao
import com.fatih.litepdf.data.model.BookmarkEntity
import com.fatih.litepdf.data.model.RecentDocumentEntity
import com.fatih.litepdf.domain.model.Bookmark
import com.fatih.litepdf.domain.model.RecentDocument
import com.fatih.litepdf.domain.repository.DocumentRepository
import com.fatih.litepdf.domain.repository.OpenDocumentResult
import com.fatih.litepdf.domain.repository.OpenFailureReason
import com.fatih.litepdf.pdf.PdfEngine
import com.fatih.litepdf.util.stableDocumentId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.IOException

class RoomDocumentRepository(
    private val contentResolver: ContentResolver,
    private val recentDao: RecentDocumentDao,
    private val bookmarkDao: BookmarkDao,
    private val pdfEngine: PdfEngine,
    private val clock: () -> Long = { System.currentTimeMillis() }
) : DocumentRepository {

    override val recentDocuments: Flow<List<RecentDocument>> =
        recentDao.observeAll().map { entities -> entities.map { it.asDomain() } }

    override suspend fun openDocument(uri: Uri, replaceDocumentId: String?): OpenDocumentResult =
        withContext(Dispatchers.IO) {
            val uriString = uri.toString()
            val metadata = queryMetadata(uri)
            if (metadata.sizeBytes == 0L) {
                return@withContext OpenDocumentResult.Failure(OpenFailureReason.EmptyFile)
            }

            val pageCount = try {
                pdfEngine.open(uri).use { session -> session.info.pageCount }
            } catch (file: FileNotFoundException) {
                return@withContext OpenDocumentResult.Failure(OpenFailureReason.AccessUnavailable)
            } catch (security: SecurityException) {
                return@withContext OpenDocumentResult.Failure(OpenFailureReason.AccessUnavailable)
            } catch (io: IOException) {
                return@withContext OpenDocumentResult.Failure(OpenFailureReason.ProviderError)
            } catch (argument: IllegalArgumentException) {
                return@withContext OpenDocumentResult.Failure(OpenFailureReason.UnsupportedOrCorrupt)
            } catch (state: IllegalStateException) {
                return@withContext OpenDocumentResult.Failure(OpenFailureReason.UnsupportedOrCorrupt)
            }

            if (pageCount <= 0) {
                return@withContext OpenDocumentResult.Failure(OpenFailureReason.UnsupportedOrCorrupt)
            }

            val documentId = replaceDocumentId ?: stableDocumentId(uriString)
            val existing = recentDao.getById(documentId)
            val document = RecentDocumentEntity(
                id = documentId,
                uriString = uriString,
                displayName = metadata.displayName,
                lastOpenedAt = clock(),
                lastViewedPage = existing?.lastViewedPage?.coerceIn(0, pageCount - 1) ?: 0,
                pageCount = pageCount,
                sizeBytes = metadata.sizeBytes,
                lastKnownModified = metadata.lastModified
            )
            recentDao.upsert(document)
            OpenDocumentResult.Success(document.asDomain())
        }

    override suspend fun reopenDocument(documentId: String): OpenDocumentResult {
        val existing = recentDao.getById(documentId)
            ?: return OpenDocumentResult.Failure(OpenFailureReason.AccessUnavailable)
        return openDocument(Uri.parse(existing.uriString), replaceDocumentId = documentId)
    }

    override suspend fun removeRecent(documentId: String) {
        recentDao.deleteById(documentId)
    }

    override suspend fun clearRecents() {
        recentDao.clear()
    }

    override suspend fun updateLastViewedPage(documentId: String, pageIndex: Int) {
        if (pageIndex >= 0) recentDao.updateLastViewedPage(documentId, pageIndex)
    }

    override fun observeBookmarks(documentId: String): Flow<List<Bookmark>> =
        bookmarkDao.observeForDocument(documentId).map { list -> list.map { it.asDomain() } }

    override suspend fun bookmarkPage(documentId: String, pageIndex: Int) {
        if (pageIndex < 0) return
        bookmarkDao.insert(
            BookmarkEntity(
                documentId = documentId,
                pageIndex = pageIndex,
                createdAt = clock(),
                label = null
            )
        )
    }

    override suspend fun removeBookmark(documentId: String, pageIndex: Int) {
        bookmarkDao.deletePage(documentId, pageIndex)
    }

    private fun queryMetadata(uri: Uri): DocumentMetadata {
        var displayName = uri.lastPathSegment?.substringAfterLast('/') ?: "document.pdf"
        var sizeBytes: Long? = null
        var lastModified: Long? = null

        val cursor: Cursor? = try {
            contentResolver.query(uri, null, null, null, null)
        } catch (security: SecurityException) {
            null
        }
        cursor?.use {
            if (it.moveToFirst()) {
                displayName = it.stringValue(OpenableColumns.DISPLAY_NAME) ?: displayName
                sizeBytes = it.longValue(OpenableColumns.SIZE)?.takeIf { size -> size >= 0 }
                lastModified = it.longValue("last_modified")?.takeIf { modified -> modified > 0 }
            }
        }

        if (sizeBytes == null) {
            sizeBytes = try {
                contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                    afd.length.takeIf { it >= 0 }
                }
            } catch (throwable: Throwable) {
                null
            }
        }

        return DocumentMetadata(displayName, sizeBytes, lastModified)
    }

    private fun Cursor.stringValue(columnName: String): String? {
        val index = getColumnIndex(columnName)
        return if (index >= 0 && !isNull(index)) getString(index) else null
    }

    private fun Cursor.longValue(columnName: String): Long? {
        val index = getColumnIndex(columnName)
        return if (index >= 0 && !isNull(index)) getLong(index) else null
    }

    private data class DocumentMetadata(
        val displayName: String,
        val sizeBytes: Long?,
        val lastModified: Long?
    )
}
