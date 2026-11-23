package com.yourname.colornest.provider

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Point
import android.net.Uri
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import com.yourname.colornest.BuildConfig
import com.yourname.colornest.R
import com.yourname.colornest.data.ColorNestDatabase
import com.yourname.colornest.data.model.Category
import com.yourname.colornest.data.model.Page
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import kotlin.math.max

class ColorNestDocumentsProvider : DocumentsProvider() {

    companion object {
        val AUTHORITY: String
            get() = "${BuildConfig.APPLICATION_ID}.documents"

        private const val ROOT_ID = "colornest_root"
        private const val DOC_ROOT = "root"
        private const val DOC_ALL = "all"
        private const val DOC_FAVORITES = "favorites"
        private const val DOC_UNTAGGED = "untagged"
        private const val PREFIX_CATEGORY = "cat:"
        private const val PREFIX_PAGE = "page:"
        private const val MAX_RECENT_RESULTS = 50

        private val DEFAULT_ROOT_PROJECTION = arrayOf(
            Root.COLUMN_ROOT_ID,
            Root.COLUMN_MIME_TYPES,
            Root.COLUMN_FLAGS,
            Root.COLUMN_ICON,
            Root.COLUMN_TITLE,
            Root.COLUMN_SUMMARY,
            Root.COLUMN_DOCUMENT_ID
        )

        private val DEFAULT_DOCUMENT_PROJECTION = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_LAST_MODIFIED,
            Document.COLUMN_FLAGS,
            Document.COLUMN_SIZE,
            Document.COLUMN_SUMMARY
        )

        fun notifyChanged(context: Context) {
            val resolver = context.contentResolver
            val authority = AUTHORITY
            resolver.notifyChange(DocumentsContract.buildRootsUri(authority), null)
            resolver.notifyChange(DocumentsContract.buildChildDocumentsUri(authority, DOC_ROOT), null)
            resolver.notifyChange(DocumentsContract.buildChildDocumentsUri(authority, DOC_ALL), null)
            resolver.notifyChange(DocumentsContract.buildChildDocumentsUri(authority, DOC_FAVORITES), null)
            resolver.notifyChange(DocumentsContract.buildChildDocumentsUri(authority, DOC_UNTAGGED), null)
            // Category contents are separate SAF directories. Notify each one so
            // external pickers refresh after imports, deletes, and recategorization.
            val categories = runBlocking(Dispatchers.IO) {
                ColorNestDatabase.getDatabase(context).categoryDao().getAllCategories()
            }
            categories.forEach { category ->
                resolver.notifyChange(
                    DocumentsContract.buildChildDocumentsUri(authority, "$PREFIX_CATEGORY${category.id}"),
                    null
                )
            }
        }
    }

    private val database: ColorNestDatabase by lazy {
        ColorNestDatabase.getDatabase(requireNotNull(context))
    }

    override fun onCreate(): Boolean = true

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val ctx = context ?: return MatrixCursor(projection ?: DEFAULT_ROOT_PROJECTION)
        val result = MatrixCursor(projection ?: DEFAULT_ROOT_PROJECTION)

        addProjectedRow(result, listOf(
            Root.COLUMN_ROOT_ID to ROOT_ID,
            Root.COLUMN_MIME_TYPES to "image/*\nimage/png\nimage/jpeg",
            Root.COLUMN_FLAGS to (Root.FLAG_LOCAL_ONLY or Root.FLAG_SUPPORTS_SEARCH or Root.FLAG_SUPPORTS_RECENTS),
            Root.COLUMN_ICON to R.mipmap.ic_launcher,
            Root.COLUMN_TITLE to ctx.getString(R.string.app_name),
            Root.COLUMN_SUMMARY to ctx.getString(R.string.documents_root_summary),
            Root.COLUMN_DOCUMENT_ID to DOC_ROOT
        ))
        result.setNotificationUri(ctx.contentResolver, DocumentsContract.buildRootsUri(AUTHORITY))
        return result
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        val ctx = context ?: throw FileNotFoundException("Provider context unavailable")
        val result = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)

        runBlocking(Dispatchers.IO) {
            when {
                documentId == DOC_ROOT -> includeVirtualFolder(
                    result,
                    DOC_ROOT,
                    ctx.getString(R.string.app_name),
                    ctx.getString(R.string.documents_root_summary)
                )
                documentId == DOC_ALL -> includeVirtualFolder(
                    result,
                    DOC_ALL,
                    ctx.getString(R.string.all_pages),
                    null
                )
                documentId == DOC_FAVORITES -> includeVirtualFolder(
                    result,
                    DOC_FAVORITES,
                    ctx.getString(R.string.favorites_title),
                    null
                )
                documentId == DOC_UNTAGGED -> includeVirtualFolder(
                    result,
                    DOC_UNTAGGED,
                    ctx.getString(R.string.untagged_title),
                    null
                )
                documentId.startsWith(PREFIX_CATEGORY) -> {
                    val catId = documentId.removePrefix(PREFIX_CATEGORY).toLongOrNull()
                        ?: throw FileNotFoundException("Invalid category document ID: $documentId")
                    val category = database.categoryDao().getCategoryById(catId)
                        ?: throw FileNotFoundException("Category not found: $documentId")
                    includeCategoryFolder(result, category)
                }
                documentId.startsWith(PREFIX_PAGE) -> {
                    val pageId = documentId.removePrefix(PREFIX_PAGE).toLongOrNull()
                        ?: throw FileNotFoundException("Invalid page document ID: $documentId")
                    val page = database.pageDao().getPageById(pageId)
                        ?: throw FileNotFoundException("Page not found: $documentId")
                    includePageDocument(result, page)
                }
                else -> throw FileNotFoundException("Unknown document ID: $documentId")
            }
        }

        result.setNotificationUri(
            ctx.contentResolver,
            DocumentsContract.buildDocumentUri(AUTHORITY, documentId)
        )
        return result
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val ctx = context ?: return MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        val result = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)

        runBlocking(Dispatchers.IO) {
            when {
                parentDocumentId == DOC_ROOT -> {
                    val pages = database.pageDao().getAllPages().filter { File(it.imageUrl).exists() }
                    val categories = database.categoryDao().getAllCategories()

                    includeVirtualFolder(
                        result,
                        DOC_ALL,
                        ctx.getString(R.string.all_pages),
                        "${pages.size} pages"
                    )
                    val favCount = pages.count { it.isFavorite }
                    includeVirtualFolder(
                        result,
                        DOC_FAVORITES,
                        ctx.getString(R.string.favorites_title),
                        "$favCount pages"
                    )
                    val untaggedCount = pages.count { it.isUncategorized }
                    includeVirtualFolder(
                        result,
                        DOC_UNTAGGED,
                        ctx.getString(R.string.untagged_title),
                        "$untaggedCount pages"
                    )

                    categories
                        .filter { it.parentId == null }
                        .forEach { category -> includeCategoryFolder(result, category) }
                }
                parentDocumentId == DOC_ALL -> {
                    database.pageDao().getAllPages()
                        .filter { File(it.imageUrl).exists() }
                        .forEach { includePageDocument(result, it) }
                }
                parentDocumentId == DOC_FAVORITES -> {
                    database.pageDao().getAllPages()
                        .filter { it.isFavorite && File(it.imageUrl).exists() }
                        .forEach { includePageDocument(result, it) }
                }
                parentDocumentId == DOC_UNTAGGED -> {
                    database.pageDao().getUncategorizedPages()
                        .filter { File(it.imageUrl).exists() }
                        .forEach { includePageDocument(result, it) }
                }
                parentDocumentId.startsWith(PREFIX_CATEGORY) -> {
                    val catId = parentDocumentId.removePrefix(PREFIX_CATEGORY).toLongOrNull()
                        ?: throw FileNotFoundException("Invalid category document ID: $parentDocumentId")

                    database.categoryDao().getAllCategories()
                        .filter { it.parentId == catId }
                        .forEach { includeCategoryFolder(result, it) }

                    database.pageDao().getAllPages()
                        .filter { it.categoryId == catId && File(it.imageUrl).exists() }
                        .forEach { includePageDocument(result, it) }
                }
                else -> throw FileNotFoundException("Unknown parent document ID: $parentDocumentId")
            }
        }

        result.setNotificationUri(
            ctx.contentResolver,
            DocumentsContract.buildChildDocumentsUri(AUTHORITY, parentDocumentId)
        )
        return result
    }

    override fun queryRecentDocuments(rootId: String?, projection: Array<out String>?): Cursor {
        val ctx = context ?: return MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        val result = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)

        runBlocking(Dispatchers.IO) {
            database.pageDao().getAllPages()
                .asSequence()
                .filter { File(it.imageUrl).exists() }
                .take(MAX_RECENT_RESULTS)
                .forEach { includePageDocument(result, it) }
        }

        result.setNotificationUri(
            ctx.contentResolver,
            DocumentsContract.buildRecentDocumentsUri(AUTHORITY, ROOT_ID)
        )
        return result
    }

    override fun querySearchDocuments(
        rootId: String?,
        query: String?,
        projection: Array<out String>?
    ): Cursor {
        val result = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        val normalized = query.orEmpty().trim().lowercase()

        runBlocking(Dispatchers.IO) {
            val pages = database.pageDao().getAllPages()
            for (page in pages) {
                if (!File(page.imageUrl).exists()) continue
                if (normalized.isEmpty()) {
                    includePageDocument(result, page)
                } else {
                    val matchesTitle = page.title.lowercase().contains(normalized)
                    val matchesTags = page.tags.any { it.lowercase().contains(normalized) }
                    if (matchesTitle || matchesTags) {
                        includePageDocument(result, page)
                    }
                }
            }
        }
        return result
    }

    override fun getDocumentType(documentId: String): String {
        if (documentId == DOC_ROOT ||
            documentId == DOC_ALL ||
            documentId == DOC_FAVORITES ||
            documentId == DOC_UNTAGGED ||
            documentId.startsWith(PREFIX_CATEGORY)
        ) {
            return Document.MIME_TYPE_DIR
        }

        val pageId = documentId.removePrefix(PREFIX_PAGE).toLongOrNull()
            ?: throw FileNotFoundException("Invalid document ID: $documentId")
        val page = runBlocking(Dispatchers.IO) {
            database.pageDao().getPageById(pageId)
        } ?: throw FileNotFoundException("Page not found: $documentId")

        return resolveMimeType(page.imageUrl)
    }

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?
    ): ParcelFileDescriptor {
        signal?.throwIfCanceled()
        if (mode.contains("w", ignoreCase = true)) {
            throw SecurityException("ColorNest documents are read-only.")
        }

        val file = resolveExistingPageFile(documentId)
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun openDocumentThumbnail(
        documentId: String,
        sizeHint: Point?,
        signal: CancellationSignal?
    ): AssetFileDescriptor {
        signal?.throwIfCanceled()
        val ctx = context ?: throw FileNotFoundException("Provider context unavailable")
        val sourceFile = resolveExistingPageFile(documentId)
        val targetWidth = (sizeHint?.x ?: 256).coerceAtLeast(64)
        val targetHeight = (sizeHint?.y ?: 256).coerceAtLeast(64)

        val thumbDir = File(ctx.cacheDir, "saf_thumbs").apply { if (!exists()) mkdirs() }
        val isPng = sourceFile.name.endsWith(".png", ignoreCase = true)
        val ext = if (isPng) "png" else "jpg"
        val thumbFile = File(
            thumbDir,
            "${documentId.replace(':', '_')}_${sourceFile.lastModified()}_${targetWidth}x$targetHeight.$ext"
        )

        if (!thumbFile.exists() || thumbFile.length() == 0L) {
            val generated = createThumbnailFile(sourceFile, thumbFile, targetWidth, targetHeight, isPng)
            if (!generated) {
                val pfd = ParcelFileDescriptor.open(sourceFile, ParcelFileDescriptor.MODE_READ_ONLY)
                return AssetFileDescriptor(pfd, 0, AssetFileDescriptor.UNKNOWN_LENGTH)
            }
        }

        val pfd = ParcelFileDescriptor.open(thumbFile, ParcelFileDescriptor.MODE_READ_ONLY)
        return AssetFileDescriptor(pfd, 0, AssetFileDescriptor.UNKNOWN_LENGTH)
    }

    private fun resolveExistingPageFile(documentId: String): File {
        if (!documentId.startsWith(PREFIX_PAGE)) {
            throw FileNotFoundException("Document is not a file: $documentId")
        }
        val pageId = documentId.removePrefix(PREFIX_PAGE).toLongOrNull()
            ?: throw FileNotFoundException("Invalid page document ID: $documentId")
        val page = runBlocking(Dispatchers.IO) {
            database.pageDao().getPageById(pageId)
        } ?: throw FileNotFoundException("Page not found: $documentId")

        val file = File(page.imageUrl)
        if (!file.exists() || !file.isFile) {
            throw FileNotFoundException("Image file missing for $documentId")
        }
        return file
    }

    private fun createThumbnailFile(
        sourceFile: File,
        thumbFile: File,
        reqWidth: Int,
        reqHeight: Int,
        isPng: Boolean
    ): Boolean {
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(sourceFile.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false

            var inSampleSize = 1
            while ((bounds.outHeight / inSampleSize) > reqHeight * 2 &&
                (bounds.outWidth / inSampleSize) > reqWidth * 2
            ) {
                inSampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
            }
            val decoded = BitmapFactory.decodeFile(sourceFile.absolutePath, decodeOpts) ?: return false
            val maxSide = max(reqWidth, reqHeight)
            val scaled = if (decoded.width > maxSide * 2 || decoded.height > maxSide * 2) {
                val ratio = minOf(
                    (maxSide * 2f) / decoded.width,
                    (maxSide * 2f) / decoded.height
                )
                Bitmap.createScaledBitmap(
                    decoded,
                    (decoded.width * ratio).toInt().coerceAtLeast(1),
                    (decoded.height * ratio).toInt().coerceAtLeast(1),
                    true
                )
            } else {
                decoded
            }

            FileOutputStream(thumbFile).use { out ->
                val format = if (isPng) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                val quality = if (isPng) 100 else 85
                scaled.compress(format, quality, out)
            }
            if (scaled !== decoded) scaled.recycle()
            decoded.recycle()
            true
        }.getOrDefault(false)
    }

    private fun includeVirtualFolder(
        cursor: MatrixCursor,
        docId: String,
        displayName: String,
        summary: String?
    ) {
        addProjectedRow(cursor, listOf(
            Document.COLUMN_DOCUMENT_ID to docId,
            Document.COLUMN_MIME_TYPE to Document.MIME_TYPE_DIR,
            Document.COLUMN_DISPLAY_NAME to displayName,
            Document.COLUMN_LAST_MODIFIED to null,
            Document.COLUMN_FLAGS to 0,
            Document.COLUMN_SIZE to null,
            Document.COLUMN_SUMMARY to summary
        ))
    }

    private fun includeCategoryFolder(cursor: MatrixCursor, category: Category) {
        addProjectedRow(cursor, listOf(
            Document.COLUMN_DOCUMENT_ID to "$PREFIX_CATEGORY${category.id}",
            Document.COLUMN_MIME_TYPE to Document.MIME_TYPE_DIR,
            Document.COLUMN_DISPLAY_NAME to category.name,
            Document.COLUMN_LAST_MODIFIED to null,
            Document.COLUMN_FLAGS to 0,
            Document.COLUMN_SIZE to null,
            Document.COLUMN_SUMMARY to null
        ))
    }

    private fun includePageDocument(cursor: MatrixCursor, page: Page) {
        val file = File(page.imageUrl)
        val mimeType = resolveMimeType(page.imageUrl)
        val ext = File(page.imageUrl).extension.lowercase().ifBlank {
            if (mimeType == "image/png") "png" else "jpg"
        }
        val cleanBase = page.title.ifBlank { "ColorNest_Page_${page.id}" }
            .replace(Regex("[/\\\\:*?\"<>|]+"), "_")
            .trim()
            .ifBlank { "ColorNest_Page_${page.id}" }
        val displayName = if (cleanBase.endsWith(".$ext", ignoreCase = true)) {
            cleanBase
        } else {
            "$cleanBase.$ext"
        }
        val lastModified = if (file.exists() && file.lastModified() > 0L) {
            file.lastModified()
        } else {
            page.createdAt
        }
        val size = if (file.exists()) file.length() else 0L
        val summary = if (page.tags.isNotEmpty()) page.tags.joinToString(", ") else null

        addProjectedRow(cursor, listOf(
            Document.COLUMN_DOCUMENT_ID to "$PREFIX_PAGE${page.id}",
            Document.COLUMN_MIME_TYPE to mimeType,
            Document.COLUMN_DISPLAY_NAME to displayName,
            Document.COLUMN_LAST_MODIFIED to lastModified,
            Document.COLUMN_FLAGS to Document.FLAG_SUPPORTS_THUMBNAIL,
            Document.COLUMN_SIZE to size,
            Document.COLUMN_SUMMARY to summary
        ))
    }

    /**
     * SAF clients may request any subset of columns. MatrixCursor.RowBuilder.add(name, value)
     * throws when a requested projection omits a default column, so only populate
     * columns that the caller actually requested.
     */
    private fun addProjectedRow(cursor: MatrixCursor, values: List<Pair<String, Any?>>) {
        val row = arrayOfNulls<Any?>(cursor.columnCount)
        values.forEach { (column, value) ->
            val index = cursor.getColumnIndex(column)
            if (index >= 0) row[index] = value
        }
        cursor.addRow(row)
    }

    private fun resolveMimeType(path: String): String {
        return when (File(path).extension.lowercase()) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "bmp" -> "image/bmp"
            "heic" -> "image/heic"
            "heif" -> "image/heif"
            "avif" -> "image/avif"
            "jpg", "jpeg", "" -> "image/jpeg"
            else -> "image/jpeg"
        }
    }
}
