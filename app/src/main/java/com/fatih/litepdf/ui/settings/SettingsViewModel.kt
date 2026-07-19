package com.fatih.litepdf.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.fatih.litepdf.domain.model.AppSettings
import com.fatih.litepdf.domain.model.PageSpacing
import com.fatih.litepdf.domain.model.ThemeMode
import com.fatih.litepdf.domain.repository.DocumentRepository
import com.fatih.litepdf.domain.repository.SettingsRepository
import com.fatih.litepdf.pdf.PdfBitmapCache
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val documentRepository: DocumentRepository,
    private val bitmapCache: PdfBitmapCache
) : ViewModel() {
    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setPageSpacing(spacing: PageSpacing) {
        viewModelScope.launch { settingsRepository.setPageSpacing(spacing) }
    }

    fun setKeepAwake(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setKeepScreenAwake(enabled) }
    }

    fun setRememberLastPage(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setRememberLastPage(enabled) }
    }

    fun setShowPageControls(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setShowPageControls(enabled) }
    }

    fun clearBitmapCache() {
        bitmapCache.clear()
    }

    fun clearRecents() {
        viewModelScope.launch { documentRepository.clearRecents() }
    }

    class Factory(
        private val settingsRepository: SettingsRepository,
        private val documentRepository: DocumentRepository,
        private val bitmapCache: PdfBitmapCache
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SettingsViewModel(settingsRepository, documentRepository, bitmapCache) as T
    }
}
