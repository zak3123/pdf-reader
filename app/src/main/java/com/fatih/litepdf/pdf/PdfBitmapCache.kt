package com.fatih.litepdf.pdf

import android.graphics.Bitmap
import android.util.LruCache

class PdfBitmapCache(
    maxBytes: Int = defaultMaxBytes(),
    val retentionRadius: Int = 3
) {
    private val cache = object : LruCache<String, Bitmap>(maxBytes) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount

        override fun entryRemoved(
            evicted: Boolean,
            key: String,
            oldValue: Bitmap,
            newValue: Bitmap?
        ) {
            if (oldValue !== newValue && !oldValue.isRecycled) {
                oldValue.recycle()
            }
        }
    }

    fun get(documentId: String, pageIndex: Int, widthPx: Int): Bitmap? =
        cache.get(key(documentId, pageIndex, widthPx))?.takeUnless(Bitmap::isRecycled)

    fun put(documentId: String, pageIndex: Int, widthPx: Int, bitmap: Bitmap) {
        cache.put(key(documentId, pageIndex, widthPx), bitmap)
    }

    fun trimToVisibleRange(
        documentId: String,
        centerPage: Int,
        radius: Int = retentionRadius
    ) {
        val keys = cache.snapshot().keys
        keys.filter { key ->
            val parts = key.split(':')
            parts.size == 3 &&
                parts[0] == documentId &&
                (parts[1].toIntOrNull()?.let { kotlin.math.abs(it - centerPage) > radius } == true)
        }.forEach(cache::remove)
    }

    fun clear() {
        cache.evictAll()
    }

    fun sizeBytes(): Int = cache.size()

    private fun key(documentId: String, pageIndex: Int, widthPx: Int): String =
        "$documentId:$pageIndex:$widthPx"

    companion object {
        fun defaultMaxBytes(): Int {
            val runtime = Runtime.getRuntime()
            val available = (runtime.maxMemory() / 8).coerceAtMost(32L * 1024L * 1024L)
            return available.coerceAtLeast(8L * 1024L * 1024L).toInt()
        }
    }
}
