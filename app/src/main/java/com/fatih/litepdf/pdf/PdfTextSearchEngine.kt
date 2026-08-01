package com.fatih.litepdf.pdf

import android.content.ContentResolver
import android.net.Uri
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import com.fatih.litepdf.util.normalizeRotation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.lang.ref.SoftReference
import java.util.LinkedHashMap
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min

data class PdfWordHighlight(
    val normX: Float,
    val normY: Float,
    val normW: Float,
    val normH: Float
)

data class PdfSearchHit(
    val pageIndex: Int,
    val snippet: String,
    val highlights: List<PdfWordHighlight>
)

sealed interface PdfSearchResult {
    data class Success(val hits: List<PdfSearchHit>) : PdfSearchResult
    data class Failure(val reason: PdfSearchFailure) : PdfSearchResult
}

enum class PdfSearchFailure {
    EmptyQuery,
    AccessUnavailable,
    Unsupported,
    Failed
}

interface PdfTextSearchEngine {
    suspend fun search(uri: Uri, query: String, maxResults: Int = 80): PdfSearchResult
    fun clearCache(uri: Uri) = Unit
}

class PdfBoxTextSearchEngine(
    private val contentResolver: ContentResolver
) : PdfTextSearchEngine {
    private val extractionLocks = ConcurrentHashMap<String, Mutex>()
    private val pageCache =
        object : LinkedHashMap<String, SoftReference<List<List<PositionedWord>>>>(
            MAX_CACHED_DOCUMENTS,
            0.75f,
            true
        ) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<
                    String,
                    SoftReference<List<List<PositionedWord>>>
                >
            ): Boolean = size > MAX_CACHED_DOCUMENTS
        }

    override suspend fun search(uri: Uri, query: String, maxResults: Int): PdfSearchResult =
        withContext(Dispatchers.IO) {
            val cleanQuery = query.trim()
            if (cleanQuery.isEmpty()) {
                return@withContext PdfSearchResult.Failure(PdfSearchFailure.EmptyQuery)
            }

            try {
                val pages = getOrExtractPages(uri)
                val hits = mutableListOf<PdfSearchHit>()
                val needle = cleanQuery.replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)
                for ((pageIndex, words) in pages.withIndex()) {
                    val pageMatch = words.findMatches(needle)
                    if (pageMatch != null) {
                        hits += PdfSearchHit(
                            pageIndex = pageIndex,
                            snippet = pageMatch.text.snippetAround(
                                pageMatch.firstMatchIndex,
                                needle.length
                            ),
                            highlights = pageMatch.wordIndices.map { wordIndex ->
                                words[wordIndex].bounds
                            }
                        )
                        if (hits.size >= maxResults.coerceAtLeast(1)) break
                    }
                }
                PdfSearchResult.Success(hits)
            } catch (access: PdfAccessUnavailableException) {
                PdfSearchResult.Failure(PdfSearchFailure.AccessUnavailable)
            } catch (encrypted: EncryptedPdfException) {
                PdfSearchResult.Failure(PdfSearchFailure.Unsupported)
            } catch (security: SecurityException) {
                PdfSearchResult.Failure(PdfSearchFailure.AccessUnavailable)
            } catch (io: IOException) {
                PdfSearchResult.Failure(PdfSearchFailure.Unsupported)
            } catch (throwable: Throwable) {
                PdfSearchResult.Failure(PdfSearchFailure.Failed)
            }
        }

    override fun clearCache(uri: Uri) {
        val key = uri.toString()
        synchronized(pageCache) {
            pageCache.remove(key)
        }
        extractionLocks.remove(key)
    }

    private suspend fun getOrExtractPages(uri: Uri): List<List<PositionedWord>> {
        val key = uri.toString()
        cachedPages(key)?.let { return it }
        val lock = extractionLocks.getOrPut(key) { Mutex() }
        return lock.withLock {
            cachedPages(key)?.let { return@withLock it }
            val pages = extractPages(uri)
            synchronized(pageCache) {
                pageCache[key] = SoftReference(pages)
            }
            pages
        }
    }

    private fun cachedPages(key: String): List<List<PositionedWord>>? =
        synchronized(pageCache) {
            val pages = pageCache[key]?.get()
            if (pages == null) pageCache.remove(key)
            pages
        }

    private fun extractPages(uri: Uri): List<List<PositionedWord>> {
        val inputStream = contentResolver.openInputStream(uri)
            ?: throw PdfAccessUnavailableException()
        inputStream.use {
            PDDocument.load(it).use { document ->
                if (document.isEncrypted) throw EncryptedPdfException()
                return List(document.numberOfPages) { pageIndex ->
                    val rotation = document.getPage(pageIndex).rotation.normalizeRotation()
                    PositionedWordStripper().run {
                        startPage = pageIndex + 1
                        endPage = pageIndex + 1
                        getText(document)
                        words.map { word ->
                            word.copy(bounds = word.bounds.rotateForPage(rotation))
                        }
                    }
                }
            }
        }
    }

    private fun String.snippetAround(index: Int, length: Int): String {
        val start = (index - 48).coerceAtLeast(0)
        val end = (index + length + 72).coerceAtMost(this.length)
        val prefix = if (start > 0) "... " else ""
        val suffix = if (end < this.length) " ..." else ""
        return prefix + substring(start, end).trim() + suffix
    }

    private companion object {
        const val MAX_CACHED_DOCUMENTS = 2
    }
}

private class PdfAccessUnavailableException : IOException()
private class EncryptedPdfException : IOException()

private class PositionedWordStripper : PDFTextStripper() {
    val words = mutableListOf<PositionedWord>()

    init {
        sortByPosition = true
    }

    override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
        val wordText = text.trim()
        if (wordText.isNotEmpty() && textPositions.isNotEmpty()) {
            textPositions.toNormalizedBounds()?.let { bounds ->
                words += PositionedWord(wordText, bounds)
            }
        }
        super.writeString(text, textPositions)
    }
}

private data class PositionedWord(
    val text: String,
    val bounds: PdfWordHighlight
)

private data class PageMatch(
    val text: String,
    val firstMatchIndex: Int,
    val wordIndices: List<Int>
)

private fun List<PositionedWord>.findMatches(needle: String): PageMatch? {
    if (isEmpty() || needle.isEmpty()) return null

    val pageText = StringBuilder()
    val wordRanges = ArrayList<IntRange>(size)
    forEach { word ->
        if (pageText.isNotEmpty()) pageText.append(' ')
        val start = pageText.length
        pageText.append(word.text)
        wordRanges += start until pageText.length
    }

    val text = pageText.toString()
    val lowerText = text.lowercase(Locale.ROOT)
    val matchedWordIndices = linkedSetOf<Int>()
    var firstMatchIndex = -1
    var searchFrom = 0
    while (searchFrom <= lowerText.length - needle.length) {
        val matchIndex = lowerText.indexOf(needle, searchFrom)
        if (matchIndex < 0) break
        if (firstMatchIndex < 0) firstMatchIndex = matchIndex
        val matchEnd = matchIndex + needle.length
        wordRanges.forEachIndexed { wordIndex, range ->
            if (range.first < matchEnd && range.last >= matchIndex) {
                matchedWordIndices += wordIndex
            }
        }
        searchFrom = matchIndex + 1
    }

    return if (firstMatchIndex >= 0 && matchedWordIndices.isNotEmpty()) {
        PageMatch(text, firstMatchIndex, matchedWordIndices.toList())
    } else {
        null
    }
}

private fun List<TextPosition>.toNormalizedBounds(): PdfWordHighlight? {
    val pageWidth = firstOrNull()?.pageWidth?.takeIf { it > 0f } ?: return null
    val pageHeight = firstOrNull()?.pageHeight?.takeIf { it > 0f } ?: return null
    var left = Float.MAX_VALUE
    var top = Float.MAX_VALUE
    var right = -Float.MAX_VALUE
    var bottom = -Float.MAX_VALUE

    forEach { position ->
        val positionLeft = position.xDirAdj
        val positionTop = position.yDirAdj - position.heightDir
        left = min(left, positionLeft)
        top = min(top, positionTop)
        right = max(right, positionLeft + position.widthDirAdj)
        bottom = max(bottom, position.yDirAdj)
    }

    val normLeft = (left / pageWidth).coerceIn(0f, 1f)
    val normTop = (top / pageHeight).coerceIn(0f, 1f)
    val normRight = (right / pageWidth).coerceIn(normLeft, 1f)
    val normBottom = (bottom / pageHeight).coerceIn(normTop, 1f)
    return PdfWordHighlight(
        normX = normLeft,
        normY = normTop,
        normW = normRight - normLeft,
        normH = normBottom - normTop
    )
}

private fun PdfWordHighlight.rotateForPage(rotation: Int): PdfWordHighlight =
    when (rotation.normalizeRotation()) {
        90 -> PdfWordHighlight(
            normX = 1f - (normY + normH),
            normY = normX,
            normW = normH,
            normH = normW
        )
        180 -> PdfWordHighlight(
            normX = 1f - (normX + normW),
            normY = 1f - (normY + normH),
            normW = normW,
            normH = normH
        )
        270 -> PdfWordHighlight(
            normX = normY,
            normY = 1f - (normX + normW),
            normW = normH,
            normH = normW
        )
        else -> this
    }.clamp()

private fun PdfWordHighlight.clamp(): PdfWordHighlight {
    val left = normX.coerceIn(0f, 1f)
    val top = normY.coerceIn(0f, 1f)
    val right = (normX + normW).coerceIn(left, 1f)
    val bottom = (normY + normH).coerceIn(top, 1f)
    return copy(normX = left, normY = top, normW = right - left, normH = bottom - top)
}
