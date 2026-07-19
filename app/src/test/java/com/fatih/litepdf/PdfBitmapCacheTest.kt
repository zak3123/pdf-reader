package com.fatih.litepdf

import android.graphics.Bitmap
import com.fatih.litepdf.pdf.PdfBitmapCache
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PdfBitmapCacheTest {
    @Test
    fun cacheEvictsWhenMemoryLimitIsExceeded() {
        val cache = PdfBitmapCache(maxBytes = 400)
        val first = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        val second = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)

        cache.put("doc", 0, 100, first)
        cache.put("doc", 1, 100, second)

        assertNull(cache.get("doc", 0, 100))
        assertNotNull(cache.get("doc", 1, 100))
    }
}
