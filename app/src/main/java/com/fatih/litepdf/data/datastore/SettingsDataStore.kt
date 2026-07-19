package com.fatih.litepdf.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.fatih.litepdf.domain.model.AppSettings
import com.fatih.litepdf.domain.model.PageSpacing
import com.fatih.litepdf.domain.model.ThemeMode
import com.fatih.litepdf.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore("settings")

class SettingsDataStore(context: Context) : SettingsRepository {
    private val store = context.applicationContext.settingsStore

    override val settings: Flow<AppSettings> = store.data.map { preferences ->
        AppSettings(
            themeMode = preferences[Keys.theme]?.let(::safeThemeMode) ?: ThemeMode.System,
            pageSpacing = preferences[Keys.spacing]?.let(::safePageSpacing) ?: PageSpacing.Normal,
            keepScreenAwake = preferences[Keys.keepAwake] ?: false,
            rememberLastPage = preferences[Keys.rememberLastPage] ?: true,
            showPageControls = preferences[Keys.showPageControls] ?: true
        )
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[Keys.theme] = mode.name }
    }

    override suspend fun setPageSpacing(spacing: PageSpacing) {
        store.edit { it[Keys.spacing] = spacing.name }
    }

    override suspend fun setKeepScreenAwake(enabled: Boolean) {
        store.edit { it[Keys.keepAwake] = enabled }
    }

    override suspend fun setRememberLastPage(enabled: Boolean) {
        store.edit { it[Keys.rememberLastPage] = enabled }
    }

    override suspend fun setShowPageControls(enabled: Boolean) {
        store.edit { it[Keys.showPageControls] = enabled }
    }

    private fun safeThemeMode(value: String): ThemeMode =
        ThemeMode.entries.firstOrNull { it.name == value } ?: ThemeMode.System

    private fun safePageSpacing(value: String): PageSpacing =
        PageSpacing.entries.firstOrNull { it.name == value } ?: PageSpacing.Normal

    private object Keys {
        val theme = stringPreferencesKey("theme")
        val spacing = stringPreferencesKey("spacing")
        val keepAwake = booleanPreferencesKey("keep_screen_awake")
        val rememberLastPage = booleanPreferencesKey("remember_last_page")
        val showPageControls = booleanPreferencesKey("show_page_controls")
    }
}
