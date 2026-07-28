package com.fatih.litepdf.ui.reader

import kotlin.math.min

data class PageTransform(
    val originalWidth: Float,
    val originalHeight: Float,
    val viewportWidth: Float,
    val viewportHeight: Float,
    val pdfMetadataRotation: Int,
    val manualRotation: Int,
    val zoomMode: ZoomMode,
    val userScale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f
) {
    val effectiveRotation: Int = (pdfMetadataRotation + manualRotation).normalizeRotation()
    val rotatedWidth: Float =
        if (effectiveRotation == 90 || effectiveRotation == 270) originalHeight else originalWidth
    val rotatedHeight: Float =
        if (effectiveRotation == 90 || effectiveRotation == 270) originalWidth else originalHeight
    val baseScale: Float = when (zoomMode) {
        ZoomMode.FitPage -> min(
            viewportWidth / rotatedWidth.coerceAtLeast(1f),
            viewportHeight / rotatedHeight.coerceAtLeast(1f)
        )
        ZoomMode.FitWidth -> viewportWidth / rotatedWidth.coerceAtLeast(1f)
        ZoomMode.ActualSize -> 1f
        is ZoomMode.Percent -> zoomMode.value / 100f
    }
    val totalScale: Float = (baseScale * userScale).coerceAtLeast(0.05f)
}

sealed interface ZoomMode {
    data object FitPage : ZoomMode
    data object FitWidth : ZoomMode
    data object ActualSize : ZoomMode
    data class Percent(val value: Float) : ZoomMode
}

fun Int.normalizeRotation(): Int = ((this % 360) + 360) % 360
