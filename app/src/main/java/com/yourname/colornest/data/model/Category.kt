package com.yourname.colornest.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val parentId: Long? = null,
    // Google Drive folder ID of this category's folder inside the ColorNest
    // folder (null = folder not created yet).
    val driveFolderId: String? = null
)
