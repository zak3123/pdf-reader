package com.fatih.litepdf.domain.repository

import com.fatih.litepdf.domain.model.AppSettings
import com.fatih.litepdf.domain.model.PageSpacing
import com.fatih.litepdf.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setPageSpacing(spacing: PageSpacing)
    suspend fun setKeepScreenAwake(enabled: Boolean)
    suspend fun setRememberLastPage(enabled: Boolean)
    suspend fun setShowPageControls(enabled: Boolean)
}
