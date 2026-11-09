package com.yourname.colornest.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import com.yourname.colornest.data.ColorNestDatabase
import com.yourname.colornest.data.LocalBrain
import com.yourname.colornest.data.PrepopulateCategories
import com.yourname.colornest.data.model.Category
import com.yourname.colornest.data.model.Page
import com.yourname.colornest.data.model.Setting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.max

class ColorNestRepository(private val context: Context) {
    private val db = ColorNestDatabase.getDatabase(context)
    val pageDao = db.pageDao()
    val categoryDao = db.categoryDao()
    val learnedPatternDao = db.learnedPatternDao()
    val settingDao = db.settingDao()

    private val imagesDir: File by lazy {
        val dir = File(context.filesDir, "colornest")
        if (!dir.exists()) dir.mkdirs()
        dir
    }

    fun getAllPagesFlow(): Flow<List<Page>> = pageDao.getAllPagesFlow()
    fun getAllCategoriesFlow(): Flow<List<Category>> = categoryDao.getAllCategoriesFlow()
    fun getSettingFlow(key: String): Flow<String?> = settingDao.getSettingFlow(key)

    suspend fun getSetting(key: String): String? = withContext(Dispatchers.IO) {
        settingDao.getSetting(key)
    }

    suspend fun setSetting(key: String, value: String) = withContext(Dispatchers.IO) {
        settingDao.setSetting(Setting(key = key, value = value))
    }

    private fun secureAiPreferences() = EncryptedSharedPreferences.create(
        context,
        "colornest_secure_ai",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    suspend fun getGeminiApiKey(): String = withContext(Dispatchers.IO) {
        runCatching { secureAiPreferences().getString("gemini_api_key", "").orEmpty() }.getOrDefault("")
    }

    suspend fun saveGeminiApiKey(key: String) = withContext(Dispatchers.IO) {
        secureAiPreferences().edit().putString("gemini_api_key", key.trim()).apply()
    }

    suspend fun testGeminiConnection(apiKey: String): String = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "Paste your Gemini API key first." }
        val payload = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts",
                JSONArray().put(JSONObject().put("text", "Reply with exactly: COLORNEST_CONNECTED"))
            )))
            .put("generationConfig", JSONObject().put("maxOutputTokens", 20))
        val connection = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite:generateContent").openConnection() as HttpsURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 20000
            connection.readTimeout = 30000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("x-goog-api-key", apiKey.trim())
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val response = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val detail = runCatching { JSONObject(response).optJSONObject("error")?.optString("message") }.getOrNull()
                throw IllegalStateException("Gemini connection failed ($status): " +
                    (detail?.takeIf { it.isNotBlank() } ?: "Check the API key, model access, and AI Studio quota."))
            }
            val answer = JSONObject(response).optJSONArray("candidates")
                ?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
                ?.let { parts -> (0 until parts.length()).joinToString(" ") { parts.optJSONObject(it)?.optString("text").orEmpty() } }
                .orEmpty()
            if (answer.isBlank()) throw IllegalStateException("Gemini responded, but returned no text. Check model access and API quota.")
            "Connected to Gemini successfully."
        } finally {
            connection.disconnect()
        }
    }

    // Keep tags about what is depicted, not generic properties of the image.
    private fun normalizeSubjectTags(values: List<String>): List<String> {
        val rejected = setOf(
            "sky", "monochrome", "black and white", "black-and-white", "grayscale", "grey",
            "gray", "space", "screenshot", "illustration", "drawing", "coloring page",
            "colouring page", "line art", "image", "picture", "background", "texture",
            "contrast", "aesthetic", "art", "visual art", "photograph", "photo",
            "paper", "white", "black", "shadow", "light", "darkness"
        )
        return values.map { it.lowercase().trim().replace(Regex("\\s+"), " ") }
            .filter { it.length in 2..40 && it !in rejected }
            .distinct()
            .take(6)
    }

    private suspend fun callGeminiSubjectDetection(
        page: Page,
        categories: List<Category>,
        apiKey: String
    ): Pair<List<String>, String?> {
        val source = BitmapFactory.decodeFile(page.imageUrl)
            ?: throw IllegalStateException("Could not open this image")
        // Flatten transparent line-art onto white so the model sees clean black lines.
        val flattened = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        Canvas(flattened).apply {
            drawColor(AndroidColor.WHITE)
            drawBitmap(source, 0f, 0f, null)
        }
        source.recycle()
        val maxSide = max(flattened.width, flattened.height)
        val scaled = if (maxSide > 1024) {
            val ratio = 1024f / maxSide
            Bitmap.createScaledBitmap(flattened, (flattened.width * ratio).toInt().coerceAtLeast(1), (flattened.height * ratio).toInt().coerceAtLeast(1), true)
        } else flattened
        val bytes = java.io.ByteArrayOutputStream().use { output ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 82, output)
            output.toByteArray()
        }
        if (scaled !== flattened) scaled.recycle()
        flattened.recycle()

        val categoryNames = categories.joinToString(", ") { it.name }
        val learnedTags = getLearnedTagMap()
        val learningHints = categories.mapNotNull { category ->
            learnedTags[category.id]?.takeIf { it.isNotEmpty() }?.let { category.name + ": " + it.joinToString(", ") }
        }.joinToString("; ")
        val prompt = """
            You are labeling a collection of BLACK-AND-WHITE COLORING PAGE DRAWINGS.
            Your task is SUBJECT RECOGNITION, not image-style or scene description.
            Identify the actual object, creature, person, plant, character, building, or other thing
            the artist drew and that a person would color. Inspect the line art itself.
            Ignore the white paper, transparent areas, page border, lighting, image quality,
            camera/screenshot artifacts, and all artistic or visual properties.
            NEVER use generic appearance, medium, color, or background labels such as:
            sky, monochrome, black and white, grayscale, space, screenshot, illustration,
            drawing, coloring page, line art, image, background, texture, contrast, aesthetic.
            Do not label empty regions or scenery unless that scenery is clearly the main
            depicted subject (for example, a specific mountain landscape).
            Tags must be concrete subject nouns, from most specific to broader useful taxonomy.
            Good examples:
            - a fox drawing -> ["fox", "animal", "mammal", "canine"]
            - roses -> ["rose", "flower", "plant", "botanical"]
            - a dragon -> ["dragon", "mythical creature", "fantasy"]
            - a human portrait -> ["person", "portrait", "human"]
            - a castle -> ["castle", "building", "architecture"]
            Bad examples for ANY coloring page: ["sky", "monochrome", "space", "screenshot"].
            If you cannot confidently identify a concrete subject, return an empty tags array.
            Return ONLY valid JSON in this exact shape:
            {"subject":"one concise concrete subject noun phrase or empty string","tags":["lowercase subject nouns"],"category":"one exact category name from the list or null"}
            Available categories (choose the closest specific category): $categoryNames
            User-confirmed category examples and terms: $learningHints
            Respect user-confirmed examples only when the visible subject actually matches.
            Choose the most specific matching category. Never choose a category based only on
            background, color, art style, or image format. If no category fits, use null.
            Return at most 6 subject tags. No explanations or markdown.
        """.trimIndent()
        val parts = JSONArray()
            .put(JSONObject().put("text", prompt))
            .put(JSONObject().put("inlineData", JSONObject()
                .put("mimeType", "image/jpeg")
                .put("data", Base64.encodeToString(bytes, Base64.NO_WRAP))))
        val payload = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", parts)))
            .put("generationConfig", JSONObject().put("responseMimeType", "application/json"))
        val connection = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite:generateContent").openConnection() as HttpsURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 20000
            connection.readTimeout = 30000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("x-goog-api-key", apiKey)
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val response = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val detail = runCatching { JSONObject(response).optJSONObject("error")?.optString("message") }.getOrNull()
                throw IllegalStateException("Gemini request failed ($status): " +
                    (detail?.takeIf { it.isNotBlank() } ?: "Check your API key, model access, and AI Studio quota."))
            }
            val responseText = JSONObject(response)
                .getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts")
                .let { parts -> (0 until parts.length()).joinToString("") { parts.optJSONObject(it)?.optString("text").orEmpty() } }
                .trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val result = JSONObject(responseText)
            val tags = result.optJSONArray("tags") ?: JSONArray()
            val rawLabels = buildList {
                result.optString("subject").takeIf { it.isNotBlank() }?.let { add(it) }
                for (index in 0 until tags.length()) add(tags.optString(index))
            }
            val labels = normalizeSubjectTags(rawLabels)
            val category = result.optString("category").takeIf {
                it.isNotBlank() && !it.equals("null", true) && categories.any { item -> item.name.equals(it, true) }
            }
            return labels to category
        } finally {
            connection.disconnect()
        }
    }

    suspend fun insertCategory(name: String, parentId: Long?): Long = withContext(Dispatchers.IO) {
        categoryDao.insertCategory(Category(name = name, parentId = parentId))
    }

    suspend fun seedMassiveCategories() = withContext(Dispatchers.IO) {
        PrepopulateCategories.seedMassiveCategories(categoryDao)
    }

    suspend fun toggleFavorite(pageId: Long) = withContext(Dispatchers.IO) {
        val page = pageDao.getPageById(pageId) ?: return@withContext
        pageDao.updatePage(page.copy(isFavorite = !page.isFavorite))
    }

    suspend fun deletePage(page: Page) = withContext(Dispatchers.IO) {
        val file = File(page.imageUrl)
        if (file.exists()) {
            file.delete()
        }
        pageDao.deletePage(page)
    }

    suspend fun updatePageDetails(
        pageId: Long,
        newTitle: String,
        newCategoryId: Long?,
        tags: List<String>
    ) = withContext(Dispatchers.IO) {
        val page = pageDao.getPageById(pageId) ?: return@withContext
        val updated = page.copy(
            title = newTitle,
            categoryId = newCategoryId,
            isUncategorized = false,
            aiStatus = null,
            tags = tags
        )
        pageDao.updatePage(updated)

        if (newCategoryId != null) {
            LocalBrain.learnPattern(page.imageUrl, newCategoryId, learnedPatternDao)
            learnUserTags(newCategoryId, tags)
        }
    }

    private suspend fun learnUserTags(categoryId: Long, tags: List<String>) {
        if (tags.isEmpty()) return
        val key = "aiLearnedTags"
        val learned = runCatching { JSONObject(settingDao.getSetting(key) ?: "{}") }.getOrElse { JSONObject() }
        val categoryKey = categoryId.toString()
        val current = learned.optJSONArray(categoryKey) ?: JSONArray()
        val merged = (0 until current.length()).map { current.optString(it) }
            .toMutableSet()
        tags.map { it.trim().lowercase() }
            .filter { it.length >= 2 && it.length <= 40 }
            .forEach { merged.add(it) }
        learned.put(categoryKey, JSONArray(merged.take(100)))
        settingDao.setSetting(Setting(key = key, value = learned.toString()))
    }

    private suspend fun getLearnedTagMap(): Map<Long, Set<String>> {
        val learned = runCatching { JSONObject(settingDao.getSetting("aiLearnedTags") ?: "{}") }.getOrElse { JSONObject() }
        return buildMap {
            val keys = learned.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val categoryId = key.toLongOrNull() ?: continue
                val values = learned.optJSONArray(key) ?: continue
                put(categoryId, (0 until values.length()).map { values.optString(it).lowercase() }.toSet())
            }
        }
    }

    suspend fun resetAiBrain() = withContext(Dispatchers.IO) {
        LocalBrain.resetAIBrain(learnedPatternDao)
        settingDao.setSetting(Setting(key = "aiLearnedTags", value = "{}"))
    }

    suspend fun wipeAllData() = withContext(Dispatchers.IO) {
        imagesDir.listFiles()?.forEach { it.delete() }
        pageDao.deleteAllPages()
        categoryDao.deleteAllCategories()
        learnedPatternDao.deleteAllPatterns()
        settingDao.deleteAllSettings()
        secureAiPreferences().edit().remove("gemini_api_key").apply()

        // Re-seed default 5 categories
        val defaults = listOf(
            Category(name = "Animals", parentId = null),
            Category(name = "Mandalas & Patterns", parentId = null),
            Category(name = "Fantasy & Mythical", parentId = null),
            Category(name = "Botanical & Nature", parentId = null),
            Category(name = "Architecture", parentId = null)
        )
        categoryDao.insertCategories(defaults)
    }

    // Convert white paper to transparency while preserving anti-aliased linework.
    // The source bitmap is never modified; the transparent PNG is a separate app copy.
    private fun saveTransparentLineArt(bitmap: Bitmap, baseName: String): String {
        val maxDimension = max(bitmap.width, bitmap.height)
        val working = if (maxDimension > 2000) {
            val scale = 2000f / maxDimension
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1), true)
        } else bitmap
        val width = working.width
        val height = working.height
        val pixels = IntArray(width * height)
        working.getPixels(pixels, 0, width, 0, 0, width, height)
        for (index in pixels.indices) {
            val pixel = pixels[index]
            val luminance = (0.2126 * AndroidColor.red(pixel) + 0.7152 * AndroidColor.green(pixel) + 0.0722 * AndroidColor.blue(pixel)).toInt()
            val alpha = (255 - luminance).coerceIn(0, 255)
            pixels[index] = AndroidColor.argb(alpha, 0, 0, 0)
        }
        val transparent = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        transparent.setPixels(pixels, 0, width, 0, 0, width, height)
        val file = File(imagesDir, "${baseName}_${System.currentTimeMillis()}_${(100..999).random()}.png")
        FileOutputStream(file).use { output -> transparent.compress(Bitmap.CompressFormat.PNG, 100, output) }
        transparent.recycle()
        if (working !== bitmap) working.recycle()
        return file.absolutePath
    }

    // Save Bitmap scaled to max 600px width/height
    private fun saveScaledBitmap(bitmap: Bitmap, baseName: String): String {
        val width = bitmap.width
        val height = bitmap.height
        val maxDim = max(width, height)
        val scaled = if (maxDim > 600) {
            val scale = 600f / maxDim
            val matrix = Matrix().apply { postScale(scale, scale) }
            Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true)
        } else {
            bitmap
        }

        val file = File(imagesDir, "${baseName}_${System.currentTimeMillis()}_${(100..999).random()}.jpg")
        FileOutputStream(file).use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }
        if (scaled != bitmap) scaled.recycle()
        return file.absolutePath
    }

    suspend fun importUri(
        uri: Uri,
        removeWhiteBackground: Boolean = true,
        onProgress: (text: String, percent: Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        val mimeType = context.contentResolver.getType(uri) ?: ""
        val fileName = getFileName(uri)

        if (mimeType.contains("pdf", ignoreCase = true) || fileName.endsWith(".pdf", ignoreCase = true)) {
            onProgress("Reading PDF...", 10)
            convertPdfToPages(uri, fileName, removeWhiteBackground, onProgress)
        } else {
            onProgress("Compressing Image...", 40)
            var inputStream: InputStream? = null
            try {
                inputStream = context.contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                if (bitmap != null) {
                    onProgress("Saving Image...", 80)
                    val savedPath = if (removeWhiteBackground) saveTransparentLineArt(bitmap, "img") else saveScaledBitmap(bitmap, "img")
                    bitmap.recycle()

                    pageDao.insertPage(
                        Page(
                            title = "",
                            imageUrl = savedPath,
                            isUncategorized = true,
                            aiStatus = "Queued"
                        )
                    )
                }
            } finally {
                inputStream?.close()
            }
        }
    }

    private fun convertPdfToPages(
        uri: Uri,
        pdfName: String,
        removeWhiteBackground: Boolean,
        onProgress: (text: String, percent: Int) -> Unit
    ) {
        val tempPdfFile = File(context.cacheDir, "temp_${System.currentTimeMillis()}.pdf")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempPdfFile).use { output ->
                input.copyTo(output)
            }
        } ?: return

        try {
            val fileDescriptor = ParcelFileDescriptor.open(tempPdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val pdfRenderer = PdfRenderer(fileDescriptor)
            val pageCount = pdfRenderer.pageCount
            // PDF page titles remain blank until the user enters one.

            for (i in 0 until pageCount) {
                val percent = ((i + 1) * 100) / pageCount
                onProgress("Converting Page ${i + 1} of $pageCount...", percent)

                val page = pdfRenderer.openPage(i)
                val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                val savedPath = if (removeWhiteBackground) saveTransparentLineArt(bitmap, "pdf_page") else saveScaledBitmap(bitmap, "pdf_page")
                bitmap.recycle()

                val pageEntity = Page(
                    title = "",
                    imageUrl = savedPath,
                    isUncategorized = true,
                    aiStatus = "Queued"
                )
                kotlinx.coroutines.runBlocking {
                    pageDao.insertPage(pageEntity)
                }
            }

            pdfRenderer.close()
            fileDescriptor.close()
        } finally {
            tempPdfFile.delete()
        }
    }

    private fun getFileName(uri: Uri): String {
        var name = "coloring_page.jpg"
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    name = it.getString(index)
                }
            }
        }
        return name
    }

    private fun categorySynonyms(name: String): Set<String> {
        val n = name.lowercase()
        return when {
            n.contains("animal") -> setOf("animal", "dog", "cat", "bird", "fish", "horse", "wildlife", "mammal", "reptile", "insect", "butterfly")
            n.contains("mammal") -> setOf("mammal", "dog", "cat", "horse", "bear", "fox", "wolf", "deer", "rabbit", "elephant")
            n.contains("canine") || n.contains("dog") -> setOf("dog", "wolf", "fox", "coyote")
            n.contains("feline") || n.contains("cat") -> setOf("cat", "lion", "tiger", "leopard")
            n.contains("bird") || n.contains("avian") -> setOf("bird", "eagle", "owl", "parrot", "duck")
            n.contains("plant") || n.contains("botanical") || n.contains("nature") -> setOf("plant", "flower", "tree", "leaf", "botanical", "garden")
            n.contains("flower") -> setOf("flower", "blossom", "rose", "tulip")
            n.contains("tree") -> setOf("tree", "forest", "wood")
            n.contains("insect") -> setOf("insect", "butterfly", "bee", "dragonfly", "beetle")
            n.contains("fantasy") || n.contains("myth") -> setOf("dragon", "unicorn", "fairy", "fantasy", "mythical")
            n.contains("mandala") || n.contains("pattern") -> setOf("pattern", "mandala", "geometric", "symmetry")
            n.contains("architecture") || n.contains("building") -> setOf("building", "architecture", "house", "castle", "bridge")
            else -> setOf(n)
        }
    }

    private suspend fun findBestCategory(labels: List<String>, categories: List<Category>): Category? {
        val learnedTags = getLearnedTagMap()
        fun depth(category: Category): Int {
            var level = 0
            var parent = category.parentId
            while (parent != null && level < 20) {
                parent = categories.firstOrNull { it.id == parent }?.parentId
                level++
            }
            return level
        }
        val normalized = labels.map { it.lowercase().trim() }
        return categories.filter { category ->
            val name = category.name.lowercase().trim()
            val synonyms = categorySynonyms(name)
            normalized.any { label ->
                label == name || label.contains(name) || name.contains(label) ||
                    synonyms.any { synonym -> label == synonym || label.contains(synonym) } ||
                    learnedTags[category.id].orEmpty().any { learnedTag ->
                        label == learnedTag || label.contains(learnedTag)
                    }
            }
        }.maxByOrNull { depth(it) }
    }

    private suspend fun runSubjectDetection(page: Page, categories: List<Category>) {
        val file = File(page.imageUrl)
        if (!file.exists()) {
            pageDao.updatePage(page.copy(isProcessingAi = false, aiStatus = "Image unavailable"))
            return
        }
        val useGemini = settingDao.getSetting("geminiEnabled") == "true"
        val apiKey = if (useGemini) getGeminiApiKey() else ""
        val labels: List<String>
        val suggestedCategory: String?
        if (useGemini) {
            if (apiKey.isBlank()) {
                throw IllegalStateException("Gemini is enabled but no API key is saved. Open Settings and connect your Gemini API key.")
            }
            val result = callGeminiSubjectDetection(page, categories, apiKey)
            labels = result.first
            suggestedCategory = result.second
        } else {
            val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
            try {
                val image = InputImage.fromFilePath(context, Uri.fromFile(file))
                labels = normalizeSubjectTags(
                    Tasks.await(labeler.process(image))
                        .filter { it.confidence >= 0.55f }
                        .sortedByDescending { it.confidence }
                        .take(12)
                        .map { it.text }
                )
                suggestedCategory = null
            } finally {
                labeler.close()
            }
        }
        val category = if (labels.isNotEmpty()) {
            categories.firstOrNull { it.name.equals(suggestedCategory, ignoreCase = true) }
                ?: findBestCategory(labels, categories)
        } else null
        if (category != null) {
                pageDao.updatePage(page.copy(
                    categoryId = category.id,
                    isUncategorized = false,
                    aiSuggestedCategoryId = category.id,
                    tags = normalizeSubjectTags(page.tags + labels),
                    isProcessingAi = false,
                    aiStatus = null
                ))
                LocalBrain.learnPattern(page.imageUrl, category.id, learnedPatternDao)
            } else {
                pageDao.updatePage(page.copy(
                    tags = normalizeSubjectTags(page.tags + labels),
                    isUncategorized = true,
                    isProcessingAi = false,
                    aiStatus = if (labels.isEmpty()) "No subject found — ready to tag" else "AI labels found — choose a category"
                ))
            }
    }

    // Run on-device subject labeling for selected pages, or every page when ids is null.
    suspend fun detectSubjects(ids: List<Long>? = null) = withContext(Dispatchers.IO) {
        val categories = categoryDao.getAllCategories()
        val targets = if (ids == null) pageDao.getUncategorizedPages() else pageDao.getPagesByIds(ids).filter { it.isUncategorized }
        targets.forEach { page ->
            pageDao.updatePage(page.copy(isProcessingAi = true, aiStatus = "Detecting subjects..."))
            try {
                runSubjectDetection(page.copy(isProcessingAi = true), categories)
            } catch (e: Exception) {
                val reason = e.message?.take(90) ?: "Check your AI settings"
                pageDao.updatePage(page.copy(isProcessingAi = false, aiStatus = "AI error: $reason"))
            }
        }
    }

    // New imports are queued and stay in the Untagged section until a confident category is found.
    fun startBackgroundWorker(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    val aiEnabled = settingDao.getSetting("aiCategorizationEnabled")?.toBooleanStrictOrNull() ?: true
                    val pendingPage = if (aiEnabled) pageDao.getNextPendingAiPage() else null
                    if (pendingPage != null) {
                        pageDao.updatePage(pendingPage.copy(isProcessingAi = true, aiStatus = "Detecting subjects..."))
                        try {
                            runSubjectDetection(pendingPage.copy(isProcessingAi = true), categoryDao.getAllCategories())
                        } catch (e: Exception) {
                            val reason = e.message?.take(90) ?: "Check your AI settings"
                            pageDao.updatePage(pendingPage.copy(isProcessingAi = false, aiStatus = "AI error: $reason"))
                        }
                    }
                } catch (e: Exception) {
                    // Keep the worker alive; failed pages remain available for manual tagging.
                }
                delay(5000)
            }
        }
    }
}
