package com.yourname.colornest.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.yourname.colornest.data.model.LearnedPattern

@Dao
interface LearnedPatternDao {
    @Query("SELECT * FROM learned_patterns")
    suspend fun getAllPatterns(): List<LearnedPattern>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPattern(pattern: LearnedPattern): Long

    @Query("DELETE FROM learned_patterns")
    suspend fun deleteAllPatterns()
}
