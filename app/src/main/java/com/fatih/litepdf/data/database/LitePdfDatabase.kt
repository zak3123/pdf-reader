package com.fatih.litepdf.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.fatih.litepdf.data.model.BookmarkEntity
import com.fatih.litepdf.data.model.RecentDocumentEntity

@Database(
    entities = [RecentDocumentEntity::class, BookmarkEntity::class],
    version = 2,
    exportSchema = true
)
abstract class LitePdfDatabase : RoomDatabase() {
    abstract fun recentDocumentDao(): RecentDocumentDao
    abstract fun bookmarkDao(): BookmarkDao

    companion object {
        /**
         * v1 -> v2 adds the document `kind` column so LitePDF can distinguish PDF from
         * Word/PowerPoint/Excel/text documents. Existing rows were all PDFs.
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE recent_documents ADD COLUMN kind TEXT NOT NULL DEFAULT 'PDF'"
                )
            }
        }
    }
}
