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
    @Query("SELECT * FROM pages ORDER BY updatedAt DESC, createdAt DESC")
    fun getAllPagesFlow(): Flow<List<Page>>

    @Query(
        """
        SELECT * FROM pages
        WHERE (:escapedQuery = ''
            OR title LIKE '%' || :escapedQuery || '%' ESCAPE '\'
            OR tags LIKE '%' || :escapedQuery || '%' ESCAPE '\')
        ORDER BY updatedAt DESC, createdAt DESC
        """
    )
    fun searchPagesFlow(escapedQuery: String): Flow<List<Page>>

    @Query("SELECT * FROM pages ORDER BY updatedAt DESC, createdAt DESC")
    suspend fun getAllPages(): List<Page>

    @Query("SELECT * FROM pages WHERE id = :id LIMIT 1")
    suspend fun getPageById(id: Long): Page?

    @Query("SELECT * FROM pages WHERE driveFileId = :driveFileId LIMIT 1")
    suspend fun getPageByDriveFileId(driveFileId: String): Page?

    @Query("SELECT * FROM pages WHERE isUncategorized = 1 ORDER BY updatedAt DESC, createdAt DESC")
    suspend fun getUncategorizedPages(): List<Page>

    @Query("SELECT * FROM pages WHERE id IN (:ids)")
    suspend fun getPagesByIds(ids: List<Long>): List<Page>

    @Query("UPDATE pages SET tags = '', updatedAt = :updatedAt")
    suspend fun clearAllTags(updatedAt: Long = System.currentTimeMillis())

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
