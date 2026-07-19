package com.fatih.litepdf.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.fatih.litepdf.data.model.BookmarkEntity
import com.fatih.litepdf.data.model.RecentDocumentEntity

@Database(
    entities = [RecentDocumentEntity::class, BookmarkEntity::class],
    version = 1,
    exportSchema = true
)
abstract class LitePdfDatabase : RoomDatabase() {
    abstract fun recentDocumentDao(): RecentDocumentDao
    abstract fun bookmarkDao(): BookmarkDao
}
