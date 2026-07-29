package com.fatih.litepdf.pdf

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.fatih.litepdf.util.normalizeRotation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.math.roundToInt

class AndroidPdfEngine(
    private val contentResolver: ContentResolver,
    private val isLowRamDevice: Boolean = false
) : PdfEngine {
    override suspend fun open(uri: Uri): PdfDocumentSession = withContext(Dispatchers.IO) {
        val pageRotations = loadPageRotations(uri)
        val pfd = contentResolver.openFileDescriptor(uri, "r")
            ?: throw IOException("Provider returned no file descriptor")
        try {
            AndroidPdfDocumentSession(pfd, isLowRamDevice, pageRotations)
        } catch (throwable: Throwable) {
            pfd.close()
            throw throwable
        }
    }

    private fun loadPageRotations(uri: Uri): List<Int> {
        val inputStream = contentResolver.openInputStream(uri) ?: return emptyList()
        return try {
            inputStream.use {
                PDDocument.load(it).use { document ->
                    List(document.numberOfPages) { index ->
                        document.getPage(index).rotation.normalizeRotation()
                    }
                }
            }
        } catch (throwable: Throwable) {
            Log.w(TAG, "failed to read page rotation metadata", throwable)
            emptyList()
        }
    }

    private companion object {
        const val TAG = "PdfEngine"
    }
}

class AndroidPdfDocumentSession(
    private val fileDescriptor: ParcelFileDescriptor,
    private val isLowRamDevice: Boolean = false,
    private val pageRotations: List<Int> = emptyList()
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
                        val target = calculateTargetSize(
                            pageWidth = page.width,
                            pageHeight = page.height,
                            requestedWidth = targetWidthPx,
                            bytesPerPixel = ARGB_8888_BYTES_PER_PIXEL
                        ) ?: return@withLock PdfRenderResult.Failure(PdfRenderFailure.TooLarge)
                        val rendered = createArgbBitmap(target)
                            ?: return@withLock PdfRenderResult.Failure(PdfRenderFailure.OutOfMemory)
                        rendered.eraseColor(Color.WHITE)
                        val renderStartedAt = System.currentTimeMillis()
                        page.render(rendered, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val renderMs = System.currentTimeMillis() - renderStartedAt
                        val renderedWidth = rendered.width
                        val renderedHeight = rendered.height
                        val metadataRotation = pageRotation(pageIndex)
                        val displayBitmap = rendered.rotateForDisplay(metadataRotation)
                        val displayWidth = displayBitmap.width
                        val displayHeight = displayBitmap.height
                        val cached = createCacheBitmap(displayBitmap)
                        val totalMs = System.currentTimeMillis() - renderStartedAt
                        Log.d(
                            RENDER_TIMING_TAG,
                            "page=${pageIndex + 1} target=${displayWidth}x${displayHeight} " +
                                "renderMs=$renderMs totalMs=$totalMs " +
                                "thread=${Thread.currentThread().name}"
                        )
                        Log.d(
                            ROTATION_DEBUG_TAG,
                            "page=${pageIndex + 1} source=${page.width}x${page.height} " +
                                "metadata=$metadataRotation rendered=${renderedWidth}x$renderedHeight " +
                                "display=${displayWidth}x$displayHeight " +
                                "requestedWidth=$targetWidthPx"
                        )
                        PdfRenderResult.Success(RenderedPage(pageIndex, cached ?: displayBitmap))
                    }
                }
            } catch (oom: OutOfMemoryError) {
                Log.e(TAG, "render failed", oom)
                PdfRenderResult.Failure(PdfRenderFailure.OutOfMemory)
            } catch (illegal: IllegalStateException) {
                Log.e(TAG, "render failed", illegal)
                PdfRenderResult.Failure(PdfRenderFailure.Unsupported)
            } catch (argument: IllegalArgumentException) {
                Log.e(TAG, "render failed", argument)
                PdfRenderResult.Failure(PdfRenderFailure.Unsupported)
            } catch (runtime: RuntimeException) {
                Log.e(TAG, "render failed", runtime)
                PdfRenderResult.Failure(PdfRenderFailure.Unsupported)
            }
        }

    override fun close() {
        if (closed) return
        closed = true
        renderer.close()
        fileDescriptor.close()
    }

    private fun createArgbBitmap(target: Pair<Int, Int>): Bitmap? {
        return try {
            Bitmap.createBitmap(target.first, target.second, Bitmap.Config.ARGB_8888)
        } catch (_: OutOfMemoryError) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun createCacheBitmap(rendered: Bitmap): Bitmap? {
        return try {
            rendered.copy(Bitmap.Config.RGB_565, false)?.also {
                if (it !== rendered && !rendered.isRecycled) {
                    rendered.recycle()
                }
            }
        } catch (_: OutOfMemoryError) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun pageRotation(pageIndex: Int): Int =
        pageRotations.getOrNull(pageIndex)?.normalizeRotation() ?: 0

    private fun calculateTargetSize(
        pageWidth: Int,
        pageHeight: Int,
        requestedWidth: Int,
        bytesPerPixel: Int = RGB_565_BYTES_PER_PIXEL,
        byteLimit: Long = maxBitmapBytes
    ): Pair<Int, Int>? {
        val aspect = pageHeight.toFloat() / pageWidth.toFloat()
        var width = requestedWidth.coerceIn(120, maxDimension)
        var height = (width * aspect).roundToInt().coerceAtLeast(1)
        if (height > maxDimension) {
            height = maxDimension
            width = (height / aspect).roundToInt().coerceAtLeast(1)
        }
        val bytes = width.toLong() * height.toLong() * bytesPerPixel
        if (bytes <= byteLimit) return width to height
        val scale = kotlin.math.sqrt(byteLimit.toDouble() / bytes.toDouble())
        width = (width * scale).roundToInt().coerceAtLeast(1)
        height = (height * scale).roundToInt().coerceAtLeast(1)
        return if (width.toLong() * height.toLong() * bytesPerPixel <= byteLimit) {
            width to height
        } else {
            null
        }
    }

    private val maxDimension: Int = if (isLowRamDevice) 2400 else 3200
    private val maxBitmapBytes: Long =
        (if (isLowRamDevice) 16L else 36L) * 1024L * 1024L

    private companion object {
        const val TAG = "PdfEngine"
        const val RENDER_TIMING_TAG = "PdfRenderTiming"
        const val ROTATION_DEBUG_TAG = "PdfRotationDebug"
        const val RGB_565_BYTES_PER_PIXEL = 2
        const val ARGB_8888_BYTES_PER_PIXEL = 4
    }
}

private fun Bitmap.rotateForDisplay(rotation: Int): Bitmap {
    val normalized = rotation.normalizeRotation()
    if (normalized == 0) return this
    val matrix = Matrix().apply { postRotate(normalized.toFloat()) }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true).also { rotated ->
        if (rotated !== this && !isRecycled) {
            recycle()
        }
    }
}
