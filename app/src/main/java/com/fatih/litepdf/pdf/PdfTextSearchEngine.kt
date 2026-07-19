package com.fatih.litepdf.pdf

import android.content.ContentResolver
import android.net.Uri
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale

data class PdfSearchHit(
    val pageIndex: Int,
    val snippet: String
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
}

class PdfBoxTextSearchEngine(
    private val contentResolver: ContentResolver
) : PdfTextSearchEngine {
    override suspend fun search(uri: Uri, query: String, maxResults: Int): PdfSearchResult =
        withContext(Dispatchers.IO) {
            val cleanQuery = query.trim()
            if (cleanQuery.isEmpty()) {
                return@withContext PdfSearchResult.Failure(PdfSearchFailure.EmptyQuery)
            }

            try {
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    PDDocument.load(inputStream).use { document ->
                        if (document.isEncrypted) {
                            return@withContext PdfSearchResult.Failure(PdfSearchFailure.Unsupported)
                        }
                        val stripper = PDFTextStripper()
                        val hits = mutableListOf<PdfSearchHit>()
                        val needle = cleanQuery.lowercase(Locale.getDefault())
                        for (page in 1..document.numberOfPages) {
                            stripper.startPage = page
                            stripper.endPage = page
                            val text = stripper.getText(document).replace(Regex("\\s+"), " ").trim()
                            val index = text.lowercase(Locale.getDefault()).indexOf(needle)
                            if (index >= 0) {
                                hits += PdfSearchHit(page - 1, text.snippetAround(index, cleanQuery.length))
                                if (hits.size >= maxResults) break
                            }
                        }
                        PdfSearchResult.Success(hits)
                    }
                } ?: PdfSearchResult.Failure(PdfSearchFailure.AccessUnavailable)
            } catch (security: SecurityException) {
                PdfSearchResult.Failure(PdfSearchFailure.AccessUnavailable)
            } catch (io: IOException) {
                PdfSearchResult.Failure(PdfSearchFailure.Unsupported)
            } catch (throwable: Throwable) {
                PdfSearchResult.Failure(PdfSearchFailure.Failed)
            }
        }

    private fun String.snippetAround(index: Int, length: Int): String {
        val start = (index - 48).coerceAtLeast(0)
        val end = (index + length + 72).coerceAtMost(this.length)
        val prefix = if (start > 0) "... " else ""
        val suffix = if (end < this.length) " ..." else ""
        return prefix + substring(start, end).trim() + suffix
    }
}
