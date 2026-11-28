package com.yourname.colornest.data.drive

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File as DriveFile
import com.yourname.colornest.data.dao.CategoryDao
import com.yourname.colornest.data.dao.PageDao
import com.yourname.colornest.data.dao.SettingDao
import com.yourname.colornest.data.dao.TagDao
import com.yourname.colornest.data.model.Category
import com.yourname.colornest.data.model.Page
import com.yourname.colornest.data.model.Setting
import com.yourname.colornest.data.model.TagEntity
import com.yourname.colornest.data.processing.ImageProcessor
import com.yourname.colornest.provider.ColorNestDocumentsProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class SyncResult(
    val notConnected: Boolean = false,
    val previewsImported: Int = 0,
    val pdfsArchived: Int = 0,
    val uploaded: Int = 0,
    val downloaded: Int = 0,
    val moved: Int = 0,
    val renamed: Int = 0,
    val removedLocal: Int = 0,
    val unlinkedLocal: Int = 0
) {
    fun summary(): String {
        if (notConnected) return "Not connected to Google Drive"
        val parts = mutableListOf<String>()
        if (previewsImported > 0) parts.add("$previewsImported preview${if (previewsImported == 1) "" else "s"} imported")
        if (pdfsArchived > 0) parts.add("$pdfsArchived PDF${if (pdfsArchived == 1) "" else "s"} archived")
        if (uploaded > 0) parts.add("$uploaded uploaded")
        if (downloaded > 0) parts.add("$downloaded downloaded")
        if (moved > 0) parts.add("$moved moved")
        if (renamed > 0) parts.add("$renamed renamed")
        if (removedLocal > 0) parts.add("$removedLocal preview(s) removed")
        if (unlinkedLocal > 0) parts.add("$unlinkedLocal local original(s) kept offline")
        return if (parts.isEmpty()) "Sync complete — everything is up to date"
        else "Sync complete — " + parts.joinToString(", ")
    }
}

data class ScanProgress(
    val active: Boolean = false,
    val percent: Int = 0,
    val message: String = ""
)

data class DrivePageMetadata(
    val title: String? = null,
    val complexity: String? = null,
    val time: String? = null,
    val tool: String? = null,
    val isFavorite: Boolean = false,
    val isCompleted: Boolean = false,
    val progress: Int = 0,
    val isUncategorized: Boolean? = null,
    val driveFileId: String? = null,
    val syncId: String? = null,
    val updatedAt: Long? = null,
    val categoryPath: List<String> = emptyList(),
    val tags: List<String> = emptyList()
)

data class DriveBrowserEntry(
    val id: String,
    val name: String,
    val mimeType: String,
    val thumbnailLink: String?,
    val parentIds: List<String>,
    val isPdf: Boolean,
    val metadata: DrivePageMetadata?,
    val categoryId: Long?,
    val inLibrary: Boolean,
    val libraryPreviewOnly: Boolean,
    val importedPdf: Boolean
)

private data class RemoteFile(
    val id: String,
    val name: String,
    val mimeType: String,
    val parentIds: List<String>,
    val thumbnailLink: String? = null
)

class DriveSyncManager(
    private val context: Context,
    private val pageDao: PageDao,
    private val categoryDao: CategoryDao,
    private val settingDao: SettingDao,
    private val tagDao: TagDao
) {
    companion object {
        const val ROOT_FOLDER_NAME = "ColorNest"
        const val UNTAGGED_FOLDER_NAME = "Untagged"
        private const val PDF_INBOX_FOLDER_NAME = "PDF Inbox"
        private const val PROCESSED_PDFS_FOLDER_NAME = "Processed PDFs"
        private const val METADATA_FOLDER_NAME = "Metadata"
        private const val PAGE_META_PREFIX = ".colornest-page-"
        private const val FOLDER_MIME_TYPE = "application/vnd.google-apps.folder"

        private const val KEY_ACCOUNT = "driveAccountName"
        private const val KEY_SCAN_ON_OPEN = "driveScanOnOpen"
        private const val KEY_LAST_SYNC = "driveLastSync"
        private const val KEY_TOMBSTONES = "driveTombstones"
        private const val KEY_PROCESSED_PDF_IDS = "driveProcessedPdfIds"
        private const val MAX_TOMBSTONES = 500
        private val SCOPES = listOf(DriveScopes.DRIVE_FILE, DriveScopes.DRIVE_READONLY)

        fun largeThumbnailUrl(link: String?): String? {
            if (link.isNullOrBlank()) return null
            return link.replace(Regex("=s\\d+$"), "=s1600")
        }
    }

    private val imagesDir: File by lazy {
        File(context.filesDir, "colornest").apply { if (!exists()) mkdirs() }
    }

    private val thumbsDir: File by lazy {
        File(context.filesDir, "colornest/thumbs").apply { if (!exists()) mkdirs() }
    }

    private val _scanProgress = MutableStateFlow<ScanProgress?>(null)
    val scanProgress: StateFlow<ScanProgress?> = _scanProgress.asStateFlow()

    // ---------- Connection state ----------

    suspend fun isConnected(): Boolean = withContext(Dispatchers.IO) {
        !settingDao.getSetting(KEY_ACCOUNT).isNullOrBlank()
    }

    suspend fun getAccountName(): String? = withContext(Dispatchers.IO) {
        settingDao.getSetting(KEY_ACCOUNT)
    }

    suspend fun connect(accountName: String) = withContext(Dispatchers.IO) {
        val previousAccount = settingDao.getSetting(KEY_ACCOUNT)
        if (!previousAccount.isNullOrBlank() && previousAccount != accountName) {
            pageDao.getAllPages().forEach { page ->
                if (page.driveFileId != null) {
                    pageDao.updatePage(page.copy(driveFileId = null))
                }
            }
            categoryDao.getAllCategories().forEach { category ->
                if (category.driveFolderId != null) {
                    categoryDao.updateCategory(category.copy(driveFolderId = null))
                }
            }
            settingDao.deleteSetting(KEY_TOMBSTONES)
            settingDao.deleteSetting(KEY_PROCESSED_PDF_IDS)
        }
        settingDao.setSetting(Setting(key = KEY_ACCOUNT, value = accountName))
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        settingDao.deleteSetting(KEY_ACCOUNT)
    }

    suspend fun isScanOnOpenEnabled(): Boolean = withContext(Dispatchers.IO) {
        settingDao.getSetting(KEY_SCAN_ON_OPEN)?.toBooleanStrictOrNull() ?: true
    }

    suspend fun setScanOnOpenEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        settingDao.setSetting(Setting(key = KEY_SCAN_ON_OPEN, value = enabled.toString()))
    }

    suspend fun getLastSyncMillis(): Long? = withContext(Dispatchers.IO) {
        settingDao.getSetting(KEY_LAST_SYNC)?.toLongOrNull()
    }

    // ---------- Drive service ----------

    private suspend fun buildService(): Drive? = withContext(Dispatchers.IO) {
        val accountName = settingDao.getSetting(KEY_ACCOUNT)
        if (accountName.isNullOrBlank()) return@withContext null
        val credential = GoogleAccountCredential.usingOAuth2(context, SCOPES)
            .setSelectedAccountName(accountName)
        Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        )
            .setApplicationName("ColorNest")
            .build()
    }

    // ---------- Preview-only sync (matches driveSync.js) ----------

    suspend fun syncPreviews(): SyncResult = withContext(Dispatchers.IO) {
        val service = buildService() ?: return@withContext SyncResult(notConnected = true)
        try {
            _scanProgress.value = ScanProgress(active = true, percent = 5, message = "Connecting to Google Drive…")
            val rootId = getOrCreateFolder(service, ROOT_FOLDER_NAME, "root")
            val metadataFolderId = getOrCreateFolder(service, METADATA_FOLDER_NAME, rootId)
            val pdfInboxId = getOrCreateFolder(service, PDF_INBOX_FOLDER_NAME, rootId)
            val processedPdfsId = getOrCreateFolder(service, PROCESSED_PDFS_FOLDER_NAME, rootId)

            var previewsImported = 0
            var pdfsArchived = 0
            val processedPdfIds = readProcessedPdfIds().toMutableSet()
            val pages = pageDao.getAllPages()
            val localDriveIds = pages.mapNotNull { it.driveFileId }.toMutableSet()
            val categories = categoryDao.getAllCategories()
            val categoryIdByFolder = categories
                .filter { !it.driveFolderId.isNullOrBlank() }
                .associate { it.driveFolderId!! to it.id }

            // PDF Inbox
            _scanProgress.value = ScanProgress(active = true, percent = 10, message = "Checking PDF Inbox…")
            val inboxFiles = listChildren(service, pdfInboxId)
            val pdfFiles = inboxFiles.filter {
                it.mimeType == "application/pdf" || it.name.endsWith(".pdf", ignoreCase = true)
            }
            for ((index, file) in pdfFiles.withIndex()) {
                val pct = 10 + (((index + 1) * 25) / pdfFiles.size.coerceAtLeast(1))
                _scanProgress.value = ScanProgress(active = true, percent = pct, message = "Checking PDF ${index + 1} of ${pdfFiles.size}: ${file.name}")
                runCatching {
                    if (file.id !in processedPdfIds && file.id !in localDriveIds) {
                        importPreviewEntry(service, file, null, categoryIdByFolder)
                        localDriveIds.add(file.id)
                        processedPdfIds.add(file.id)
                        saveProcessedPdfIds(processedPdfIds)
                        previewsImported++
                    }
                    moveFile(service, file.id, file.parentIds, processedPdfsId)
                    pdfsArchived++
                }
            }

            _scanProgress.value = ScanProgress(active = true, percent = 40, message = "Looking for new Drive files…")
            val remoteFiles = listTree(service, rootId)
            val tombstones = readTombstones()
            val metadataByDriveId = readMetadataSidecars(service, rootId, metadataFolderId)

            val remoteMedia = remoteFiles.filter {
                !it.name.startsWith(PAGE_META_PREFIX) &&
                    (it.mimeType.startsWith("image/") ||
                        it.mimeType == "application/pdf" ||
                        it.name.endsWith(".pdf", ignoreCase = true))
            }

            for ((index, remote) in remoteMedia.withIndex()) {
                val pct = 45 + (((index + 1) * 50) / remoteMedia.size.coerceAtLeast(1))
                _scanProgress.value = ScanProgress(active = true, percent = pct, message = "Importing previews ${index + 1} of ${remoteMedia.size}")
                if (remote.id in localDriveIds || remote.id in tombstones) continue
                if (remote.id in processedPdfIds) continue

                val metadata = metadataByDriveId[remote.id]
                importPreviewEntry(service, remote, metadata, categoryIdByFolder)
                localDriveIds.add(remote.id)
                previewsImported++
            }

            settingDao.setSetting(
                Setting(key = KEY_LAST_SYNC, value = System.currentTimeMillis().toString())
            )
            ColorNestDocumentsProvider.notifyChanged(context)
            _scanProgress.value = ScanProgress(active = false, percent = 100, message = "Previews imported")
            SyncResult(previewsImported = previewsImported, pdfsArchived = pdfsArchived)
        } catch (e: Exception) {
            _scanProgress.value = null
            throw e
        }
    }

    /**
     * Full two-way sync (kept for DriveConnectActivity). Imports Drive previews
     * and uploads/reconciles local pages.
     */
    suspend fun sync(): SyncResult = withContext(Dispatchers.IO) {
        val service = buildService() ?: return@withContext SyncResult(notConnected = true)

        var uploaded = 0
        var downloaded = 0
        var moved = 0
        var renamed = 0
        var removedLocal = 0
        var unlinkedLocal = 0

        val rootId = getOrCreateFolder(service, ROOT_FOLDER_NAME, "root")
        val untaggedId = getOrCreateFolder(service, UNTAGGED_FOLDER_NAME, rootId)
        val categories = categoryDao.getAllCategories()
        val folderIdByCategory = mutableMapOf<Long, String>()
        for (category in categories) {
            folderIdByCategory[category.id] =
                ensureCategoryFolder(service, rootId, categories, category)
        }

        val remoteById = listTree(service, rootId).associateBy { it.id }
        val categoryIdByFolder = folderIdByCategory.entries
            .associate { (categoryId, folderId) -> folderId to categoryId }
        val tombstones = readTombstones()
        val localDriveIds = pageDao.getAllPages()
            .mapNotNull { it.driveFileId }
            .toSet()

        val pages = pageDao.getAllPages()
        for (page in pages) {
            val driveId = page.driveFileId
            if (driveId == null) {
                if (page.isPreviewOnly) continue
                val localFile = File(page.imageUrl)
                if (!localFile.exists()) continue
                val folderId = folderIdByCategory[page.categoryId] ?: untaggedId
                val newId = uploadFile(service, localFile, driveFileName(page), folderId)
                pageDao.updatePage(page.copy(driveFileId = newId))
                uploaded++
                continue
            }

            val remote = remoteById[driveId]
            if (remote == null) {
                when (checkRemoteState(service, driveId)) {
                    RemoteState.TRASHED, RemoteState.GONE -> {
                        if (page.isPreviewOnly) {
                            // A preview has no locally owned full-resolution original.
                            File(page.imageUrl).takeIf { it.isFile }?.delete()
                            page.thumbnailUrl?.let { File(it).takeIf { file -> file.isFile }?.delete() }
                            pageDao.deletePage(page)
                            removedLocal++
                        } else {
                            // A cloud-side deletion must not destroy the user's offline original.
                            // Unlink it so the next sync can upload it again if desired.
                            pageDao.updatePage(
                                page.copy(
                                    driveFileId = null,
                                    updatedAt = System.currentTimeMillis()
                                )
                            )
                            unlinkedLocal++
                        }
                    }
                    RemoteState.EXISTS_ELSEWHERE -> Unit
                }
                continue
            }

            val expectedFolder = folderIdByCategory[page.categoryId] ?: untaggedId
            if (expectedFolder !in remote.parentIds) {
                moveFile(service, driveId, remote.parentIds, expectedFolder)
                moved++
            }
            val expectedName = driveFileName(page)
            if (remote.name != expectedName) {
                renameFile(service, driveId, expectedName)
                renamed++
            }
        }

        val processedPdfIds = readProcessedPdfIds()
        for (remote in remoteById.values) {
            if (remote.name.startsWith(PAGE_META_PREFIX)) continue
            val isPdf = remote.mimeType == "application/pdf" || remote.name.endsWith(".pdf", ignoreCase = true)
            val isImage = remote.mimeType.startsWith("image/")
            if (!isImage && !isPdf) continue
            if (remote.id in localDriveIds || remote.id in tombstones) continue
            if (isPdf && remote.id in processedPdfIds) continue

            importPreviewEntry(service, remote, null, categoryIdByFolder)
            downloaded++
        }

        settingDao.setSetting(
            Setting(key = KEY_LAST_SYNC, value = System.currentTimeMillis().toString())
        )
        ColorNestDocumentsProvider.notifyChanged(context)
        SyncResult(
            uploaded = uploaded,
            downloaded = downloaded,
            moved = moved,
            renamed = renamed,
            removedLocal = removedLocal,
            unlinkedLocal = unlinkedLocal
        )
    }

    // ---------- Drive Browser & On-Demand Download ----------

    suspend fun listDriveFiles(): Pair<Boolean, List<DriveBrowserEntry>> = withContext(Dispatchers.IO) {
        val service = buildService() ?: return@withContext Pair(false, emptyList())
        val rootId = findFolder(service, ROOT_FOLDER_NAME, "root")
            ?: return@withContext Pair(true, emptyList())
        val metadataFolderId = findFolder(service, METADATA_FOLDER_NAME, rootId)
        val metadataByDriveId = readMetadataSidecars(service, rootId, metadataFolderId)

        val remoteFiles = listTree(service, rootId)
        val pages = pageDao.getAllPages()
        val localDriveIds = pages.mapNotNull { it.driveFileId }.toSet()
        val previewOnlyDriveIds = pages.filter { it.driveFileId != null && it.isPreviewOnly }.mapNotNull { it.driveFileId }.toSet()
        val processedPdfIds = readProcessedPdfIds()
        val categories = categoryDao.getAllCategories()
        val categoryIdByFolder = categories
            .filter { !it.driveFolderId.isNullOrBlank() }
            .associate { it.driveFolderId!! to it.id }

        val entries = mutableListOf<DriveBrowserEntry>()
        for (remote in remoteFiles) {
            if (remote.name.startsWith(PAGE_META_PREFIX)) continue
            val isPdf = remote.mimeType == "application/pdf" || remote.name.endsWith(".pdf", ignoreCase = true)
            val isImage = remote.mimeType.startsWith("image/")
            if (!isPdf && !isImage) continue

            entries.add(
                DriveBrowserEntry(
                    id = remote.id,
                    name = remote.name,
                    mimeType = remote.mimeType,
                    thumbnailLink = remote.thumbnailLink,
                    parentIds = remote.parentIds,
                    isPdf = isPdf,
                    metadata = metadataByDriveId[remote.id],
                    categoryId = categoryIdByFolder[remote.parentIds.firstOrNull()],
                    inLibrary = remote.id in localDriveIds,
                    libraryPreviewOnly = remote.id in previewOnlyDriveIds,
                    importedPdf = isPdf && remote.id in processedPdfIds
                )
            )
        }
        entries.sortBy { it.name.lowercase() }
        Pair(true, entries)
    }

    suspend fun downloadDriveEntry(entry: DriveBrowserEntry): Int = withContext(Dispatchers.IO) {
        val service = buildService() ?: throw IllegalStateException("Not connected to Google Drive")
        if (entry.isPdf) {
            return@withContext downloadDrivePdf(service, entry)
        }

        val existing = pageDao.getPageByDriveFileId(entry.id)
        if (existing != null && !existing.isPreviewOnly) {
            throw IllegalStateException("This file is already in your library.")
        }

        val tempFile = File(context.cacheDir, "drive_dl_${System.currentTimeMillis()}.tmp")
        try {
            downloadFile(service, entry.id, tempFile)
            val bitmap = decodeSampledBitmapFile(tempFile)
                ?: throw IllegalStateException("Could not decode downloaded image.")
            val processed = try {
                ImageProcessor.processBitmapAndSave(
                    bitmap = bitmap,
                    imagesDir = imagesDir,
                    thumbsDir = thumbsDir,
                    baseName = "drive",
                    removeWhitePaper = true
                )
            } finally {
                bitmap.recycle()
            }

            if (existing != null) {
                pageDao.updatePage(
                    existing.copy(
                        imageUrl = processed.imagePath,
                        thumbnailUrl = processed.thumbnailPath ?: existing.thumbnailUrl,
                        isPreviewOnly = false,
                        whiteBackgroundProcessed = processed.whiteBackgroundProcessed,
                        whiteBackgroundVersion = processed.whiteBackgroundVersion,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                ColorNestDocumentsProvider.notifyChanged(context)
                return@withContext 1
            }

            val meta = entry.metadata
            var categoryId = entry.categoryId
            if (meta != null && meta.categoryPath.isNotEmpty()) {
                categoryId = ensureLocalCategoryPath(meta.categoryPath)
            }
            val importedTags = includeCategoryTag(categoryId, meta?.tags.orEmpty())
            val title = meta?.title?.takeIf { it.isNotBlank() }
                ?: entry.name.substringBeforeLast('.').ifBlank { "Untitled page" }

            pageDao.insertPage(
                Page(
                    title = title,
                    categoryId = categoryId,
                    complexity = meta?.complexity ?: "Beginner",
                    time = meta?.time ?: "TBD",
                    tool = meta?.tool ?: "TBD",
                    imageUrl = processed.imagePath,
                    thumbnailUrl = processed.thumbnailPath,
                    isPreviewOnly = false,
                    driveThumbnailLink = entry.thumbnailLink,
                    isFavorite = meta?.isFavorite ?: false,
                    isCompleted = meta?.isCompleted ?: false,
                    progress = meta?.progress ?: 0,
                    isUncategorized = meta?.isUncategorized ?: (categoryId == null),
                    tags = importedTags,
                    driveFileId = entry.id,
                    whiteBackgroundProcessed = processed.whiteBackgroundProcessed,
                    whiteBackgroundVersion = processed.whiteBackgroundVersion,
                    syncId = meta?.syncId ?: UUID.randomUUID().toString(),
                    updatedAt = meta?.updatedAt ?: System.currentTimeMillis()
                )
            )
            ColorNestDocumentsProvider.notifyChanged(context)
            1
        } finally {
            tempFile.delete()
        }
    }

    private suspend fun downloadDrivePdf(service: Drive, entry: DriveBrowserEntry): Int {
        val processedPdfIds = readProcessedPdfIds().toMutableSet()
        val existingPreview = pageDao.getPageByDriveFileId(entry.id)?.takeIf { it.isPreviewOnly }
        if (entry.id in processedPdfIds && existingPreview == null) {
            throw IllegalStateException("This PDF was already imported.")
        }

        val rootId = getOrCreateFolder(service, ROOT_FOLDER_NAME, "root")
        val pdfInboxId = findFolder(service, PDF_INBOX_FOLDER_NAME, rootId)
        val processedPdfsId = getOrCreateFolder(service, PROCESSED_PDFS_FOLDER_NAME, rootId)

        val tempPdf = File(context.cacheDir, "drive_pdf_${System.currentTimeMillis()}.pdf")
        try {
            downloadFile(service, entry.id, tempPdf)
            var pageCount = 0
            val descriptor = ParcelFileDescriptor.open(tempPdf, ParcelFileDescriptor.MODE_READ_ONLY)
            try {
                val renderer = PdfRenderer(descriptor)
                try {
                    pageCount = renderer.pageCount
                    if (pageCount <= 0) throw IllegalStateException("The PDF did not contain any pages.")

                    val baseTitle = entry.name.replace(Regex("\\.pdf$", RegexOption.IGNORE_CASE), "").ifBlank { "Imported PDF" }
                    val pdfTags = includeCategoryTag(entry.categoryId, emptyList())
                    val now = System.currentTimeMillis()

                    for (i in 0 until pageCount) {
                        val pdfPage = renderer.openPage(i)
                        try {
                            val maxPdfSide = max(pdfPage.width, pdfPage.height).coerceAtLeast(1)
                            val scale = min(3.0f, 2200f / maxPdfSide.toFloat()).coerceAtLeast(1.0f)
                            val width = (pdfPage.width * scale).roundToInt().coerceAtLeast(1)
                            val height = (pdfPage.height * scale).roundToInt().coerceAtLeast(1)
                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            val processed = try {
                                bitmap.eraseColor(AndroidColor.WHITE)
                                pdfPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                ImageProcessor.processBitmapAndSave(
                                    bitmap = bitmap,
                                    imagesDir = imagesDir,
                                    thumbsDir = thumbsDir,
                                    baseName = "drive_pdf",
                                    removeWhitePaper = true
                                )
                            } finally {
                                bitmap.recycle()
                            }

                            pageDao.insertPage(
                                Page(
                                    title = "$baseTitle - Page ${i + 1}",
                                    categoryId = entry.categoryId,
                                    imageUrl = processed.imagePath,
                                    thumbnailUrl = processed.thumbnailPath,
                                    isUncategorized = entry.categoryId == null,
                                    tags = pdfTags,
                                    whiteBackgroundProcessed = processed.whiteBackgroundProcessed,
                                    whiteBackgroundVersion = processed.whiteBackgroundVersion,
                                    syncId = UUID.randomUUID().toString(),
                                    createdAt = now,
                                    updatedAt = now
                                )
                            )
                        } finally {
                            pdfPage.close()
                        }
                    }
                } finally {
                    renderer.close()
                }
            } finally {
                descriptor.close()
            }

            if (existingPreview != null) {
                existingPreview.thumbnailUrl?.let { File(it).takeIf { file -> file.isFile }?.delete() }
                pageDao.deletePage(existingPreview)
            }
            processedPdfIds.add(entry.id)
            saveProcessedPdfIds(processedPdfIds)

            if (pdfInboxId != null && pdfInboxId in entry.parentIds) {
                runCatching { moveFile(service, entry.id, entry.parentIds, processedPdfsId) }
            }
            ColorNestDocumentsProvider.notifyChanged(context)
            return pageCount
        } finally {
            tempPdf.delete()
        }
    }

    suspend fun downloadPreviewPage(page: Page): Pair<Int, Boolean> = withContext(Dispatchers.IO) {
        val service = buildService() ?: throw IllegalStateException("Not connected to Google Drive")
        val driveId = page.driveFileId ?: throw IllegalStateException("This page is not linked to a Drive file.")
        val meta = service.files().get(driveId)
            .setFields("id,name,mimeType,parents,thumbnailLink")
            .execute()
        val isPdf = meta.mimeType == "application/pdf" || (meta.name ?: "").endsWith(".pdf", ignoreCase = true)
        val entry = DriveBrowserEntry(
            id = driveId,
            name = meta.name ?: page.title,
            mimeType = meta.mimeType ?: "image/png",
            thumbnailLink = meta.thumbnailLink,
            parentIds = meta.parents ?: emptyList(),
            isPdf = isPdf,
            metadata = null,
            categoryId = page.categoryId,
            inLibrary = true,
            libraryPreviewOnly = true,
            importedPdf = false
        )
        val added = downloadDriveEntry(entry)
        Pair(added, isPdf)
    }

    suspend fun fetchTempPreviewBytes(page: Page): ByteArray? = withContext(Dispatchers.IO) {
        val link = largeThumbnailUrl(page.driveThumbnailLink) ?: return@withContext null
        fetchUrlBytesWithCredential(link)
    }

    suspend fun fetchThumbnailBytes(thumbnailLink: String?): ByteArray? = withContext(Dispatchers.IO) {
        if (thumbnailLink.isNullOrBlank()) return@withContext null
        fetchUrlBytesWithCredential(thumbnailLink)
    }

    suspend fun downloadDriveFileToCache(page: Page): File? = withContext(Dispatchers.IO) {
        val driveId = page.driveFileId ?: return@withContext null
        val service = buildService() ?: return@withContext null
        val meta = runCatching {
            service.files().get(driveId).setFields("id,name,mimeType").execute()
        }.getOrNull()
        val ext = when {
            meta?.mimeType == "application/pdf" -> "pdf"
            meta?.mimeType == "image/png" -> "png"
            else -> meta?.name?.substringAfterLast('.', "")?.ifBlank { "png" } ?: "png"
        }
        val cleanTitle = page.title.ifBlank { "coloring-page" }
            .replace(Regex("[\\\\/:*?\"<>|]+"), "_")
        val outFile = File(context.cacheDir, "${cleanTitle}_${driveId.take(8)}.$ext")
        downloadFile(service, driveId, outFile)
        outFile
    }

    private suspend fun fetchUrlBytesWithCredential(urlString: String): ByteArray? {
        return runCatching {
            val accountName = settingDao.getSetting(KEY_ACCOUNT)
            val token = if (!accountName.isNullOrBlank()) {
                runCatching {
                    GoogleAccountCredential.usingOAuth2(context, SCOPES)
                        .setSelectedAccountName(accountName)
                        .token
                }.getOrNull()
            } else null

            val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 15000
                if (!token.isNullOrBlank()) {
                    setRequestProperty("Authorization", "Bearer $token")
                }
            }
            try {
                if (conn.responseCode in 200..299) {
                    conn.inputStream.use { it.readBytes() }
                } else null
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

    private suspend fun importPreviewEntry(
        service: Drive,
        remote: RemoteFile,
        metadata: DrivePageMetadata?,
        categoryIdByFolder: Map<String, Long>
    ) {
        var categoryId = categoryIdByFolder[remote.parentIds.firstOrNull()]
        if (metadata != null && metadata.categoryPath.isNotEmpty()) {
            categoryId = ensureLocalCategoryPath(metadata.categoryPath)
        }

        val thumbBytes = remote.thumbnailLink?.let { fetchUrlBytesWithCredential(it) }
        var thumbPath: String? = null
        if (thumbBytes != null && thumbBytes.isNotEmpty()) {
            val thumbFile = File(thumbsDir, "drive_prev_${remote.id}_${System.currentTimeMillis()}.png")
            FileOutputStream(thumbFile).use { it.write(thumbBytes) }
            thumbPath = thumbFile.absolutePath
        }

        val importedTags = includeCategoryTag(categoryId, metadata?.tags.orEmpty())
        val title = metadata?.title?.takeIf { it.isNotBlank() }
            ?: remote.name.substringBeforeLast('.').ifBlank { "Untitled page" }

        pageDao.insertPage(
            Page(
                title = title,
                categoryId = categoryId,
                complexity = metadata?.complexity ?: "Beginner",
                time = metadata?.time ?: "TBD",
                tool = metadata?.tool ?: "TBD",
                imageUrl = thumbPath ?: "",
                thumbnailUrl = thumbPath,
                isPreviewOnly = true,
                driveThumbnailLink = remote.thumbnailLink,
                isFavorite = metadata?.isFavorite ?: false,
                isCompleted = metadata?.isCompleted ?: false,
                progress = metadata?.progress ?: 0,
                isUncategorized = metadata?.isUncategorized ?: (categoryId == null),
                tags = importedTags,
                driveFileId = remote.id,
                syncId = metadata?.syncId ?: UUID.randomUUID().toString(),
                updatedAt = metadata?.updatedAt ?: System.currentTimeMillis()
            )
        )
    }

    private suspend fun includeCategoryTag(categoryId: Long?, existingTags: List<String>): List<String> {
        val merged = LinkedHashSet<String>()
        existingTags.map { it.trim() }.filter { it.isNotEmpty() }.forEach { merged.add(it) }
        if (categoryId != null) {
            val catName = categoryDao.getCategoryById(categoryId)?.name?.trim()
            if (!catName.isNullOrEmpty()) {
                merged.add(catName)
                if (tagDao.getTagByName(catName) == null) {
                    tagDao.insertTag(TagEntity(name = catName))
                }
            }
        }
        return merged.toList()
    }

    private suspend fun ensureLocalCategoryPath(path: List<String>): Long? {
        var parentId: Long? = null
        for (name in path) {
            val existing = categoryDao.getAllCategories()
                .firstOrNull { it.name == name && it.parentId == parentId }
            parentId = existing?.id ?: categoryDao.insertCategory(Category(name = name, parentId = parentId))
        }
        return parentId
    }

    private fun readMetadataSidecars(
        service: Drive,
        rootId: String,
        metadataFolderId: String?
    ): Map<String, DrivePageMetadata> {
        val map = mutableMapOf<String, DrivePageMetadata>()
        val candidates = mutableListOf<RemoteFile>()
        if (metadataFolderId != null) {
            candidates.addAll(listChildren(service, metadataFolderId))
        }
        candidates.addAll(
            listChildren(service, rootId).filter { it.name.startsWith(PAGE_META_PREFIX) }
        )
        for (file in candidates) {
            if (!file.name.startsWith(PAGE_META_PREFIX) || !file.name.endsWith(".json")) continue
            runCatching {
                val out = ByteArrayOutputStream()
                service.files().get(file.id).executeMediaAndDownloadTo(out)
                val obj = JSONObject(out.toString(Charsets.UTF_8.name()))
                val driveFileId = obj.optString("driveFileId", "").takeIf { it.isNotBlank() } ?: return@runCatching
                val pathArr = obj.optJSONArray("categoryPath") ?: JSONArray()
                val path = (0 until pathArr.length()).mapNotNull { idx ->
                    pathArr.optString(idx, "").takeIf { it.isNotBlank() }
                }
                val tagsArr = obj.optJSONArray("tags") ?: JSONArray()
                val tags = (0 until tagsArr.length()).mapNotNull { idx ->
                    tagsArr.optString(idx, "").takeIf { it.isNotBlank() }
                }
                map[driveFileId] = DrivePageMetadata(
                    title = obj.optString("title", "").takeIf { it.isNotBlank() },
                    complexity = obj.optString("complexity", "").takeIf { it.isNotBlank() },
                    time = obj.optString("time", "").takeIf { it.isNotBlank() },
                    tool = obj.optString("tool", "").takeIf { it.isNotBlank() },
                    isFavorite = obj.optBoolean("isFavorite", false) || obj.optInt("isFavorite", 0) == 1,
                    isCompleted = obj.optBoolean("isCompleted", false) || obj.optInt("isCompleted", 0) == 1,
                    progress = obj.optInt("progress", 0),
                    isUncategorized = if (obj.has("isUncategorized")) obj.optBoolean("isUncategorized", false) || obj.optInt("isUncategorized", 0) == 1 else null,
                    driveFileId = driveFileId,
                    syncId = obj.optString("syncId", "").takeIf { it.isNotBlank() },
                    updatedAt = obj.optLong("updatedAt", 0L).takeIf { it > 0L },
                    categoryPath = path,
                    tags = tags
                )
            }
        }
        return map
    }

    // ---------- Single-page propagation ----------

    suspend fun trashPageFile(page: Page) = withContext(Dispatchers.IO) {
        val driveId = page.driveFileId ?: return@withContext
        addTombstone(driveId)
        try {
            val service = buildService() ?: return@withContext
            service.files().update(driveId, DriveFile().setTrashed(true)).execute()
        } catch (_: Exception) {
        }
    }

    suspend fun propagatePageChange(page: Page) = withContext(Dispatchers.IO) {
        val driveId = page.driveFileId ?: return@withContext
        try {
            val service = buildService() ?: return@withContext
            val rootId = getOrCreateFolder(service, ROOT_FOLDER_NAME, "root")
            val untaggedId = getOrCreateFolder(service, UNTAGGED_FOLDER_NAME, rootId)
            val categories = categoryDao.getAllCategories()
            val category = page.categoryId?.let { categoryDao.getCategoryById(it) }
            val expectedFolder = if (category != null) {
                ensureCategoryFolder(service, rootId, categories, category)
            } else {
                untaggedId
            }
            val remote = service.files().get(driveId)
                .setFields("id,name,parents")
                .execute() ?: return@withContext
            val parents = remote.parents ?: emptyList()
            if (expectedFolder !in parents) {
                moveFile(service, driveId, parents, expectedFolder)
            }
            val expectedName = driveFileName(page)
            if (remote.name != expectedName) {
                renameFile(service, driveId, expectedName)
            }
        } catch (_: Exception) {
        }
    }

    suspend fun trashRootFolder(): Boolean = withContext(Dispatchers.IO) {
        val service = buildService() ?: return@withContext false
        try {
            val rootId = findFolder(service, ROOT_FOLDER_NAME, "root")
                ?: return@withContext true
            service.files().update(rootId, DriveFile().setTrashed(true)).execute()
            true
        } catch (_: Exception) {
            false
        }
    }

    // ---------- Folder helpers ----------

    private fun findFolder(service: Drive, name: String, parentId: String): String? {
        val escaped = name.replace("'", "\\'")
        val result = service.files().list()
            .setQ(
                "name = '$escaped' and mimeType = '$FOLDER_MIME_TYPE' " +
                    "and trashed = false and '$parentId' in parents"
            )
            .setFields("files(id)")
            .setPageSize(10)
            .execute()
        return result.files?.firstOrNull()?.id
    }

    private fun getOrCreateFolder(service: Drive, name: String, parentId: String): String {
        findFolder(service, name, parentId)?.let { return it }
        val created = service.files().create(
            DriveFile().apply {
                this.name = name
                mimeType = FOLDER_MIME_TYPE
                parents = listOf(parentId)
            }
        ).setFields("id").execute()
        return created.id
    }

    private suspend fun ensureCategoryFolder(
        service: Drive,
        rootId: String,
        allCategories: List<Category>,
        category: Category
    ): String {
        val chain = mutableListOf<Category>()
        var current: Category? = category
        var guard = 0
        while (current != null && guard++ < 50) {
            chain.add(0, current)
            current = allCategories.find { it.id == current?.parentId }
        }
        var parentId = rootId
        for (node in chain) {
            val folderId = getOrCreateFolder(service, node.name, parentId)
            if (node.driveFolderId != folderId) {
                categoryDao.updateCategory(node.copy(driveFolderId = folderId))
            }
            parentId = folderId
        }
        return parentId
    }

    // ---------- File listing / transfer ----------

    private fun listChildren(service: Drive, folderId: String): List<RemoteFile> {
        val out = mutableListOf<RemoteFile>()
        var pageToken: String? = null
        do {
            val result = service.files().list()
                .setQ("'$folderId' in parents and trashed = false")
                .setFields("nextPageToken, files(id,name,mimeType,parents,thumbnailLink)")
                .setPageSize(1000)
                .setPageToken(pageToken)
                .execute()
            for (file in result.files ?: emptyList()) {
                out.add(
                    RemoteFile(
                        id = file.id,
                        name = file.name,
                        mimeType = file.mimeType,
                        parentIds = file.parents ?: emptyList(),
                        thumbnailLink = file.thumbnailLink
                    )
                )
            }
            pageToken = result.nextPageToken
        } while (pageToken != null)
        return out
    }

    private fun listTree(service: Drive, rootId: String): List<RemoteFile> {
        val out = mutableListOf<RemoteFile>()
        val foldersToVisit = ArrayDeque<String>()
        foldersToVisit.add(rootId)
        while (foldersToVisit.isNotEmpty()) {
            val folderId = foldersToVisit.removeFirst()
            for (child in listChildren(service, folderId)) {
                if (child.mimeType == FOLDER_MIME_TYPE) {
                    foldersToVisit.add(child.id)
                } else {
                    out.add(child)
                }
            }
        }
        return out
    }

    private fun uploadFile(
        service: Drive,
        localFile: File,
        driveName: String,
        folderId: String
    ): String {
        val metadata = DriveFile().apply {
            name = driveName
            parents = listOf(folderId)
        }
        val content = FileContent(mimeTypeFor(localFile.name), localFile)
        return service.files().create(metadata, content)
            .setFields("id")
            .execute().id
    }

    private fun decodeSampledBitmapFile(file: File, maxDimension: Int = 2500): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (max(bounds.outWidth / sampleSize, bounds.outHeight / sampleSize) > maxDimension) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(file.absolutePath, options)
    }

    private fun downloadFile(service: Drive, driveId: String, target: File) {
        service.files().get(driveId).executeMediaAsInputStream().use { input ->
            FileOutputStream(target).use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun moveFile(
        service: Drive,
        driveId: String,
        currentParents: List<String>,
        newParentId: String
    ) {
        service.files().update(driveId, null)
            .setAddParents(newParentId)
            .setRemoveParents(currentParents.joinToString(","))
            .execute()
    }

    private fun renameFile(service: Drive, driveId: String, newName: String) {
        service.files().update(driveId, DriveFile().setName(newName)).execute()
    }

    private enum class RemoteState { TRASHED, GONE, EXISTS_ELSEWHERE }

    private fun checkRemoteState(service: Drive, driveId: String): RemoteState {
        return try {
            val file = service.files().get(driveId).setFields("id,trashed").execute()
            if (file.trashed == true) RemoteState.TRASHED else RemoteState.EXISTS_ELSEWHERE
        } catch (e: GoogleJsonResponseException) {
            if (e.statusCode == 404) RemoteState.GONE else RemoteState.EXISTS_ELSEWHERE
        } catch (_: Exception) {
            RemoteState.EXISTS_ELSEWHERE
        }
    }

    // ---------- Tombstones & Processed PDF IDs ----------

    private suspend fun readTombstones(): Set<String> {
        val raw = settingDao.getSetting(KEY_TOMBSTONES).orEmpty()
        if (raw.isBlank()) return emptySet()
        return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    private suspend fun addTombstone(driveId: String) {
        val updated = (readTombstones() + driveId).toList().takeLast(MAX_TOMBSTONES)
        settingDao.setSetting(Setting(key = KEY_TOMBSTONES, value = updated.joinToString(",")))
    }

    private suspend fun readProcessedPdfIds(): Set<String> {
        val raw = settingDao.getSetting(KEY_PROCESSED_PDF_IDS).orEmpty()
        if (raw.isBlank()) return emptySet()
        return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    private suspend fun saveProcessedPdfIds(ids: Set<String>) {
        settingDao.setSetting(Setting(key = KEY_PROCESSED_PDF_IDS, value = ids.joinToString(",")))
    }

    // ---------- Naming ----------

    private fun sanitizeTitle(title: String): String {
        val cleaned = title.trim()
            .replace(Regex("[\\p{Cntrl}]"), "")
            .take(100)
        return cleaned.ifBlank { "Untitled page" }
    }

    private fun driveFileName(page: Page): String {
        val extension = File(page.imageUrl).extension.ifBlank { "png" }
        return "${sanitizeTitle(page.title)}.$extension"
    }

    private fun mimeTypeFor(fileName: String): String {
        return when (fileName.substringAfterLast('.', "").lowercase()) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            else -> "image/jpeg"
        }
    }
}
