package com.yourname.colornest.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
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
        }
    }

    suspend fun resetAiBrain() = withContext(Dispatchers.IO) {
        LocalBrain.resetAIBrain(learnedPatternDao)
    }

    suspend fun wipeAllData() = withContext(Dispatchers.IO) {
        imagesDir.listFiles()?.forEach { it.delete() }
        pageDao.deleteAllPages()
        categoryDao.deleteAllCategories()
        learnedPatternDao.deleteAllPatterns()
        settingDao.deleteAllSettings()

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
        onProgress: (text: String, percent: Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        val mimeType = context.contentResolver.getType(uri) ?: ""
        val fileName = getFileName(uri)

        if (mimeType.contains("pdf", ignoreCase = true) || fileName.endsWith(".pdf", ignoreCase = true)) {
            onProgress("Reading PDF...", 10)
            convertPdfToPages(uri, fileName, onProgress)
        } else {
            onProgress("Compressing Image...", 40)
            var inputStream: InputStream? = null
            try {
                inputStream = context.contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                if (bitmap != null) {
                    onProgress("Saving Image...", 80)
                    val title = fileName.substringBeforeLast(".")
                    val savedPath = saveScaledBitmap(bitmap, "img")
                    bitmap.recycle()

                    pageDao.insertPage(
                        Page(
                            title = title.ifBlank { "Sorting..." },
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
            val baseTitle = pdfName.substringBeforeLast(".pdf")

            for (i in 0 until pageCount) {
                val percent = ((i + 1) * 100) / pageCount
                onProgress("Converting Page ${i + 1} of $pageCount...", percent)

                val page = pdfRenderer.openPage(i)
                val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                val savedPath = saveScaledBitmap(bitmap, "pdf_page")
                bitmap.recycle()

                val pageEntity = Page(
                    title = "$baseTitle - Page ${i + 1}",
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

    // Background worker loop
    fun startBackgroundWorker(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    val aiSetting = settingDao.getSetting("aiCategorizationEnabled")
                    val isAiEnabled = aiSetting?.toBooleanStrictOrNull() ?: true

                    val pendingPage = pageDao.getNextPendingAiPage()
                    if (pendingPage != null) {
                        pageDao.updatePage(pendingPage.copy(isProcessingAi = true, aiStatus = "Analyzing..."))

                        val rawCategories = categoryDao.getAllCategories()
                        var finalCategoryId: Long? = null

                        // 1. Local AI Memory First
                        val localGuess = LocalBrain.guessCategoryLocally(pendingPage.imageUrl, learnedPatternDao)
                        if (localGuess != null) {
                            finalCategoryId = localGuess.first
                            pageDao.updatePage(
                                pendingPage.copy(
                                    categoryId = finalCategoryId,
                                    isUncategorized = false,
                                    aiSuggestedCategoryId = finalCategoryId,
                                    isProcessingAi = false,
                                    aiStatus = null
                                )
                            )
                            LocalBrain.learnPattern(pendingPage.imageUrl, finalCategoryId, learnedPatternDao)
                        } else if (isAiEnabled) {
                            pageDao.updatePage(pendingPage.copy(aiStatus = "Matching Categories..."))
                            delay(1200)

                            // Intelligent categorization heuristic matching title or categories
                            val lowerTitle = pendingPage.title.lowercase()
                            val matched = rawCategories.find { cat ->
                                lowerTitle.contains(cat.name.lowercase()) ||
                                        cat.name.lowercase().contains(lowerTitle)
                            } ?: rawCategories.firstOrNull()

                            if (matched != null) {
                                finalCategoryId = matched.id
                                val inferredComplexity = when {
                                    lowerTitle.contains("mandala") || lowerTitle.contains("pattern") -> "Advanced"
                                    lowerTitle.contains("expert") || lowerTitle.contains("intricate") -> "Expert"
                                    lowerTitle.contains("easy") || lowerTitle.contains("simple") -> "Beginner"
                                    else -> "Medium"
                                }
                                pageDao.updatePage(
                                    pendingPage.copy(
                                        categoryId = finalCategoryId,
                                        complexity = inferredComplexity,
                                        time = "45m",
                                        tool = "Colored Pencils",
                                        isUncategorized = false,
                                        aiSuggestedCategoryId = finalCategoryId,
                                        isProcessingAi = false,
                                        aiStatus = null
                                    )
                                )
                                LocalBrain.learnPattern(pendingPage.imageUrl, finalCategoryId, learnedPatternDao)
                            } else {
                                pageDao.updatePage(pendingPage.copy(isProcessingAi = false, aiStatus = "Ready to categorize"))
                            }
                        } else {
                            pageDao.updatePage(pendingPage.copy(isProcessingAi = false, aiStatus = "Ready to categorize"))
                        }
                    }
                } catch (e: Exception) {
                    // Log and proceed
                }
                delay(5000)
            }
        }
    }
}
