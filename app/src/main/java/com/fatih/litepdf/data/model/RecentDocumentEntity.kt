package com.fatih.litepdf.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.fatih.litepdf.domain.model.DocumentKind
import com.fatih.litepdf.domain.model.RecentDocument

@Entity(
    tableName = "recent_documents",
    indices = [Index(value = ["lastOpenedAt"])]
)
data class RecentDocumentEntity(
    @PrimaryKey val id: String,
    val uriString: String,
    val displayName: String,
    val lastOpenedAt: Long,
    val lastViewedPage: Int,
    val pageCount: Int?,
    val sizeBytes: Long?,
    val lastKnownModified: Long?,
    val kind: String = DocumentKind.PDF.name
) {
    fun asDomain(): RecentDocument = RecentDocument(
        id = id,
        uriString = uriString,
        displayName = displayName,
        lastOpenedAt = lastOpenedAt,
        lastViewedPage = lastViewedPage,
        pageCount = pageCount,
        sizeBytes = sizeBytes,
        lastKnownModified = lastKnownModified,
        kind = runCatching { DocumentKind.valueOf(kind) }.getOrDefault(DocumentKind.PDF)
    )
}

fun RecentDocument.asEntity(): RecentDocumentEntity = RecentDocumentEntity(
    id = id,
    uriString = uriString,
    displayName = displayName,
    lastOpenedAt = lastOpenedAt,
    lastViewedPage = lastViewedPage,
    pageCount = pageCount,
    sizeBytes = sizeBytes,
    lastKnownModified = lastKnownModified,
    kind = kind.name
)
