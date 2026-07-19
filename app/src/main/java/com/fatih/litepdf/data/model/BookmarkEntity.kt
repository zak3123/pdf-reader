package com.fatih.litepdf.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.fatih.litepdf.domain.model.Bookmark

@Entity(
    tableName = "bookmarks",
    foreignKeys = [
        ForeignKey(
            entity = RecentDocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["documentId", "pageIndex"], unique = true),
        Index(value = ["createdAt"])
    ]
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: String,
    val pageIndex: Int,
    val createdAt: Long,
    val label: String?
) {
    fun asDomain(): Bookmark = Bookmark(
        id = id,
        documentId = documentId,
        pageIndex = pageIndex,
        createdAt = createdAt,
        label = label
    )
}
