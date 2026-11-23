package com.yourname.colornest.data.drive

import android.content.Context
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
import com.yourname.colornest.data.model.Category
import com.yourname.colornest.data.model.Page
import com.yourname.colornest.data.model.Setting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Google Drive sync for ColorNest.
 *
 * Layout in Drive: a top-level "ColorNest" folder holds one folder per
 * category, mirroring the nested category tree
 * (ColorNest/Animals/Mammals/Dogs). Pages without a category live in
 * ColorNest/Untagged. Each page image is uploaded into its category folder.
 *
 * Scopes: DRIVE_FILE (create and manage the files this app uploads) plus
 * DRIVE_READONLY (see images the user drops into the folders themselves —
 * DRIVE_FILE alone cannot see those). See DRIVE_SETUP.md for the
 * Google Cloud Console setup.
 *
 * Deletion rule: sync NEVER permanently deletes anything from Drive.
 * Deleting a page in the app moves its Drive file to the trash, and a page
 * whose Drive file was trashed is removed from the app. Trashed Drive files
 * stay recoverable from the Drive trash for 30 days.
 *
 * The Android app is identified to Google by its package name + signing
 * certificate (see DRIVE_SETUP.md) — no OAuth client ID is embedded here.
 */
data class SyncResult(
    val notConnected: Boolean = false,
    val uploaded: Int = 0,
    val downloaded: Int = 0,
    val moved: Int = 0,
    val renamed: Int = 0,
    val removedLocal: Int = 0
) {
    fun summary(): String {
        if (notConnected) return "Not connected to Google Drive"
        val parts = mutableListOf<String>()
        if (uploaded > 0) parts.add("$uploaded uploaded")
        if (downloaded > 0) parts.add("$downloaded downloaded")
        if (moved > 0) parts.add("$moved moved")
        if (renamed > 0) parts.add("$renamed renamed")
        if (removedLocal > 0) parts.add("$removedLocal removed")
        return if (parts.isEmpty()) "Sync complete — everything is up to date"
        else "Sync complete — " + parts.joinToString(", ")
    }
}

private data class RemoteFile(
    val id: String,
    val name: String,
    val mimeType: String,
    val parentIds: List<String>
)

class DriveSyncManager(
    private val context: Context,
    private val pageDao: PageDao,
    private val categoryDao: CategoryDao,
    private val settingDao: SettingDao
) {
    companion object {
        const val ROOT_FOLDER_NAME = "ColorNest"
        const val UNTAGGED_FOLDER_NAME = "Untagged"
        private const val FOLDER_MIME_TYPE = "application/vnd.google-apps.folder"
        private const val KEY_ACCOUNT = "driveAccountName"
        private const val KEY_SCAN_ON_OPEN = "driveScanOnOpen"
        private const val KEY_LAST_SYNC = "driveLastSync"
        private const val KEY_TOMBSTONES = "driveTombstones"
        private const val MAX_TOMBSTONES = 500
        private val SCOPES = listOf(DriveScopes.DRIVE_FILE, DriveScopes.DRIVE_READONLY)
    }

    private val imagesDir: File by lazy {
        val dir = File(context.filesDir, "colornest")
        if (!dir.exists()) dir.mkdirs()
        dir
    }

    // ---------- Connection state ----------

    suspend fun isConnected(): Boolean = withContext(Dispatchers.IO) {
        !settingDao.getSetting(KEY_ACCOUNT).isNullOrBlank()
    }

    suspend fun getAccountName(): String? = withContext(Dispatchers.IO) {
        settingDao.getSetting(KEY_ACCOUNT)
    }

    suspend fun connect(accountName: String) = withContext(Dispatchers.IO) {
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

    // ---------- Full sync ----------

    /**
     * Runs a full two-way sync. Safe to call from a background coroutine;
     * never throws for "not connected" (returns a [SyncResult] instead).
     * Network/API errors propagate to the caller for status display.
     */
    suspend fun sync(): SyncResult = withContext(Dispatchers.IO) {
        val service = buildService() ?: return@withContext SyncResult(notConnected = true)

        var uploaded = 0
        var downloaded = 0
        var moved = 0
        var renamed = 0
        var removedLocal = 0

        val rootId = getOrCreateFolder(service, ROOT_FOLDER_NAME, "root")
        val untaggedId = getOrCreateFolder(service, UNTAGGED_FOLDER_NAME, rootId)

        // Ensure every category has a folder, mirroring the nested tree.
        val categories = categoryDao.getAllCategories()
        val folderIdByCategory = mutableMapOf<Long, String>()
        for (category in categories) {
            folderIdByCategory[category.id] =
                ensureCategoryFolder(service, rootId, categories, category)
        }

        // One recursive listing of the whole ColorNest tree.
        val remoteById = listTree(service, rootId).associateBy { it.id }
        // ensureCategoryFolder() may have just created these IDs in Room.
        // Build the reverse lookup from the IDs returned in this same sync,
        // not from the stale category objects loaded before folder creation.
        val categoryIdByFolder = folderIdByCategory.entries
            .associate { (categoryId, folderId) -> folderId to categoryId }
        val tombstones = readTombstones()
        val localDriveIds = pageDao.getAllPages()
            .mapNotNull { it.driveFileId }
            .toSet()

        // Upload new pages; move/rename changed ones; drop pages whose
        // Drive file was trashed or deleted.
        val pages = pageDao.getAllPages()
        for (page in pages) {
            val driveId = page.driveFileId
            if (driveId == null) {
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
                // Not in the tree: trashed, deleted, or moved out by the user.
                when (checkRemoteState(service, driveId)) {
                    RemoteState.TRASHED, RemoteState.GONE -> {
                        val localFile = File(page.imageUrl)
                        if (localFile.exists()) localFile.delete()
                        pageDao.deletePage(page)
                        removedLocal++
                    }
                    RemoteState.EXISTS_ELSEWHERE -> {
                        // The user moved it out of the ColorNest tree; leave
                        // the local page alone.
                    }
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

        // Download images the user added to Drive directly.
        for (remote in remoteById.values) {
            if (!remote.mimeType.startsWith("image/")) continue
            if (remote.id in localDriveIds || remote.id in tombstones) continue
            val folderId = remote.parentIds.firstOrNull() ?: continue
            val categoryId = categoryIdByFolder[folderId]
            val isUntagged = folderId == untaggedId || categoryId == null
            val extension = remote.name.substringAfterLast('.', "").ifBlank { "jpg" }
            val localFile = File(
                imagesDir,
                "drive_${remote.id}_${System.currentTimeMillis()}.$extension"
            )
            downloadFile(service, remote.id, localFile)
            pageDao.insertPage(
                Page(
                    title = remote.name.substringBeforeLast('.').ifBlank { "Untitled page" },
                    categoryId = if (isUntagged) null else categoryId,
                    imageUrl = localFile.absolutePath,
                    isUncategorized = isUntagged,
                    driveFileId = remote.id
                )
            )
            downloaded++
        }

        settingDao.setSetting(
            Setting(key = KEY_LAST_SYNC, value = System.currentTimeMillis().toString())
        )
        SyncResult(
            uploaded = uploaded,
            downloaded = downloaded,
            moved = moved,
            renamed = renamed,
            removedLocal = removedLocal
        )
    }

    // ---------- Single-page propagation ----------

    /**
     * Trash the Drive copy of a page being deleted in the app, and record a
     * tombstone so a later scan never re-downloads it. Best-effort: never
     * throws, so a local delete always succeeds even offline.
     */
    suspend fun trashPageFile(page: Page) = withContext(Dispatchers.IO) {
        val driveId = page.driveFileId ?: return@withContext
        addTombstone(driveId)
        try {
            val service = buildService() ?: return@withContext
            service.files().update(driveId, DriveFile().setTrashed(true)).execute()
        } catch (e: Exception) {
            // Offline or already gone — the tombstone covers the re-download
            // case, and the Drive file simply stays until a later sync.
        }
    }

    /**
     * After a local title/category edit, move and/or rename the Drive copy to
     * match. Best-effort: never throws.
     */
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
        } catch (e: Exception) {
            // Offline or the Drive file is gone; the next full sync reconciles.
        }
    }

    /**
     * Move the whole ColorNest folder in Drive to the trash (used by the
     * "Also wipe Cloud Folder" option). Trash only — never a permanent
     * delete. Best-effort: never throws.
     */
    suspend fun trashRootFolder(): Boolean = withContext(Dispatchers.IO) {
        val service = buildService() ?: return@withContext false
        try {
            val rootId = findFolder(service, ROOT_FOLDER_NAME, "root")
                ?: return@withContext true
            service.files().update(rootId, DriveFile().setTrashed(true)).execute()
            true
        } catch (e: Exception) {
            // The caller must not erase local data if cloud deletion failed.
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
        // Walk from the root down to this category, creating folders as needed.
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
                .setFields("nextPageToken, files(id,name,mimeType,parents)")
                .setPageSize(1000)
                .setPageToken(pageToken)
                .execute()
            for (file in result.files ?: emptyList()) {
                out.add(
                    RemoteFile(
                        id = file.id,
                        name = file.name,
                        mimeType = file.mimeType,
                        parentIds = file.parents ?: emptyList()
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
        } catch (e: Exception) {
            // Network blip — do not delete anything on a maybe.
            RemoteState.EXISTS_ELSEWHERE
        }
    }

    // ---------- Tombstones ----------

    private suspend fun readTombstones(): Set<String> {
        val raw = settingDao.getSetting(KEY_TOMBSTONES).orEmpty()
        if (raw.isBlank()) return emptySet()
        return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    private suspend fun addTombstone(driveId: String) {
        val updated = (readTombstones() + driveId).toList().takeLast(MAX_TOMBSTONES)
        settingDao.setSetting(Setting(key = KEY_TOMBSTONES, value = updated.joinToString(",")))
    }

    // ---------- Naming ----------

    private fun sanitizeTitle(title: String): String {
        val cleaned = title.trim()
            .replace(Regex("[\\p{Cntrl}]"), "")
            .take(100)
        return cleaned.ifBlank { "Untitled page" }
    }

    private fun driveFileName(page: Page): String {
        val extension = File(page.imageUrl).extension.ifBlank { "jpg" }
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
