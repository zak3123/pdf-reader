package com.fatih.litepdf

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.fatih.litepdf.data.database.LitePdfDatabase
import com.fatih.litepdf.data.repository.RoomDocumentRepository
import com.fatih.litepdf.domain.repository.OpenDocumentResult
import com.fatih.litepdf.domain.repository.OpenFailureReason
import com.fatih.litepdf.pdf.PdfDocumentInfo
import com.fatih.litepdf.pdf.PdfDocumentSession
import com.fatih.litepdf.pdf.PdfEngine
import com.fatih.litepdf.pdf.PdfPageSize
import com.fatih.litepdf.pdf.PdfRenderResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner
import java.io.FileNotFoundException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RoomDocumentRepositoryTest {
    private lateinit var database: LitePdfDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, LitePdfDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun openDocumentStoresRecentAndPersistsLastPage() = runTest {
        val repository = repository(FakePdfEngine(pageCount = 7))
        val result = repository.openDocument(Uri.parse("content://example/document.pdf"))

        assertTrue(result is OpenDocumentResult.Success)
        val document = (result as OpenDocumentResult.Success).document
        repository.updateLastViewedPage(document.id, 4)

        val recents = repository.recentDocuments.first()
        assertEquals(1, recents.size)
        assertEquals(4, recents.first().lastViewedPage)
        assertEquals(7, recents.first().pageCount)
    }

    @Test
    fun bookmarkInsertionAndDeletionAreStored() = runTest {
        val repository = repository(FakePdfEngine(pageCount = 3))
        val document = (repository.openDocument(Uri.parse("content://example/bookmarks.pdf")) as OpenDocumentResult.Success).document

        repository.bookmarkPage(document.id, 1)
        assertEquals(listOf(1), repository.observeBookmarks(document.id).first().map { it.pageIndex })

        repository.removeBookmark(document.id, 1)
        assertTrue(repository.observeBookmarks(document.id).first().isEmpty())
    }

    @Test
    fun inaccessibleUriReturnsAccessUnavailable() = runTest {
        val repository = repository(FakePdfEngine(pageCount = 0, inaccessible = true))
        val result = repository.openDocument(Uri.parse("content://example/missing.pdf"))

        assertEquals(
            OpenFailureReason.AccessUnavailable,
            (result as OpenDocumentResult.Failure).reason
        )
    }

    private fun repository(engine: PdfEngine): RoomDocumentRepository {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return RoomDocumentRepository(
            contentResolver = context.contentResolver,
            recentDao = database.recentDocumentDao(),
            bookmarkDao = database.bookmarkDao(),
            pdfEngine = engine,
            clock = { 1_000L }
        )
    }

    private class FakePdfEngine(
        private val pageCount: Int,
        private val inaccessible: Boolean = false
    ) : PdfEngine {
        override suspend fun open(uri: Uri): PdfDocumentSession {
            if (inaccessible) throw FileNotFoundException()
            return object : PdfDocumentSession {
                override val info: PdfDocumentInfo = PdfDocumentInfo(pageCount)
                override suspend fun pageSize(pageIndex: Int): PdfPageSize = PdfPageSize(100, 140)
                override suspend fun renderPage(pageIndex: Int, targetWidthPx: Int): PdfRenderResult =
                    PdfRenderResult.Failure(com.fatih.litepdf.pdf.PdfRenderFailure.Unsupported)
                override fun close() = Unit
            }
        }
    }
}
