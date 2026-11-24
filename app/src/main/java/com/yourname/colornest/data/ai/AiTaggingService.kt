package com.yourname.colornest.data.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.max

object AiTaggingService {
    private const val TAG = "AiTaggingService"
    private const val MODEL_NAME = "gemini-3.8-flash"
    private const val AI_TIMEOUT_MS = 60_000L
    private const val MAX_IMAGE_DIMENSION = 1280

    private val tagsSchema = Schema.obj(
        properties = mapOf(
            "tags" to Schema.array(
                items = Schema.string(),
                description = "3 to 8 concise lowercase subject tags describing what is visibly depicted."
            )
        )
    )

    private val categorySchema = Schema.obj(
        properties = mapOf(
            "category_id" to Schema.integer(
                description = "The exact numeric ID of the single best matching existing category."
            ),
            "reason" to Schema.string(
                description = "A short explanation for why this category matches."
            )
        )
    )

    private val tagsModel by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = tagsSchema
            }
        )
    }

    private val categoryModel by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = categorySchema
            }
        )
    }

    data class CategorySuggestion(val categoryId: Long, val reason: String)

    suspend fun suggestPageTags(
        imagePath: String?,
        fileName: String?
    ): List<String> = withContext(Dispatchers.IO) {
        val prompt = listOf(
            "Analyze this coloring-page image and its original filename.",
            "Filename: ${fileName?.takeIf { it.isNotBlank() } ?: "Unknown"}",
            "Suggest 3 to 8 concise, useful subject tags based on what is visibly depicted and the filename.",
            "Prioritize concrete subjects (animals, flowers, people, objects, places), themes, and art style when clearly relevant.",
            "Do not guess hidden context. Avoid generic tags such as image, picture, coloring page, or art.",
            "Use lowercase tags, no hashtags, no duplicates."
        ).joinToString("\n")

        val bitmap = loadBitmapForAi(imagePath)
        try {
            val response = withTimeout(AI_TIMEOUT_MS) {
                tagsModel.generateContent(
                    content {
                        text(prompt)
                        if (bitmap != null) {
                            image(bitmap)
                        }
                    }
                )
            }

            val jsonString = response.text?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("Gemini returned an empty response.")
            val parsed = JSONObject(jsonString)
            val rawTags = parsed.optJSONArray("tags") ?: JSONArray()
            val cleaned = LinkedHashSet<String>()
            for (i in 0 until rawTags.length()) {
                val tag = rawTags.optString(i, "")
                    .trim()
                    .lowercase()
                    .removePrefix("#")
                    .trim()
                if (tag.isNotEmpty() && tag.length <= 40) {
                    cleaned.add(tag)
                }
                if (cleaned.size >= 8) break
            }

            if (cleaned.isEmpty()) {
                throw IllegalStateException("Gemini could not identify any useful subject tags.")
            }
            cleaned.toList()
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini tag suggestion timed out", e)
            throw IllegalStateException("Gemini request timed out after 60 seconds. Please try again.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini tag suggestion failed", e)
            throw IllegalStateException(e.localizedMessage ?: "Gemini tagging failed.", e)
        } finally {
            bitmap?.recycle()
        }
    }

    suspend fun suggestPageCategory(
        imagePath: String?,
        fileName: String?,
        categories: List<Pair<Long, String>>,
        learningExamples: List<String>
    ): CategorySuggestion = withContext(Dispatchers.IO) {
        require(categories.isNotEmpty()) { "Load your default categories before sorting." }
        val bitmap = loadBitmapForAi(imagePath)
            ?: throw IllegalStateException("This page has no locally stored image to sort.")

        val choices = categories.joinToString("\n") { (id, path) -> "$id = $path" }
        val examples = learningExamples.takeLast(30).joinToString("\n")
        val prompt = listOf(
            "Organize this coloring page into exactly ONE existing category.",
            "Never invent, rename, or create categories. Select only one supplied category ID.",
            "Use visible image content, filename, existing tags, and category hierarchy. Prefer the most specific suitable child category.",
            "Filename and existing tags: ${fileName?.takeIf { it.isNotBlank() } ?: "Unknown"}",
            "Existing categories (ID = full hierarchy path):\n$choices",
            if (examples.isNotBlank()) "Learn from these user-approved category corrections:\n$examples" else "No user correction examples are available yet."
        ).joinToString("\n")

        try {
            val response = withTimeout(AI_TIMEOUT_MS) {
                categoryModel.generateContent(
                    content {
                        text(prompt)
                        image(bitmap)
                    }
                )
            }

            val jsonString = response.text?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("Gemini returned an empty category response.")
            val parsed = JSONObject(jsonString)
            val id = parsed.optLong("category_id", -1L)
            if (categories.none { it.first == id }) {
                throw IllegalStateException("Gemini selected a category outside your existing category list.")
            }
            CategorySuggestion(
                categoryId = id,
                reason = parsed.optString("reason", "Best match in your category tree.")
                    .ifBlank { "Best match in your category tree." }
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini category sorting timed out", e)
            throw IllegalStateException("Gemini sorting timed out after 60 seconds. Please try again.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini category sorting failed", e)
            throw IllegalStateException(e.localizedMessage ?: "Gemini sorting failed.", e)
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * Decodes a scaled bitmap and composites transparent line-art PNGs onto a white background
     * so transparent pixels do not turn black when encoded for multimodal inference, keeping payloads compact.
     */
    private fun loadBitmapForAi(imagePath: String?): Bitmap? {
        val file = imagePath?.takeIf { it.isNotBlank() }?.let { File(it) }?.takeIf { it.exists() && it.isFile }
            ?: return null

        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, boundsOptions)
        if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) return null

        var sampleSize = 1
        val maxSide = max(boundsOptions.outWidth, boundsOptions.outHeight)
        while ((maxSide / sampleSize) > MAX_IMAGE_DIMENSION * 2) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeFile(file.absolutePath, decodeOptions) ?: return null

        val targetScale = if (max(decoded.width, decoded.height) > MAX_IMAGE_DIMENSION) {
            MAX_IMAGE_DIMENSION.toFloat() / max(decoded.width, decoded.height).toFloat()
        } else {
            1f
        }
        val targetW = (decoded.width * targetScale).toInt().coerceAtLeast(1)
        val targetH = (decoded.height * targetScale).toInt().coerceAtLeast(1)

        val flattened = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(flattened)
        canvas.drawColor(Color.WHITE)
        val scaled = if (targetW != decoded.width || targetH != decoded.height) {
            Bitmap.createScaledBitmap(decoded, targetW, targetH, true).also {
                if (it !== decoded) decoded.recycle()
            }
        } else {
            decoded
        }
        canvas.drawBitmap(scaled, 0f, 0f, null)
        scaled.recycle()
        return flattened
    }
}
