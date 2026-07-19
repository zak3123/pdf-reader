package com.fatih.litepdf.pdf

import android.net.Uri

interface PdfEngine {
    suspend fun open(uri: Uri): PdfDocumentSession
}

interface PdfDocumentSession : AutoCloseable {
    val info: PdfDocumentInfo
    suspend fun pageSize(pageIndex: Int): PdfPageSize?
    suspend fun renderPage(pageIndex: Int, targetWidthPx: Int): PdfRenderResult
}
