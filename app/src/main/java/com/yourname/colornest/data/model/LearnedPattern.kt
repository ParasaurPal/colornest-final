package com.yourname.colornest.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "learned_patterns")
data class LearnedPattern(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val fingerprint: List<Float>,
    val categoryId: Long
)
