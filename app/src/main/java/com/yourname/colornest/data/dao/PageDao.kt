package com.yourname.colornest.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.yourname.colornest.data.model.Page
import kotlinx.coroutines.flow.Flow

@Dao
interface PageDao {
    @Query("SELECT * FROM pages ORDER BY createdAt DESC")
    fun getAllPagesFlow(): Flow<List<Page>>

    @Query("SELECT * FROM pages ORDER BY createdAt DESC")
    suspend fun getAllPages(): List<Page>

    @Query("SELECT * FROM pages WHERE id = :id LIMIT 1")
    suspend fun getPageById(id: Long): Page?

    @Query("SELECT * FROM pages WHERE isUncategorized = 1 AND isProcessingAi = 0 LIMIT 1")
    suspend fun getNextPendingAiPage(): Page?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPage(page: Page): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPages(pages: List<Page>): List<Long>

    @Update
    suspend fun updatePage(page: Page)

    @Delete
    suspend fun deletePage(page: Page)

    @Query("DELETE FROM pages WHERE id = :id")
    suspend fun deletePageById(id: Long)

    @Query("DELETE FROM pages")
    suspend fun deleteAllPages()
}
