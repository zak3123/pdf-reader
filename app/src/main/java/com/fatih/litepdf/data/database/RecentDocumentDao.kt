package com.fatih.litepdf.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.fatih.litepdf.data.model.RecentDocumentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentDocumentDao {
    @Query("SELECT * FROM recent_documents ORDER BY lastOpenedAt DESC")
    fun observeAll(): Flow<List<RecentDocumentEntity>>

    @Query("SELECT * FROM recent_documents WHERE id = :documentId LIMIT 1")
    suspend fun getById(documentId: String): RecentDocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(document: RecentDocumentEntity)

    @Query("UPDATE recent_documents SET lastViewedPage = :pageIndex WHERE id = :documentId")
    suspend fun updateLastViewedPage(documentId: String, pageIndex: Int)

    @Delete
    suspend fun delete(document: RecentDocumentEntity)

    @Query("DELETE FROM recent_documents WHERE id = :documentId")
    suspend fun deleteById(documentId: String)

    @Query("DELETE FROM recent_documents")
    suspend fun clear()
}
