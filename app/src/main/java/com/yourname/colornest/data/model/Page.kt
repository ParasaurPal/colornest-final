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
    val isFavorite: Boolean = false,
    val isCompleted: Boolean = false,
    val progress: Int = 0,
    val isUncategorized: Boolean = true,
    val tags: List<String> = emptyList(),
    // Google Drive file ID of the uploaded copy of this page (null = not uploaded yet).
    val driveFileId: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
