package com.fatih.litepdf.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.fatih.litepdf.data.model.BookmarkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE documentId = :documentId ORDER BY pageIndex ASC")
    fun observeForDocument(documentId: String): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE documentId = :documentId AND pageIndex = :pageIndex")
    suspend fun deletePage(documentId: String, pageIndex: Int)

    @Query("DELETE FROM bookmarks WHERE documentId = :documentId")
    suspend fun deleteForDocument(documentId: String)
}
