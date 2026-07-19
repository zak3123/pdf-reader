package com.fatih.litepdf

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.fatih.litepdf.data.datastore.SettingsDataStore
import com.fatih.litepdf.domain.model.PageSpacing
import com.fatih.litepdf.domain.model.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsDataStoreTest {
    @Test
    fun settingsArePersisted() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = SettingsDataStore(context)

        store.setThemeMode(ThemeMode.Dark)
        store.setPageSpacing(PageSpacing.Relaxed)
        store.setKeepScreenAwake(true)
        store.setRememberLastPage(false)
        store.setShowPageControls(false)

        val settings = store.settings.first()
        assertEquals(ThemeMode.Dark, settings.themeMode)
        assertEquals(PageSpacing.Relaxed, settings.pageSpacing)
        assertTrue(settings.keepScreenAwake)
        assertFalse(settings.rememberLastPage)
        assertFalse(settings.showPageControls)
    }
}
