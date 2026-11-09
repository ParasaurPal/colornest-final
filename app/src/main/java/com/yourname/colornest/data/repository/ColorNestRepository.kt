package com.yourname.colornest.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
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
        val file = File(imagesDir, "\u0024{baseName}_\u0024{System.currentTimeMillis()}_\u0024{(100..999).random()}.png")
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
                    val savedPath = saveTransparentLineArt(bitmap, "img")
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

                val savedPath = saveTransparentLineArt(bitmap, "pdf_page")
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

    // Background worker: only apply a confident user-trained image match.
    // Never generate titles or guess a category from a filename/default folder.
    fun startBackgroundWorker(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    val pendingPage = pageDao.getNextPendingAiPage()
                    if (pendingPage != null) {
                        pageDao.updatePage(pendingPage.copy(isProcessingAi = true, aiStatus = "Checking learned matches..."))
                        val localGuess = LocalBrain.guessCategoryLocally(pendingPage.imageUrl, learnedPatternDao)
                        if (localGuess != null) {
                            val categoryId = localGuess.first
                            pageDao.updatePage(pendingPage.copy(
                                categoryId = categoryId,
                                isUncategorized = false,
                                aiSuggestedCategoryId = categoryId,
                                isProcessingAi = false,
                                aiStatus = null
                            ))
                            LocalBrain.learnPattern(pendingPage.imageUrl, categoryId, learnedPatternDao)
                        } else {
                            pageDao.updatePage(pendingPage.copy(
                                isProcessingAi = false,
                                aiStatus = "Ready to categorize"
                            ))
                        }
                    }
                } catch (e: Exception) {
                    // Keep the worker alive; the page remains available for manual sorting.
                }
                delay(5000)
            }
        }
    }
}
