package com.yourname.colornest.data.ai

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object AiTaggingService {
    private const val MODEL = "gemini-3.8-flash"
    private const val API_URL = "https://generativelanguage.googleapis.com/v1beta/interactions"
    private const val API_REVISION = "2026-05-20"

    suspend fun suggestPageTags(
        apiKey: String,
        imagePath: String?,
        fileName: String?
    ): List<String> = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isEmpty()) {
            throw IllegalStateException("Add your Gemini API key in Settings first.")
        }

        val prompt = listOf(
            "Analyze this coloring-page image and its original filename.",
            "Filename: ${fileName?.takeIf { it.isNotBlank() } ?: "Unknown"}",
            "Suggest 3 to 8 concise, useful subject tags based on what is visibly depicted and the filename.",
            "Prioritize concrete subjects (animals, flowers, people, objects, places), themes, and art style when clearly relevant.",
            "Do not guess hidden context. Avoid generic tags such as image, picture, coloring page, or art.",
            "Return only valid JSON in this exact shape: {\"tags\":[\"tag one\",\"tag two\"]}.",
            "Use lowercase tags, no hashtags, no duplicates."
        ).joinToString("\n")

        val inputArray = JSONArray().apply {
            put(JSONObject().apply {
                put("type", "text")
                put("text", prompt)
            })
            val imageFile = imagePath?.takeIf { it.isNotBlank() }?.let { File(it) }?.takeIf { it.exists() && it.isFile }
            if (imageFile != null) {
                val bytes = imageFile.readBytes()
                val mime = when (imageFile.extension.lowercase()) {
                    "png" -> "image/png"
                    "webp" -> "image/webp"
                    else -> "image/jpeg"
                }
                put(JSONObject().apply {
                    put("type", "image")
                    put("mime_type", mime)
                    put("data", Base64.encodeToString(bytes, Base64.NO_WRAP))
                })
            }
        }

        val requestBody = JSONObject().apply {
            put("model", MODEL)
            put("input", inputArray)
        }

        val connection = (URL(API_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20000
            readTimeout = 45000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("x-goog-api-key", trimmedKey)
            setRequestProperty("Api-Revision", API_REVISION)
        }

        try {
            connection.outputStream.bufferedWriter().use { writer ->
                writer.write(requestBody.toString())
            }

            val code = connection.responseCode
            val responseText = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }
                .orEmpty()

            val payload = runCatching { JSONObject(responseText) }.getOrNull()
            if (code !in 200..299) {
                val detail = payload?.optJSONObject("error")?.optString("message")
                    ?.takeIf { it.isNotBlank() }
                    ?: "Gemini request failed ($code)."
                throw IllegalStateException(detail)
            }

            val outputText = extractText(payload ?: JSONObject())
            val jsonMatch = Regex("\\{[\\s\\S]*\\}").find(outputText)?.value
                ?: throw IllegalStateException("Gemini returned an unexpected response. Please try again.")

            val parsed = runCatching { JSONObject(jsonMatch) }.getOrNull()
                ?: throw IllegalStateException("Gemini returned invalid tag data. Please try again.")

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
        } finally {
            connection.disconnect()
        }
    }


    data class CategorySuggestion(val categoryId: Long, val reason: String)

    suspend fun suggestPageCategory(
        apiKey: String,
        imagePath: String?,
        fileName: String?,
        categories: List<Pair<Long, String>>,
        learningExamples: List<String>
    ): CategorySuggestion = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "Add your Gemini API key in Settings first." }
        require(categories.isNotEmpty()) { "Load your default categories before sorting." }
        val choices = categories.joinToString("\\n") { (id, path) -> "$id = $path" }
        val examples = learningExamples.takeLast(30).joinToString("\\n")
        val prompt = listOf(
            "Organize this coloring page into exactly ONE existing category.",
            "Never invent, rename, or create categories. Select only one supplied category ID.",
            "Use visible image content, filename, existing tags, and category hierarchy. Prefer the most specific suitable child category.",
            "Filename and existing tags: ${fileName?.takeIf { it.isNotBlank() } ?: "Unknown"}",
            "Existing categories (ID = full hierarchy path):\\n$choices",
            if (examples.isNotBlank()) "Learn from these user-approved category corrections:\\n$examples" else "No user correction examples are available yet.",
            "Return only JSON: {\\"category_id\\": 123, \\"reason\\": \\"brief explanation\\"}."
        ).joinToString("\\n")
        val file = imagePath?.let { File(it) }?.takeIf { it.exists() && it.isFile }
            ?: throw IllegalStateException("This page has no locally stored image to sort.")
        val mime = when (file.extension.lowercase()) { "png" -> "image/png"; "webp" -> "image/webp"; else -> "image/jpeg" }
        val input = JSONArray().apply {
            put(JSONObject().apply { put("type", "text"); put("text", prompt) })
            put(JSONObject().apply {
                put("type", "image"); put("mime_type", mime)
                put("data", Base64.encodeToString(file.readBytes(), Base64.NO_WRAP))
            })
        }
        val connection = (URL(API_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 20000; readTimeout = 45000; doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("x-goog-api-key", apiKey.trim())
            setRequestProperty("Api-Revision", API_REVISION)
        }
        try {
            connection.outputStream.bufferedWriter().use { it.write(JSONObject().apply {
                put("model", MODEL); put("input", input)
            }.toString()) }
            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            val payload = runCatching { JSONObject(body) }.getOrNull()
            if (code !in 200..299) throw IllegalStateException(
                payload?.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
                    ?: "Gemini sorting failed ($code)."
            )
            val output = extractText(payload ?: JSONObject())
            val json = Regex("\\{[\\s\\S]*\\}").find(output)?.value
                ?: throw IllegalStateException("Gemini returned an invalid category suggestion.")
            val parsed = JSONObject(json)
            val id = parsed.optLong("category_id", -1L)
            if (categories.none { it.first == id }) throw IllegalStateException(
                "Gemini selected a category outside your existing category list."
            )
            CategorySuggestion(id, parsed.optString("reason", "Best match in your category tree."))
        } finally { connection.disconnect() }
    }

    private fun extractText(response: JSONObject): String {
        val direct = response.optString("output_text", "")
        if (direct.isNotBlank()) return direct

        val chunks = mutableListOf<String>()
        val steps = response.optJSONArray("steps") ?: return ""
        for (i in 0 until steps.length()) {
            val step = steps.optJSONObject(i) ?: continue
            if (step.optString("type") == "model_output") {
                val content = step.optJSONArray("content") ?: continue
                for (j in 0 until content.length()) {
                    val part = content.optJSONObject(j) ?: continue
                    if (part.optString("type") == "text") {
                        val text = part.optString("text", "")
                        if (text.isNotBlank()) chunks.add(text)
                    }
                }
            }
        }
        return chunks.joinToString("\n")
    }
}
