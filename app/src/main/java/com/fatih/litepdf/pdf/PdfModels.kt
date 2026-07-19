package com.fatih.litepdf.pdf

import android.graphics.Bitmap

data class PdfDocumentInfo(
    val pageCount: Int
)

data class PdfPageSize(
    val width: Int,
    val height: Int
)

data class RenderedPage(
    val pageIndex: Int,
    val bitmap: Bitmap
)

sealed interface PdfRenderResult {
    data class Success(val page: RenderedPage) : PdfRenderResult
    data class Failure(val reason: PdfRenderFailure) : PdfRenderResult
}

enum class PdfRenderFailure {
    InvalidPage,
    TooLarge,
    OutOfMemory,
    Unsupported,
    Closed
}
