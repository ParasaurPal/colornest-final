package com.yourname.colornest.data.backup

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.yourname.colornest.data.model.Category
import com.yourname.colornest.data.model.Page
import com.yourname.colornest.data.model.TagEntity
import com.yourname.colornest.data.processing.ImageProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class RestoredLibrary(
    val categories: List<Category>,
    val pages: List<Page>,
    val tags: List<TagEntity>
)

object BackupManager {
    private const val BACKUP_FORMAT = "ColorNest backup"
    private const val BACKUP_VERSION = 1

    suspend fun exportToUri(
        context: Context,
        uri: Uri,
        pages: List<Page>,
        categories: List<Category>,
        tags: List<TagEntity>
    ) = withContext(Dispatchers.IO) {
        val isoFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        val categoriesArray = JSONArray().apply {
            categories.forEach { cat ->
                put(JSONObject().apply {
                    put("id", cat.id)
                    put("name", cat.name)
                    if (cat.parentId != null) put("parentId", cat.parentId) else put("parentId", JSONObject.NULL)
                    if (cat.driveFolderId != null) put("driveFolderId", cat.driveFolderId)
                })
            }
        }

        val pagesArray = JSONArray().apply {
            pages.forEach { page ->
                put(JSONObject().apply {
                    put("id", page.id)
                    put("title", page.title)
                    if (page.categoryId != null) put("categoryId", page.categoryId) else put("categoryId", JSONObject.NULL)
                    put("complexity", page.complexity)
                    put("time", page.time)
                    put("tool", page.tool)
                    put("isFavorite", if (page.isFavorite) 1 else 0)
                    put("isCompleted", if (page.isCompleted) 1 else 0)
                    put("progress", page.progress)
                    put("isUncategorized", if (page.isUncategorized) 1 else 0)
                    put("isPreviewOnly", if (page.isPreviewOnly) 1 else 0)
                    if (page.driveFileId != null) put("driveFileId", page.driveFileId)
                    if (page.driveThumbnailLink != null) put("driveThumbnailLink", page.driveThumbnailLink)
                    if (page.syncId != null) put("syncId", page.syncId)
                    put("whiteBackgroundProcessed", page.whiteBackgroundProcessed)
                    put("whiteBackgroundVersion", page.whiteBackgroundVersion)
                    put("createdAt", page.createdAt)
                    put("updatedAt", page.updatedAt)
                    put("tags", JSONArray(page.tags))

                    val file = page.imageUrl.takeIf { it.isNotBlank() }?.let { File(it) }?.takeIf { it.exists() && it.isFile }
                    if (file != null && !page.isPreviewOnly) {
                        val mime = if (file.extension.equals("png", true)) "image/png" else "image/jpeg"
                        val b64 = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
                        put("imageUrl", "data:$mime;base64,$b64")
                    } else {
                        put("imageUrl", JSONObject.NULL)
                    }

                    val thumbFile = page.thumbnailUrl?.takeIf { it.isNotBlank() }?.let { File(it) }?.takeIf { it.exists() && it.isFile }
                    if (thumbFile != null) {
                        val b64Thumb = Base64.encodeToString(thumbFile.readBytes(), Base64.NO_WRAP)
                        put("thumbnailUrl", "data:image/png;base64,$b64Thumb")
                    }
                })
            }
        }

        val tagsArray = JSONArray().apply {
            tags.forEach { tag ->
                put(JSONObject().apply {
                    put("id", tag.id)
                    put("name", tag.name)
                    put("createdAt", tag.createdAt)
                })
            }
        }

        val root = JSONObject().apply {
            put("format", BACKUP_FORMAT)
            put("version", BACKUP_VERSION)
            put("exportedAt", isoFormatter.format(Date()))
            put("pages", pagesArray)
            put("categories", categoriesArray)
            put("tags", tagsArray)
        }

        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(root.toString().toByteArray(Charsets.UTF_8))
        } ?: throw IllegalStateException("Could not open backup destination.")
    }

    suspend fun parseAndExtractFromUri(
        context: Context,
        uri: Uri,
        imagesDir: File,
        thumbsDir: File
    ): RestoredLibrary = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: throw IllegalStateException("Could not read backup file.")

        val root = runCatching { JSONObject(text) }.getOrNull()
            ?: throw IllegalStateException("This is not a valid ColorNest backup file.")

        if (root.optString("format") != BACKUP_FORMAT ||
            !root.has("pages") ||
            !root.has("categories")
        ) {
            throw IllegalStateException("This is not a valid ColorNest backup file.")
        }

        if (!imagesDir.exists()) imagesDir.mkdirs()
        if (!thumbsDir.exists()) thumbsDir.mkdirs()

        val categoriesJson = root.getJSONArray("categories")
        val categories = mutableListOf<Category>()
        for (i in 0 until categoriesJson.length()) {
            val obj = categoriesJson.getJSONObject(i)
            categories.add(
                Category(
                    id = obj.optLong("id", 0L),
                    name = obj.optString("name", "Category"),
                    parentId = if (obj.isNull("parentId")) null else obj.optLong("parentId"),
                    driveFolderId = if (obj.isNull("driveFolderId")) null else obj.optString("driveFolderId").takeIf { it.isNotBlank() }
                )
            )
        }

        val pagesJson = root.getJSONArray("pages")
        val pages = mutableListOf<Page>()
        for (i in 0 until pagesJson.length()) {
            val obj = pagesJson.getJSONObject(i)
            val tagsList = mutableListOf<String>()
            val tagsArr = obj.optJSONArray("tags")
            if (tagsArr != null) {
                for (j in 0 until tagsArr.length()) {
                    val t = tagsArr.optString(j, "").trim()
                    if (t.isNotEmpty()) tagsList.add(t)
                }
            }

            val rawImageUrl = if (obj.isNull("imageUrl")) "" else obj.optString("imageUrl", "")
            val rawThumbUrl = if (obj.isNull("thumbnailUrl")) null else obj.optString("thumbnailUrl", "").takeIf { it.isNotBlank() }

            val savedImagePath = decodeDataUrlToFile(rawImageUrl, imagesDir, "backup_${i}") ?: ""
            var savedThumbPath = rawThumbUrl?.let { decodeDataUrlToFile(it, thumbsDir, "backup_thumb_${i}") }
            if (savedThumbPath == null && savedImagePath.isNotBlank()) {
                savedThumbPath = ImageProcessor.tryCreateThumbnailFromFile(File(savedImagePath), thumbsDir, "backup_$i")
            }

            val isPreview = obj.optInt("isPreviewOnly", 0) == 1 || obj.optBoolean("isPreviewOnly", false)
            val isFav = obj.optInt("isFavorite", 0) == 1 || obj.optBoolean("isFavorite", false)
            val isComp = obj.optInt("isCompleted", 0) == 1 || obj.optBoolean("isCompleted", false)
            val isUncat = if (obj.has("isUncategorized")) {
                obj.optInt("isUncategorized", 0) == 1 || obj.optBoolean("isUncategorized", false)
            } else {
                obj.isNull("categoryId")
            }

            pages.add(
                Page(
                    id = obj.optLong("id", 0L),
                    title = obj.optString("title", "Untitled page"),
                    categoryId = if (obj.isNull("categoryId")) null else obj.optLong("categoryId"),
                    complexity = obj.optString("complexity", "Beginner"),
                    time = obj.optString("time", "TBD"),
                    tool = obj.optString("tool", "TBD"),
                    imageUrl = savedImagePath,
                    thumbnailUrl = savedThumbPath,
                    isPreviewOnly = isPreview,
                    driveThumbnailLink = if (obj.isNull("driveThumbnailLink")) null else obj.optString("driveThumbnailLink").takeIf { it.isNotBlank() },
                    isFavorite = isFav,
                    isCompleted = isComp,
                    progress = obj.optInt("progress", 0),
                    isUncategorized = isUncat,
                    tags = tagsList.distinct(),
                    driveFileId = if (obj.isNull("driveFileId")) null else obj.optString("driveFileId").takeIf { it.isNotBlank() },
                    whiteBackgroundProcessed = obj.optBoolean("whiteBackgroundProcessed", false),
                    whiteBackgroundVersion = obj.optInt("whiteBackgroundVersion", 0),
                    syncId = if (obj.isNull("syncId")) null else obj.optString("syncId").takeIf { it.isNotBlank() },
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                )
            )
        }

        val tags = mutableListOf<TagEntity>()
        val tagsJson = root.optJSONArray("tags")
        if (tagsJson != null) {
            for (i in 0 until tagsJson.length()) {
                val obj = tagsJson.optJSONObject(i) ?: continue
                val name = obj.optString("name", "").trim()
                if (name.isNotEmpty()) {
                    tags.add(
                        TagEntity(
                            name = name,
                            createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                        )
                    )
                }
            }
        }
        // Also ensure every page tag exists in tags table
        val seenNames = tags.map { it.name.lowercase() }.toMutableSet()
        pages.flatMap { it.tags }.forEach { tagName ->
            if (seenNames.add(tagName.lowercase())) {
                tags.add(TagEntity(name = tagName))
            }
        }

        RestoredLibrary(categories = categories, pages = pages, tags = tags)
    }

    private fun decodeDataUrlToFile(dataUrl: String, dir: File, prefix: String): String? {
        if (!dataUrl.startsWith("data:")) return null
        val commaIndex = dataUrl.indexOf(',')
        if (commaIndex <= 0) return null
        val header = dataUrl.substring(5, commaIndex)
        val base64Part = dataUrl.substring(commaIndex + 1)
        val ext = when {
            header.contains("png", ignoreCase = true) -> "png"
            header.contains("webp", ignoreCase = true) -> "webp"
            else -> "jpg"
        }
        return runCatching {
            val bytes = Base64.decode(base64Part, Base64.DEFAULT)
            val file = File(dir, "${prefix}_${System.currentTimeMillis()}_${(100..999).random()}.$ext")
            FileOutputStream(file).use { it.write(bytes) }
            file.absolutePath
        }.getOrNull()
    }
}
