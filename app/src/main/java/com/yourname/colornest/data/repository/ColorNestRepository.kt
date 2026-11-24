package com.yourname.colornest.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.yourname.colornest.data.ColorNestDatabase
import com.yourname.colornest.data.PrepopulateCategories
import com.yourname.colornest.data.ai.AiTaggingService
import com.yourname.colornest.data.backup.BackupManager
import com.yourname.colornest.data.drive.DriveBrowserEntry
import com.yourname.colornest.data.drive.DriveSyncManager
import com.yourname.colornest.data.drive.ScanProgress
import com.yourname.colornest.data.drive.SyncResult
import com.yourname.colornest.data.model.Category
import com.yourname.colornest.data.model.Page
import com.yourname.colornest.data.model.Setting
import com.yourname.colornest.data.model.TagEntity
import com.yourname.colornest.data.processing.DuplicateFinder
import com.yourname.colornest.data.processing.DuplicateGroup
import com.yourname.colornest.data.processing.ImageProcessor
import com.yourname.colornest.provider.ColorNestDocumentsProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class ColorNestRepository(private val context: Context) {
    private val db = ColorNestDatabase.getDatabase(context)
    val pageDao = db.pageDao()
    val categoryDao = db.categoryDao()
    val settingDao = db.settingDao()
    val tagDao = db.tagDao()

    val driveSyncManager by lazy {
        DriveSyncManager(context, pageDao, categoryDao, settingDao, tagDao)
    }

    val scanProgress: StateFlow<ScanProgress?>
        get() = driveSyncManager.scanProgress

    private val imagesDir: File by lazy {
        File(context.filesDir, "colornest").apply { if (!exists()) mkdirs() }
    }

    private val thumbsDir: File by lazy {
        File(context.filesDir, "colornest/thumbs").apply { if (!exists()) mkdirs() }
    }

    fun getAllPagesFlow(): Flow<List<Page>> = pageDao.getAllPagesFlow()

    fun searchPagesFlow(rawQuery: String): Flow<List<Page>> {
        val trimmed = rawQuery.trim()
        if (trimmed.isEmpty()) return pageDao.getAllPagesFlow()
        val escaped = trimmed
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
        return pageDao.searchPagesFlow(escaped)
    }

    fun getAllCategoriesFlow(): Flow<List<Category>> = categoryDao.getAllCategoriesFlow()
    fun getAllTagsFlow(): Flow<List<TagEntity>> = tagDao.getAllTagsFlow()
    fun getSettingFlow(key: String): Flow<String?> = settingDao.getSettingFlow(key)

    suspend fun getSetting(key: String): String? = withContext(Dispatchers.IO) {
        settingDao.getSetting(key)
    }

    suspend fun setSetting(key: String, value: String) = withContext(Dispatchers.IO) {
        settingDao.setSetting(Setting(key = key, value = value))
    }

    suspend fun deleteSetting(key: String) = withContext(Dispatchers.IO) {
        settingDao.deleteSetting(key)
    }

    suspend fun insertCategory(name: String, parentId: Long?): Long = withContext(Dispatchers.IO) {
        val id = categoryDao.insertCategory(Category(name = name, parentId = parentId))
        ColorNestDocumentsProvider.notifyChanged(context)
        id
    }

    suspend fun seedMassiveCategories() = withContext(Dispatchers.IO) {
        PrepopulateCategories.seedMassiveCategories(categoryDao)
        ColorNestDocumentsProvider.notifyChanged(context)
    }

    suspend fun toggleFavorite(pageId: Long) = withContext(Dispatchers.IO) {
        val page = pageDao.getPageById(pageId) ?: return@withContext
        pageDao.updatePage(
            page.copy(
                isFavorite = !page.isFavorite,
                updatedAt = System.currentTimeMillis()
            )
        )
        ColorNestDocumentsProvider.notifyChanged(context)
    }

    suspend fun deletePage(page: Page) = withContext(Dispatchers.IO) {
        driveSyncManager.trashPageFile(page)
        deleteLocalFilesForPage(page)
        pageDao.deletePage(page)
        ColorNestDocumentsProvider.notifyChanged(context)
    }

    private fun deleteLocalFilesForPage(page: Page) {
        page.imageUrl.takeIf { it.isNotBlank() }?.let { path ->
            File(path).takeIf { it.exists() }?.delete()
        }
        page.thumbnailUrl?.takeIf { it.isNotBlank() }?.let { path ->
            File(path).takeIf { it.exists() }?.delete()
        }
    }

    suspend fun updatePageDetails(
        pageId: Long,
        newTitle: String,
        newCategoryId: Long?,
        tags: List<String>,
        isCompleted: Boolean? = null
    ) = withContext(Dispatchers.IO) {
        val page = pageDao.getPageById(pageId) ?: return@withContext
        if (page.categoryId != null && newCategoryId != null && page.categoryId != newCategoryId && page.aiScannedAt != null) {
            saveCategoryLearningExample(page, newCategoryId)
        }
        val mergedTags = LinkedHashSet<String>()
        tags.map { it.trim() }.filter { it.isNotEmpty() }.forEach { mergedTags.add(it) }
        if (newCategoryId != null) {
            val catName = categoryDao.getCategoryById(newCategoryId)?.name?.trim()
            if (!catName.isNullOrEmpty()) {
                mergedTags.add(catName)
            }
        }
        val uniqueTags = mergedTags.toList()
        val now = System.currentTimeMillis()

        val updated = page.copy(
            title = newTitle,
            categoryId = newCategoryId,
            isUncategorized = newCategoryId == null,
            tags = uniqueTags,
            isCompleted = isCompleted ?: page.isCompleted,
            updatedAt = now
        )
        pageDao.updatePage(updated)
        uniqueTags.forEach { name ->
            if (tagDao.getTagByName(name) == null) {
                tagDao.insertTag(TagEntity(name = name, createdAt = now))
            }
        }
        driveSyncManager.propagatePageChange(updated)
        ColorNestDocumentsProvider.notifyChanged(context)
    }

    // ---------- Tag catalog management ----------

    suspend fun createTag(rawName: String): Boolean = withContext(Dispatchers.IO) {
        val name = rawName.trim()
        if (name.isEmpty()) return@withContext false
        if (tagDao.getTagByName(name) != null) return@withContext false
        tagDao.insertTag(TagEntity(name = name))
        true
    }

    suspend fun renameTag(oldName: String, rawNewName: String) = withContext(Dispatchers.IO) {
        val newName = rawNewName.trim()
        if (newName.isEmpty() || newName == oldName) return@withContext
        val now = System.currentTimeMillis()
        val allPages = pageDao.getAllPages()
        for (page in allPages) {
            if (oldName in page.tags) {
                val updatedTags = page.tags.map { if (it == oldName) newName else it }.distinct()
                pageDao.updatePage(page.copy(tags = updatedTags, updatedAt = now))
            }
        }
        tagDao.deleteTagByName(oldName)
        if (tagDao.getTagByName(newName) == null) {
            tagDao.insertTag(TagEntity(name = newName, createdAt = now))
        }
        ColorNestDocumentsProvider.notifyChanged(context)
    }

    suspend fun deleteTag(name: String) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val allPages = pageDao.getAllPages()
        for (page in allPages) {
            if (name in page.tags) {
                val updatedTags = page.tags.filterNot { it == name }
                pageDao.updatePage(page.copy(tags = updatedTags, updatedAt = now))
            }
        }
        tagDao.deleteTagByName(name)
        ColorNestDocumentsProvider.notifyChanged(context)
    }

    suspend fun clearAllTags() = withContext(Dispatchers.IO) {
        pageDao.clearAllTags(System.currentTimeMillis())
        tagDao.deleteAllTags()
        ColorNestDocumentsProvider.notifyChanged(context)
    }

    // ---------- AI subject tagging ----------

    suspend fun applyAiTagSuggestions(page: Page, suggestedTags: List<String>) = withContext(Dispatchers.IO) {
        val clean = suggestedTags.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct()
        val merged = (page.tags + clean).distinct()
        val now = System.currentTimeMillis()
        pageDao.updatePage(
            page.copy(
                tags = merged,
                aiSuggestedTags = clean,
                aiScannedAt = now,
                updatedAt = now
            )
        )
        clean.forEach { name ->
            if (tagDao.getTagByName(name) == null) {
                tagDao.insertTag(TagEntity(name = name, createdAt = now))
            }
        }
        ColorNestDocumentsProvider.notifyChanged(context)
    }


    suspend fun suggestCategoryForPage(
        page: Page, apiKey: String, categories: List<Category>, learningExamples: List<String>
    ): AiTaggingService.CategorySuggestion {
        val imagePath = page.imageUrl.takeIf { it.isNotBlank() && File(it).exists() }
            ?: page.thumbnailUrl?.takeIf { File(it).exists() }
            ?: throw IllegalStateException("This page has no locally stored image to sort.")
        val byId = categories.associateBy { it.id }
        fun path(category: Category): String {
            val names = mutableListOf(category.name)
            var parent = category.parentId?.let { byId[it] }
            while (parent != null) { names.add(0, parent.name); parent = parent.parentId?.let { byId[it] } }
            return names.joinToString(" / ")
        }
        val details = page.title + if (page.tags.isNotEmpty()) " | tags: ${page.tags.joinToString(", ")}" else ""
        return AiTaggingService.suggestPageCategory(
            apiKey, imagePath, details, categories.map { it.id to path(it) }, learningExamples
        )
    }

    suspend fun saveCategoryLearningExample(page: Page, chosenCategoryId: Long) = withContext(Dispatchers.IO) {
        val category = categoryDao.getCategoryById(chosenCategoryId) ?: return@withContext
        val byId = categoryDao.getAllCategories().associateBy { it.id }
        val names = mutableListOf(category.name)
        var parent = category.parentId?.let { byId[it] }
        while (parent != null) { names.add(0, parent.name); parent = parent.parentId?.let { byId[it] } }
        val stored = runCatching { JSONArray(settingDao.getSetting("aiCategoryLearning").orEmpty()) }.getOrDefault(JSONArray())
        val updated = JSONArray()
        for (i in (stored.length() - 39).coerceAtLeast(0) until stored.length()) updated.put(stored.optString(i))
        updated.put("${page.title.take(80)} | tags: ${page.tags.take(8).joinToString(", ")} => ${names.joinToString(" / ")}")
        settingDao.setSetting(Setting(key = "aiCategoryLearning", value = updated.toString()))
    }

    suspend fun suggestTagsForPage(page: Page, apiKey: String): List<String> {
        val imagePath = page.imageUrl.takeIf { it.isNotBlank() && File(it).exists() }
            ?: page.thumbnailUrl?.takeIf { File(it).exists() }
            ?: throw IllegalStateException("This page has no locally stored image to scan.")
        return AiTaggingService.suggestPageTags(
            apiKey = apiKey,
            imagePath = imagePath,
            fileName = page.title
        )
    }

    // ---------- Backfill, batch processing, duplicates, storage ----------

    suspend fun backfillThumbnails() = withContext(Dispatchers.IO) {
        val pending = pageDao.getAllPages().filter {
            !it.isPreviewOnly && it.imageUrl.isNotBlank() && it.thumbnailUrl.isNullOrBlank()
        }
        for (page in pending) {
            val thumb = ImageProcessor.tryCreateThumbnailFromFile(File(page.imageUrl), thumbsDir)
            if (thumb != null) {
                pageDao.updatePage(page.copy(thumbnailUrl = thumb))
            }
        }
    }

    suspend fun processExistingLibrary(
        onProgress: (done: Int, total: Int, currentTitle: String) -> Unit
    ): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val pending = pageDao.getAllPages().filter { ImageProcessor.needsWhiteBackgroundReprocessing(it) }
        var processed = 0
        var failed = 0
        for ((index, page) in pending.withIndex()) {
            onProgress(index, pending.size, page.title.ifBlank { "Untitled page" })
            val oldImageFile = File(page.imageUrl)
            val oldThumbFile = page.thumbnailUrl?.let { File(it) }
            val result = runCatching {
                ImageProcessor.reprocessExistingFile(oldImageFile, imagesDir, thumbsDir)
            }.getOrNull()

            if (result != null) {
                pageDao.updatePage(
                    page.copy(
                        imageUrl = result.imagePath,
                        thumbnailUrl = result.thumbnailPath,
                        whiteBackgroundProcessed = result.whiteBackgroundProcessed,
                        whiteBackgroundVersion = result.whiteBackgroundVersion
                    )
                )
                if (oldImageFile.exists() && oldImageFile.absolutePath != result.imagePath) {
                    oldImageFile.delete()
                }
                if (oldThumbFile != null && oldThumbFile.exists() && oldThumbFile.absolutePath != result.thumbnailPath) {
                    oldThumbFile.delete()
                }
                processed++
            } else {
                failed++
            }
            onProgress(index + 1, pending.size, page.title.ifBlank { "Untitled page" })
        }
        ColorNestDocumentsProvider.notifyChanged(context)
        Pair(processed, failed)
    }

    suspend fun findDuplicateGroups(
        onProgress: (done: Int, total: Int) -> Unit
    ): List<DuplicateGroup> = withContext(Dispatchers.IO) {
        DuplicateFinder.findDuplicateGroups(pageDao.getAllPages(), onProgress)
    }

    suspend fun deleteDuplicatePages(
        pagesToDelete: List<Page>,
        onProgress: (done: Int, total: Int) -> Unit
    ): Int = withContext(Dispatchers.IO) {
        val allPages = pageDao.getAllPages()
        val driveRefCounts = mutableMapOf<String, Int>()
        for (p in allPages) {
            val id = p.driveFileId ?: continue
            driveRefCounts[id] = (driveRefCounts[id] ?: 0) + 1
        }
        var deleted = 0
        for (page in pagesToDelete) {
            val driveId = page.driveFileId
            if (driveId != null && (driveRefCounts[driveId] ?: 0) > 1) {
                deleteLocalFilesForPage(page)
                pageDao.deletePage(page)
                driveRefCounts[driveId] = (driveRefCounts[driveId] ?: 1) - 1
            } else {
                deletePage(page)
            }
            deleted++
            onProgress(deleted, pagesToDelete.size)
        }
        ColorNestDocumentsProvider.notifyChanged(context)
        deleted
    }

    suspend fun getStorageUsedBytes(): Long = withContext(Dispatchers.IO) {
        var total = 0L
        imagesDir.walkTopDown().filter { it.isFile }.forEach { total += it.length() }
        total
    }

    // ---------- Backup Export / Restore ----------

    suspend fun exportBackup(uri: Uri) = withContext(Dispatchers.IO) {
        BackupManager.exportToUri(
            context = context,
            uri = uri,
            pages = pageDao.getAllPages(),
            categories = categoryDao.getAllCategories(),
            tags = tagDao.getAllTags()
        )
    }

    suspend fun importBackup(uri: Uri) = withContext(Dispatchers.IO) {
        val restored = BackupManager.parseAndExtractFromUri(context, uri, imagesDir, thumbsDir)
        pageDao.deleteAllPages()
        categoryDao.deleteAllCategories()
        tagDao.deleteAllTags()

        // Preserve category parent relationships cleanly while letting Room re-assign or keep IDs
        if (restored.categories.isNotEmpty()) {
            categoryDao.insertCategories(restored.categories)
        }
        if (restored.pages.isNotEmpty()) {
            pageDao.insertPages(restored.pages)
        }
        if (restored.tags.isNotEmpty()) {
            tagDao.insertTags(restored.tags)
        }
        ColorNestDocumentsProvider.notifyChanged(context)
    }

    // ---------- Drive actions ----------

    suspend fun trashCloudFolder(): Boolean = driveSyncManager.trashRootFolder()
    suspend fun isDriveConnected(): Boolean = driveSyncManager.isConnected()
    suspend fun getDriveAccountName(): String? = driveSyncManager.getAccountName()
    suspend fun disconnectDrive() = driveSyncManager.disconnect()
    suspend fun getLastDriveSync(): Long? = driveSyncManager.getLastSyncMillis()
    suspend fun isDriveScanOnOpen(): Boolean = driveSyncManager.isScanOnOpenEnabled()
    suspend fun setDriveScanOnOpen(enabled: Boolean) = driveSyncManager.setScanOnOpenEnabled(enabled)
    suspend fun syncDrivePreviews(): SyncResult = driveSyncManager.syncPreviews()
    suspend fun listDriveFiles(): Pair<Boolean, List<DriveBrowserEntry>> = driveSyncManager.listDriveFiles()
    suspend fun downloadDriveEntry(entry: DriveBrowserEntry): Int = driveSyncManager.downloadDriveEntry(entry)
    suspend fun downloadPreviewPage(page: Page): Pair<Int, Boolean> = driveSyncManager.downloadPreviewPage(page)
    suspend fun fetchTempPreviewBytes(page: Page): ByteArray? = driveSyncManager.fetchTempPreviewBytes(page)
    suspend fun downloadDriveFileToCache(page: Page): File? = driveSyncManager.downloadDriveFileToCache(page)

    // ---------- Wipe ----------

    suspend fun wipeAllData() = withContext(Dispatchers.IO) {
        imagesDir.walkBottomUp().forEach { if (it != imagesDir) it.delete() }
        pageDao.deleteAllPages()
        categoryDao.deleteAllCategories()
        tagDao.deleteAllTags()
        settingDao.deleteAllSettings()

        val defaults = listOf(
            Category(name = "Animals", parentId = null),
            Category(name = "Mandalas & Patterns", parentId = null),
            Category(name = "Fantasy & Mythical", parentId = null),
            Category(name = "Botanical & Nature", parentId = null),
            Category(name = "Architecture", parentId = null)
        )
        categoryDao.insertCategories(defaults)
        ColorNestDocumentsProvider.notifyChanged(context)
    }

    // ---------- Import Images & PDFs ----------

    suspend fun importUri(
        uri: Uri,
        removeWhiteBackground: Boolean = true,
        onProgress: (text: String, percent: Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        val mimeType = context.contentResolver.getType(uri) ?: ""
        val fileName = getFileName(uri)
        val geminiKey = settingDao.getSetting("geminiApiKey").orEmpty().trim()
        val autoTagEnabled = settingDao.getSetting("aiAutoTagUploads") == "true" && geminiKey.isNotEmpty()

        if (mimeType.contains("pdf", ignoreCase = true) || fileName.endsWith(".pdf", ignoreCase = true)) {
            onProgress("Reading PDF...", 10)
            val baseTitle = fileName.replace(Regex("\\.pdf$", RegexOption.IGNORE_CASE), "").ifBlank { "Imported PDF" }
            convertPdfToPages(uri, baseTitle, removeWhiteBackground, autoTagEnabled, geminiKey, onProgress)
        } else {
            onProgress("Processing image...", 40)
            var inputStream: InputStream? = null
            try {
                inputStream = context.contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                if (bitmap != null) {
                    onProgress(if (autoTagEnabled) "Saving & tagging image..." else "Saving image...", 75)
                    val processed = ImageProcessor.processBitmapAndSave(
                        bitmap = bitmap,
                        imagesDir = imagesDir,
                        thumbsDir = thumbsDir,
                        baseName = "img",
                        removeWhitePaper = removeWhiteBackground
                    )
                    bitmap.recycle()

                    val cleanTitle = if (fileName.contains('.')) {
                        fileName.substringBeforeLast('.').ifBlank { "Untitled page" }
                    } else {
                        fileName.ifBlank { "Untitled page" }
                    }
                    val now = System.currentTimeMillis()
                    val insertedId = pageDao.insertPage(
                        Page(
                            title = cleanTitle,
                            imageUrl = processed.imagePath,
                            thumbnailUrl = processed.thumbnailPath,
                            isUncategorized = true,
                            whiteBackgroundProcessed = processed.whiteBackgroundProcessed,
                            whiteBackgroundVersion = processed.whiteBackgroundVersion,
                            syncId = UUID.randomUUID().toString(),
                            createdAt = now,
                            updatedAt = now
                        )
                    )
                    if (autoTagEnabled) {
                        runCatching {
                            val page = pageDao.getPageById(insertedId)
                            if (page != null) {
                                val tags = suggestTagsForPage(page, geminiKey)
                                applyAiTagSuggestions(page, tags)
                            }
                        }
                    }
                    ColorNestDocumentsProvider.notifyChanged(context)
                }
            } finally {
                inputStream?.close()
            }
        }
    }

    private suspend fun convertPdfToPages(
        uri: Uri,
        baseTitle: String,
        removeWhiteBackground: Boolean,
        autoTagEnabled: Boolean,
        geminiKey: String,
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

            for (i in 0 until pageCount) {
                val percent = ((i + 1) * 100) / pageCount.coerceAtLeast(1)
                onProgress(
                    if (autoTagEnabled) "Saving & tagging page ${i + 1} of $pageCount..."
                    else "Converting page ${i + 1} of $pageCount...",
                    percent
                )

                val page = pdfRenderer.openPage(i)
                val maxPdfSide = max(page.width, page.height).coerceAtLeast(1)
                val scale = min(3.0f, 2200f / maxPdfSide.toFloat()).coerceAtLeast(1.0f)
                val w = (page.width * scale).roundToInt().coerceAtLeast(1)
                val h = (page.height * scale).roundToInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(AndroidColor.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                val processed = ImageProcessor.processBitmapAndSave(
                    bitmap = bitmap,
                    imagesDir = imagesDir,
                    thumbsDir = thumbsDir,
                    baseName = "pdf_page",
                    removeWhitePaper = removeWhiteBackground
                )
                bitmap.recycle()

                val now = System.currentTimeMillis()
                val insertedId = pageDao.insertPage(
                    Page(
                        title = "$baseTitle - Page ${i + 1}",
                        imageUrl = processed.imagePath,
                        thumbnailUrl = processed.thumbnailPath,
                        isUncategorized = true,
                        whiteBackgroundProcessed = processed.whiteBackgroundProcessed,
                        whiteBackgroundVersion = processed.whiteBackgroundVersion,
                        syncId = UUID.randomUUID().toString(),
                        createdAt = now,
                        updatedAt = now
                    )
                )

                if (autoTagEnabled) {
                    runCatching {
                        val savedPage = pageDao.getPageById(insertedId)
                        if (savedPage != null) {
                            val tags = suggestTagsForPage(savedPage, geminiKey)
                            applyAiTagSuggestions(savedPage, tags)
                        }
                    }
                }
            }

            pdfRenderer.close()
            fileDescriptor.close()
            ColorNestDocumentsProvider.notifyChanged(context)
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
                    name = it.getString(index) ?: name
                }
            }
        }
        return name
    }
}
