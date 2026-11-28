package com.yourname.colornest.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pages")
data class Page(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val categoryId: Long? = null,
    val complexity: String = "Beginner",
    val time: String = "TBD",
    val tool: String = "TBD",
    val imageUrl: String,
    val thumbnailUrl: String? = null,
    val isPreviewOnly: Boolean = false,
    val driveThumbnailLink: String? = null,
    val isFavorite: Boolean = false,
    val isCompleted: Boolean = false,
    val progress: Int = 0,
    val isUncategorized: Boolean = true,
    val tags: List<String> = emptyList(),
    // Google Drive file ID of the uploaded/linked copy of this page.
    val driveFileId: String? = null,
    val whiteBackgroundProcessed: Boolean = false,
    val whiteBackgroundVersion: Int = 0,
    val syncId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
