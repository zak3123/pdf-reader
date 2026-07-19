package com.fatih.litepdf

import android.net.Uri
import app.cash.turbine.test
import com.fatih.litepdf.domain.model.Bookmark
import com.fatih.litepdf.domain.model.RecentDocument
import com.fatih.litepdf.domain.repository.DocumentRepository
import com.fatih.litepdf.domain.repository.OpenDocumentResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    fun successfulOpenEmitsDocumentId() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = com.fatih.litepdf.ui.home.HomeViewModel(repository)

        viewModel.openedDocuments.test {
            viewModel.openUri(Uri.parse("content://example/test.pdf"))
            advanceUntilIdle()
            assertEquals("doc", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    private class FakeRepository : DocumentRepository {
        private val recent = RecentDocument(
            id = "doc",
            uriString = "content://example/test.pdf",
            displayName = "test.pdf",
            lastOpenedAt = 1L,
            lastViewedPage = 0,
            pageCount = 2,
            sizeBytes = null,
            lastKnownModified = null
        )
        override val recentDocuments: Flow<List<RecentDocument>> = MutableStateFlow(emptyList())
        override suspend fun openDocument(uri: Uri, replaceDocumentId: String?): OpenDocumentResult =
            OpenDocumentResult.Success(recent)
        override suspend fun reopenDocument(documentId: String): OpenDocumentResult =
            OpenDocumentResult.Success(recent)
        override suspend fun removeRecent(documentId: String) = Unit
        override suspend fun clearRecents() = Unit
        override suspend fun updateLastViewedPage(documentId: String, pageIndex: Int) = Unit
        override fun observeBookmarks(documentId: String): Flow<List<Bookmark>> = emptyFlow()
        override suspend fun bookmarkPage(documentId: String, pageIndex: Int) = Unit
        override suspend fun removeBookmark(documentId: String, pageIndex: Int) = Unit
    }
}
