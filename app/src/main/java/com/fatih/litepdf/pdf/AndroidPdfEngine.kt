package com.fatih.litepdf.pdf

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.math.roundToInt

class AndroidPdfEngine(
    private val contentResolver: ContentResolver
) : PdfEngine {
    override suspend fun open(uri: Uri): PdfDocumentSession = withContext(Dispatchers.IO) {
        val pfd = contentResolver.openFileDescriptor(uri, "r")
            ?: throw IOException("Provider returned no file descriptor")
        try {
            AndroidPdfDocumentSession(pfd)
        } catch (throwable: Throwable) {
            pfd.close()
            throw throwable
        }
    }
}

class AndroidPdfDocumentSession(
    private val fileDescriptor: ParcelFileDescriptor
) : PdfDocumentSession {
    private val renderer = PdfRenderer(fileDescriptor)
    private val mutex = Mutex()
    @Volatile private var closed = false

    override val info: PdfDocumentInfo = PdfDocumentInfo(renderer.pageCount)

    override suspend fun pageSize(pageIndex: Int): PdfPageSize? = withContext(Dispatchers.IO) {
        if (closed || pageIndex !in 0 until info.pageCount) return@withContext null
        mutex.withLock {
            renderer.openPage(pageIndex).use { page ->
                PdfPageSize(page.width, page.height)
            }
        }
    }

    override suspend fun renderPage(pageIndex: Int, targetWidthPx: Int): PdfRenderResult =
        withContext(Dispatchers.Default) {
            if (closed) return@withContext PdfRenderResult.Failure(PdfRenderFailure.Closed)
            if (pageIndex !in 0 until info.pageCount || targetWidthPx <= 0) {
                return@withContext PdfRenderResult.Failure(PdfRenderFailure.InvalidPage)
            }

            try {
                mutex.withLock {
                    renderer.openPage(pageIndex).use { page ->
                        val target = calculateTargetSize(page.width, page.height, targetWidthPx)
                            ?: return@withLock PdfRenderResult.Failure(PdfRenderFailure.TooLarge)
                        val bitmap = Bitmap.createBitmap(target.first, target.second, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        PdfRenderResult.Success(RenderedPage(pageIndex, bitmap))
                    }
                }
            } catch (oom: OutOfMemoryError) {
                PdfRenderResult.Failure(PdfRenderFailure.OutOfMemory)
            } catch (illegal: IllegalStateException) {
                PdfRenderResult.Failure(PdfRenderFailure.Unsupported)
            } catch (argument: IllegalArgumentException) {
                PdfRenderResult.Failure(PdfRenderFailure.Unsupported)
            }
        }

    override fun close() {
        if (closed) return
        closed = true
        renderer.close()
        fileDescriptor.close()
    }

    private fun calculateTargetSize(pageWidth: Int, pageHeight: Int, requestedWidth: Int): Pair<Int, Int>? {
        val maxDimension = 3200
        val maxBytes = 36L * 1024L * 1024L
        val aspect = pageHeight.toFloat() / pageWidth.toFloat()
        var width = requestedWidth.coerceIn(120, maxDimension)
        var height = (width * aspect).roundToInt().coerceAtLeast(1)
        if (height > maxDimension) {
            height = maxDimension
            width = (height / aspect).roundToInt().coerceAtLeast(1)
        }
        val bytes = width.toLong() * height.toLong() * 4L
        if (bytes <= maxBytes) return width to height
        val scale = kotlin.math.sqrt(maxBytes.toDouble() / bytes.toDouble())
        width = (width * scale).roundToInt().coerceAtLeast(1)
        height = (height * scale).roundToInt().coerceAtLeast(1)
        return if (width.toLong() * height.toLong() * 4L <= maxBytes) width to height else null
    }
}
