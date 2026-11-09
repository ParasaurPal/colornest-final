package com.yourname.colornest.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import com.yourname.colornest.data.dao.LearnedPatternDao
import com.yourname.colornest.data.model.LearnedPattern
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

object LocalBrain {

    fun generateImageFingerprint(bitmap: Bitmap): List<Float> {
        val scaled = Bitmap.createScaledBitmap(bitmap, 8, 8, true)
        val fingerprint = ArrayList<Float>(64)
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                val pixel = scaled.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                val alpha = Color.alpha(pixel) / 255f
                val foregroundBrightness = (r + g + b) / 3f
                // Composite transparent line art over white paper for stable fingerprints.
                val brightness = foregroundBrightness * alpha + 255f * (1f - alpha)
                fingerprint.add(brightness)
            }
        }
        if (scaled != bitmap) {
            scaled.recycle()
        }
        return fingerprint
    }

    suspend fun generateFingerprintFromFile(filePath: String): List<Float>? = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            if (!file.exists()) return@withContext null
            val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return@withContext null
            val fp = generateImageFingerprint(bitmap)
            bitmap.recycle()
            fp
        } catch (e: Exception) {
            null
        }
    }

    fun compareFingerprints(fp1: List<Float>?, fp2: List<Float>?): Float {
        if (fp1 == null || fp2 == null || fp1.size != 64 || fp2.size != 64) return 0f
        var similarityCount = 0
        for (i in 0 until 64) {
            if (abs(fp1[i] - fp2[i]) < 20f) {
                similarityCount++
            }
        }
        return similarityCount / 64f
    }

    suspend fun learnPattern(
        imagePath: String,
        categoryId: Long,
        dao: LearnedPatternDao
    ): Boolean = withContext(Dispatchers.IO) {
        if (categoryId <= 0) return@withContext false
        val fingerprint = generateFingerprintFromFile(imagePath) ?: return@withContext false
        val existingPatterns = dao.getAllPatterns()
        val closest = existingPatterns
            .map { pattern -> pattern to compareFingerprints(pattern.fingerprint, fingerprint) }
            .filter { it.second > 0.85f }
            .maxByOrNull { it.second }

        if (closest != null) {
            // A user's manual correction overrides an older AI or user label for
            // this same visual pattern, rather than being discarded as a duplicate.
            if (closest.first.categoryId != categoryId) {
                dao.correctPatternCategory(closest.first.id, categoryId)
                true
            } else {
                false
            }
        } else {
            dao.insertPattern(LearnedPattern(fingerprint = fingerprint, categoryId = categoryId))
            true
        }
    }

    suspend fun guessCategoryLocally(
        imagePath: String,
        dao: LearnedPatternDao
    ): Pair<Long, Float>? = withContext(Dispatchers.IO) {
        val fingerprint = generateFingerprintFromFile(imagePath) ?: return@withContext null
        val patterns = dao.getAllPatterns()
        if (patterns.isEmpty()) return@withContext null

        var bestMatchId: Long? = null
        var highestScore = 0f

        for (pattern in patterns) {
            val score = compareFingerprints(fingerprint, pattern.fingerprint)
            if (score > highestScore) {
                highestScore = score
                bestMatchId = pattern.categoryId
            }
        }

        if (bestMatchId != null && highestScore > 0.85f) {
            Pair(bestMatchId, highestScore)
        } else {
            null
        }
    }

    suspend fun resetAIBrain(dao: LearnedPatternDao) = withContext(Dispatchers.IO) {
        dao.deleteAllPatterns()
    }
}
