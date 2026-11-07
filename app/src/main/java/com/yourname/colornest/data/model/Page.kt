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
    val isProcessingAi: Boolean = false,
    val aiStatus: String? = "Queued",
    val aiSuggestedCategoryId: Long? = null,
    val tags: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)
