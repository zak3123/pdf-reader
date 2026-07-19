package com.fatih.litepdf.domain.model

data class Bookmark(
    val id: Long = 0,
    val documentId: String,
    val pageIndex: Int,
    val createdAt: Long,
    val label: String?
)
