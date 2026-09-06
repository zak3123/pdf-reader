package com.fatih.litepdf.office

import android.content.ContentResolver
import android.net.Uri
import com.fatih.litepdf.domain.model.DocumentKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Result of attempting to extract a textual/office document.
 */
sealed interface TextExtractionResult {
    data class Success(val content: TextDocumentContent) : TextExtractionResult
    data class Failure(val reason: TextExtractionFailure) : TextExtractionResult
}

enum class TextExtractionFailure {
    /** Legacy binary formats (.doc/.ppt/.xls) or other formats not handled offline. */
    UnsupportedFormat,

    /** The file is a supported format but is corrupt or not a real OOXML/text file. */
    CorruptOrInvalid,

    /** The provider could not be read (I/O). */
    ProviderError,

    /** The document has no extractable text content. */
    Empty
}

/**
 * Extracts readable content from Word, PowerPoint, Excel and plain-text/CSV files
 * using only Android built-ins (java.util.zip + XmlPullParser). PDF is intentionally
 * not handled here; it keeps using the original PdfRenderer engine.
 */
class TextDocumentEngine(private val contentResolver: ContentResolver) {

    suspend fun extract(uri: Uri, kind: DocumentKind, displayName: String?): TextExtractionResult =
        withContext(Dispatchers.IO) {
            val ext = displayName?.substringAfterLast('.', "")?.lowercase().orEmpty()
            // Reject legacy binary OLE2 formats explicitly with a clear message.
            if (ext in LEGACY_BINARY_EXTENSIONS) {
                return@withContext TextExtractionResult.Failure(TextExtractionFailure.UnsupportedFormat)
            }
            try {
                val content = when (kind) {
                    DocumentKind.WORD -> DocxParser(contentResolver).parse(uri)
                    DocumentKind.POWERPOINT -> PptxParser(contentResolver).parse(uri)
                    DocumentKind.SPREADSHEET -> {
                        if (ext == "csv") {
                            PlainTextParser(contentResolver).parse(uri, isCsv = true)
                        } else {
                            XlsxParser(contentResolver).parse(uri)
                        }
                    }
                    DocumentKind.TEXT -> PlainTextParser(contentResolver).parse(uri, isCsv = ext == "csv")
                    else -> return@withContext TextExtractionResult.Failure(
                        TextExtractionFailure.UnsupportedFormat
                    )
                }
                if (content.isEmpty) {
                    TextExtractionResult.Failure(TextExtractionFailure.Empty)
                } else {
                    TextExtractionResult.Success(content)
                }
            } catch (security: SecurityException) {
                TextExtractionResult.Failure(TextExtractionFailure.ProviderError)
            } catch (fnf: java.io.FileNotFoundException) {
                TextExtractionResult.Failure(TextExtractionFailure.ProviderError)
            } catch (zip: java.util.zip.ZipException) {
                TextExtractionResult.Failure(TextExtractionFailure.CorruptOrInvalid)
            } catch (io: java.io.IOException) {
                TextExtractionResult.Failure(TextExtractionFailure.CorruptOrInvalid)
            } catch (t: Throwable) {
                TextExtractionResult.Failure(TextExtractionFailure.CorruptOrInvalid)
            }
        }

    companion object {
        private val LEGACY_BINARY_EXTENSIONS = setOf("doc", "ppt", "xls")
    }
}
