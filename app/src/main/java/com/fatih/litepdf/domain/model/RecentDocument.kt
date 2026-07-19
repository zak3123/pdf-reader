package com.fatih.litepdf.domain.model

data class RecentDocument(
    val id: String,
    val uriString: String,
    val displayName: String,
    val lastOpenedAt: Long,
    val lastViewedPage: Int,
    val pageCount: Int?,
    val sizeBytes: Long?,
    val lastKnownModified: Long?
)
