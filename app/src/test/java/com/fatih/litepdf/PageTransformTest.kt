package com.fatih.litepdf

import com.fatih.litepdf.ui.reader.PageTransform
import com.fatih.litepdf.ui.reader.ZoomMode
import org.junit.Assert.assertEquals
import org.junit.Test

class PageTransformTest {
    @Test
    fun rotation90SwapsPageDimensions() {
        val transform = PageTransform(
            originalWidth = 600f,
            originalHeight = 800f,
            viewportWidth = 300f,
            viewportHeight = 500f,
            pdfMetadataRotation = 90,
            manualRotation = 0,
            zoomMode = ZoomMode.FitPage
        )

        assertEquals(90, transform.effectiveRotation)
        assertEquals(800f, transform.rotatedWidth)
        assertEquals(600f, transform.rotatedHeight)
    }

    @Test
    fun fitWidthUsesRotatedWidth() {
        val transform = PageTransform(
            originalWidth = 600f,
            originalHeight = 800f,
            viewportWidth = 400f,
            viewportHeight = 900f,
            pdfMetadataRotation = 270,
            manualRotation = 0,
            zoomMode = ZoomMode.FitWidth
        )

        assertEquals(270, transform.effectiveRotation)
        assertEquals(0.5f, transform.baseScale)
    }

    @Test
    fun fitPageUsesSmallerViewportScale() {
        val transform = PageTransform(
            originalWidth = 600f,
            originalHeight = 800f,
            viewportWidth = 300f,
            viewportHeight = 300f,
            pdfMetadataRotation = 0,
            manualRotation = 0,
            zoomMode = ZoomMode.FitPage
        )

        assertEquals(0.375f, transform.baseScale)
    }
}
