package com.fatih.litepdf.ui.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.fatih.litepdf.domain.repository.DocumentRepository
import com.fatih.litepdf.domain.repository.OpenDocumentResult
import com.fatih.litepdf.domain.repository.OpenFailureReason
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(
    private val repository: DocumentRepository
) : ViewModel() {
    private val transientState = MutableStateFlow(HomeUiState())
    private val openEvents = Channel<String>(Channel.BUFFERED)
    val openedDocuments = openEvents.receiveAsFlow()

    val uiState: StateFlow<HomeUiState> = combine(
        repository.recentDocuments,
        transientState
    ) { recents, transient ->
        transient.copy(recentDocuments = recents)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun openUri(uri: Uri, replaceDocumentId: String? = null) {
        viewModelScope.launch {
            transientState.value = transientState.value.copy(isOpening = true, openError = null)
            when (val result = repository.openDocument(uri, replaceDocumentId)) {
                is OpenDocumentResult.Success -> {
                    transientState.value = transientState.value.copy(
                        isOpening = false,
                        pendingRelocateDocumentId = null,
                        openError = null
                    )
                    openEvents.send(result.document.id)
                }
                is OpenDocumentResult.Failure -> {
                    transientState.value = transientState.value.copy(
                        isOpening = false,
                        pendingRelocateDocumentId = replaceDocumentId,
                        openError = result.reason.toHomeError()
                    )
                }
            }
        }
    }

    fun reopen(documentId: String) {
        viewModelScope.launch {
            transientState.value = transientState.value.copy(isOpening = true, openError = null)
            when (val result = repository.reopenDocument(documentId)) {
                is OpenDocumentResult.Success -> {
                    transientState.value = transientState.value.copy(isOpening = false)
                    openEvents.send(result.document.id)
                }
                is OpenDocumentResult.Failure -> {
                    transientState.value = transientState.value.copy(
                        isOpening = false,
                        pendingRelocateDocumentId = documentId,
                        openError = result.reason.toHomeError()
                    )
                }
            }
        }
    }

    fun dismissRelocate() {
        transientState.value = transientState.value.copy(pendingRelocateDocumentId = null, openError = null)
    }

    fun clearOpenError() {
        transientState.value = transientState.value.copy(openError = null)
    }

    fun removeRecent(documentId: String) {
        viewModelScope.launch { repository.removeRecent(documentId) }
    }

    fun clearRecents() {
        viewModelScope.launch { repository.clearRecents() }
    }

    private fun OpenFailureReason.toHomeError(): HomeOpenError = when (this) {
        OpenFailureReason.AccessUnavailable -> HomeOpenError.AccessUnavailable
        OpenFailureReason.EmptyFile -> HomeOpenError.EmptyFile
        OpenFailureReason.UnsupportedOrCorrupt -> HomeOpenError.UnsupportedOrCorrupt
        OpenFailureReason.ProviderError -> HomeOpenError.ProviderError
    }

    class Factory(private val repository: DocumentRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            HomeViewModel(repository) as T
    }
}
