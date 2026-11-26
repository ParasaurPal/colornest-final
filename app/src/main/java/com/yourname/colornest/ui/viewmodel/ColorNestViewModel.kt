package com.yourname.colornest.ui.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yourname.colornest.data.drive.DriveBrowserEntry
import com.yourname.colornest.data.drive.ScanProgress
import com.yourname.colornest.data.model.Category
import com.yourname.colornest.data.model.Page
import com.yourname.colornest.data.model.TagEntity
import com.yourname.colornest.data.processing.DuplicateGroup
import com.yourname.colornest.data.processing.ImageProcessor
import com.yourname.colornest.data.repository.ColorNestRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

enum class TagMatchMode { ALL, ANY }

enum class SortOption(val label: String) {
    RECENT("Recently updated"),
    OLDEST("Oldest updated"),
    TITLE_ASC("Title A–Z"),
    TITLE_DESC("Title Z–A"),
    FAVORITES("Favorites first")
}

data class PendingAiSuggestion(
    val page: Page,
    val tags: List<String>,
    val categoryId: Long,
    val categoryPath: String,
    val reason: String
)

data class ViewerUiState(
    val page: Page? = null,
    val tempPreviewBytes: ByteArray? = null,
    val isTempPreviewLoading: Boolean = false,
    val isDownloadingPreview: Boolean = false,
    val downloadStatus: String = "",
    val downloadedPdfPages: Int? = null
)

data class DriveBrowserUiState(
    val isOpen: Boolean = false,
    val isLoading: Boolean = false,
    val notConnected: Boolean = false,
    val error: String? = null,
    val files: List<DriveBrowserEntry> = emptyList(),
    val selectedEntry: DriveBrowserEntry? = null,
    val downloadingId: String? = null,
    val status: String = ""
)

enum class DuplicatesStage { SCANNING, READY, DELETING, DONE }

data class DuplicatesUiState(
    val isOpen: Boolean = false,
    val stage: DuplicatesStage = DuplicatesStage.SCANNING,
    val groups: List<DuplicateGroup> = emptyList(),
    val keepByHash: Map<String, Long> = emptyMap(),
    val scanDone: Int = 0,
    val scanTotal: Int = 0,
    val deleteDone: Int = 0,
    val deleteTotal: Int = 0,
    val deletedCount: Int = 0
)

data class SettingsExtraUiState(
    val galleryFullImages: Boolean = false,
    val storageUsedBytes: Long = 0L,
    val unprocessedImageCount: Int = 0,
    val isProcessingLibrary: Boolean = false,
    val libraryProcessDone: Int = 0,
    val libraryProcessTotal: Int = 0,
    val libraryProcessStatus: String = "",
    val backupBusy: Boolean = false,
    val backupStatus: String = "",
    val driveConnected: Boolean = false,
    val driveAccount: String? = null,
    val driveLastSync: Long? = null,
    val driveScanOnOpen: Boolean = true,
    val isSyncingDrive: Boolean = false,
    val driveStatus: String = "",
    val aiAutoTagUploads: Boolean = false,
    val isAiTagging: Boolean = false,
    val aiTagDone: Int = 0,
    val aiTagTotal: Int = 0,
    val aiTagStatus: String = "",
    val aiTagIsError: Boolean = false,
    val pendingAiSuggestions: List<PendingAiSuggestion>? = null
)

data class UiState(
    val pages: List<Page> = emptyList(),
    val filteredPages: List<Page> = emptyList(),
    val categories: List<Category> = emptyList(),
    val activeCategoryId: Long? = null,
    val availableTags: List<String> = emptyList(),
    val tagCounts: List<Pair<String, Int>> = emptyList(),
    val selectedTag: String? = null,
    val activeTags: Set<String> = emptySet(),
    val tagMatchMode: TagMatchMode = TagMatchMode.ALL,
    val showNoTagsOnly: Boolean = false,
    val sortBy: SortOption = SortOption.RECENT,
    val searchQuery: String = "",
    val isDarkMode: Boolean = false,
    val showTutorial: Boolean = false,
    val isAddModalOpen: Boolean = false,
    val isMenuOpen: Boolean = false,
    val isSettingsOpen: Boolean = false,
    val categorizingPage: Page? = null,
    val isProcessingAdd: Boolean = false,
    val addProgressText: String = "",
    val addProgressPercent: Int = 0,
    val isSeeding: Boolean = false,
    val wipeStep: Int = 0,
    val deleteCloud: Boolean = false,
    val confirmDeleteText: String = "",
    val wipeError: String? = null,
    val galleryFullImages: Boolean = false,
    val scanProgress: ScanProgress? = null,
    val importStatus: String = ""
)

private data class FilterState(
    val activeCategoryId: Long? = null,
    val activeTags: Set<String> = emptySet(),
    val tagMatchMode: TagMatchMode = TagMatchMode.ALL,
    val showNoTagsOnly: Boolean = false,
    val sortBy: SortOption = SortOption.RECENT,
    val searchQuery: String = ""
)

private data class ModalState(
    val isDarkMode: Boolean = false,
    val showTutorial: Boolean = false,
    val isAddModalOpen: Boolean = false,
    val isMenuOpen: Boolean = false,
    val isSettingsOpen: Boolean = false,
    val categorizingPage: Page? = null,
    val isProcessingAdd: Boolean = false,
    val addProgressText: String = "",
    val addProgressPercent: Int = 0,
    val isSeeding: Boolean = false,
    val wipeStep: Int = 0,
    val deleteCloud: Boolean = false,
    val confirmDeleteText: String = "",
    val wipeError: String? = null,
    val importStatus: String = ""
)

class ColorNestViewModel(application: Application) : AndroidViewModel(application) {
    val repository = ColorNestRepository(application)

    private val _filterState = MutableStateFlow(FilterState())
    private val _modalState = MutableStateFlow(ModalState())
    private val _settingsExtra = MutableStateFlow(SettingsExtraUiState())
    val settingsExtra: StateFlow<SettingsExtraUiState> = _settingsExtra.asStateFlow()

    private val _viewerState = MutableStateFlow(ViewerUiState())
    val viewerState: StateFlow<ViewerUiState> = _viewerState.asStateFlow()

    private val _driveBrowserState = MutableStateFlow(DriveBrowserUiState())
    val driveBrowserState: StateFlow<DriveBrowserUiState> = _driveBrowserState.asStateFlow()

    private val _duplicatesState = MutableStateFlow(DuplicatesUiState())
    val duplicatesState: StateFlow<DuplicatesUiState> = _duplicatesState.asStateFlow()

    val pagesFlow = repository.getAllPagesFlow()
    @OptIn(ExperimentalCoroutinesApi::class)
    val searchedPagesFlow = _filterState
        .map { it.searchQuery.trim() }
        .distinctUntilChanged()
        .flatMapLatest { query -> repository.searchPagesFlow(query) }
    val categoriesFlow = repository.getAllCategoriesFlow()
    val tagsFlow = repository.getAllTagsFlow()
    val scanProgressFlow = repository.scanProgress

    init {
        viewModelScope.launch {
            val seen = repository.getSetting("hasSeenTutorial")
            if (seen == null || seen != "true") {
                _modalState.update { it.copy(showTutorial = true) }
            }
            val theme = repository.getSetting("theme")
            if (theme == "dark") {
                _modalState.update { it.copy(isDarkMode = true) }
            }
            val fullImages = repository.getSetting("galleryFullImages") == "true"
            val autoTag = repository.getSetting("aiAutoTagUploads") == "true"
            _settingsExtra.update {
                it.copy(
                    galleryFullImages = fullImages,
                    aiAutoTagUploads = autoTag
                )
            }
            refreshSettingsMetadata()

            // Non-blocking thumbnail backfill for pages saved before thumbnails existed
            launch { repository.backfillThumbnails() }

            // Automatic Drive preview import on launch when enabled
            launch {
                if (repository.isDriveConnected() && repository.isDriveScanOnOpen()) {
                    runCatching { repository.syncDrivePreviews() }
                    refreshDriveSettings()
                }
            }
        }
    }

    private data class CatalogSnapshot(
        val allPages: List<Page>,
        val searchedPages: List<Page>,
        val categories: List<Category>,
        val savedTags: List<TagEntity>
    )

    val uiState: StateFlow<UiState> = combine(
        combine(pagesFlow, searchedPagesFlow, categoriesFlow, tagsFlow) { allPages, searchedPages, categories, savedTags ->
            CatalogSnapshot(allPages, searchedPages, categories, savedTags)
        },
        _filterState,
        _modalState,
        _settingsExtra,
        scanProgressFlow
    ) { catalog, filter, modal, settings, scanProg ->
        val pages = catalog.allPages
        val categories = catalog.categories
        val savedTags = catalog.savedTags
        val countsMap = linkedMapOf<String, Int>()
        savedTags.forEach { tag ->
            val trimmed = tag.name.trim()
            if (trimmed.isNotEmpty() && !countsMap.containsKey(trimmed)) {
                countsMap[trimmed] = 0
            }
        }
        pages.forEach { page ->
            page.tags.forEach { raw ->
                val trimmed = raw.trim()
                if (trimmed.isNotEmpty()) {
                    countsMap[trimmed] = (countsMap[trimmed] ?: 0) + 1
                }
            }
        }
        val tagCounts = countsMap.entries
            .map { it.key to it.value }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.first })

        val availableTags = tagCounts.map { it.first }
        val activeTagsNormalized = filter.activeTags.filter { active ->
            availableTags.any { it.equals(active, ignoreCase = true) }
        }.toSet()

        val filtered = filterAndSortPages(
            pages = catalog.searchedPages,
            categories = categories,
            activeCatId = filter.activeCategoryId,
            activeTags = activeTagsNormalized,
            tagMatchMode = filter.tagMatchMode,
            showNoTagsOnly = filter.showNoTagsOnly,
            sortBy = filter.sortBy
        )

        UiState(
            pages = pages,
            filteredPages = filtered,
            categories = categories,
            activeCategoryId = filter.activeCategoryId,
            availableTags = availableTags,
            tagCounts = tagCounts,
            selectedTag = activeTagsNormalized.firstOrNull(),
            activeTags = activeTagsNormalized,
            tagMatchMode = filter.tagMatchMode,
            showNoTagsOnly = filter.showNoTagsOnly,
            sortBy = filter.sortBy,
            searchQuery = filter.searchQuery,
            isDarkMode = modal.isDarkMode,
            showTutorial = modal.showTutorial,
            isAddModalOpen = modal.isAddModalOpen,
            isMenuOpen = modal.isMenuOpen,
            isSettingsOpen = modal.isSettingsOpen,
            categorizingPage = modal.categorizingPage,
            isProcessingAdd = modal.isProcessingAdd,
            addProgressText = modal.addProgressText,
            addProgressPercent = modal.addProgressPercent,
            isSeeding = modal.isSeeding,
            wipeStep = modal.wipeStep,
            deleteCloud = modal.deleteCloud,
            confirmDeleteText = modal.confirmDeleteText,
            wipeError = modal.wipeError,
            galleryFullImages = settings.galleryFullImages,
            scanProgress = scanProg,
            importStatus = modal.importStatus
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        UiState()
    )

    private fun getCategoryAndChildren(catId: Long, allCats: List<Category>): List<Long> {
        val result = mutableListOf(catId)
        val children = allCats.filter { it.parentId == catId }
        for (child in children) {
            result.addAll(getCategoryAndChildren(child.id, allCats))
        }
        return result
    }

    private fun filterAndSortPages(
        pages: List<Page>,
        categories: List<Category>,
        activeCatId: Long?,
        activeTags: Set<String>,
        tagMatchMode: TagMatchMode,
        showNoTagsOnly: Boolean,
        sortBy: SortOption
    ): List<Page> {
        val filtered = pages.filter { page ->
            if (activeCatId == -1L) {
                if (!page.isUncategorized) return@filter false
            } else if (activeCatId != null) {
                val validIds = getCategoryAndChildren(activeCatId, categories)
                if (page.categoryId !in validIds) return@filter false
            }
            if (activeTags.isNotEmpty()) {
                val matches = if (tagMatchMode == TagMatchMode.ALL) {
                    activeTags.all { selected ->
                        page.tags.any { it.trim().equals(selected, ignoreCase = true) }
                    }
                } else {
                    activeTags.any { selected ->
                        page.tags.any { it.trim().equals(selected, ignoreCase = true) }
                    }
                }
                if (!matches) return@filter false
            }
            if (showNoTagsOnly && page.tags.isNotEmpty()) {
                return@filter false
            }
            true
        }

        return when (sortBy) {
            SortOption.RECENT -> filtered.sortedByDescending { it.updatedAt }
            SortOption.OLDEST -> filtered.sortedBy { it.updatedAt }
            SortOption.TITLE_ASC -> filtered.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
            SortOption.TITLE_DESC -> filtered.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.title })
            SortOption.FAVORITES -> filtered.sortedWith(
                compareByDescending<Page> { it.isFavorite }.thenByDescending { it.updatedAt }
            )
        }
    }

    // ---------- Filter & Sort Actions ----------

    fun setSearchQuery(q: String) {
        _filterState.update { it.copy(searchQuery = q) }
    }

    fun setActiveCategory(id: Long?) {
        _filterState.update { it.copy(activeCategoryId = id) }
    }

    fun selectTag(tag: String?) {
        _filterState.update { state ->
            if (tag == null) {
                state.copy(activeTags = emptySet())
            } else {
                val existing = state.activeTags.firstOrNull { it.equals(tag, ignoreCase = true) }
                val next = if (existing != null) state.activeTags - existing else setOf(tag)
                state.copy(activeTags = next, showNoTagsOnly = false)
            }
        }
    }

    fun toggleTagFilter(tag: String) {
        _filterState.update { state ->
            val existing = state.activeTags.firstOrNull { it.equals(tag, ignoreCase = true) }
            val next = if (existing != null) state.activeTags - existing else state.activeTags + tag
            state.copy(activeTags = next, showNoTagsOnly = false)
        }
    }

    fun clearTagFilters() {
        _filterState.update { it.copy(activeTags = emptySet()) }
    }

    fun setTagMatchMode(mode: TagMatchMode) {
        _filterState.update { it.copy(tagMatchMode = mode) }
    }

    fun toggleNoTagsOnly() {
        _filterState.update {
            val next = !it.showNoTagsOnly
            it.copy(
                showNoTagsOnly = next,
                activeTags = if (next) emptySet() else it.activeTags
            )
        }
    }

    fun clearAllFilters() {
        _filterState.update {
            it.copy(
                activeCategoryId = null,
                activeTags = emptySet(),
                showNoTagsOnly = false,
                searchQuery = ""
            )
        }
    }

    fun setSortOption(option: SortOption) {
        _filterState.update { it.copy(sortBy = option) }
    }

    // ---------- Tag CRUD ----------

    fun createTag(name: String, onDuplicate: () -> Unit = {}) {
        viewModelScope.launch {
            val created = repository.createTag(name)
            if (!created) onDuplicate()
        }
    }

    fun renameTag(oldName: String, newName: String) {
        viewModelScope.launch {
            repository.renameTag(oldName, newName)
            _filterState.update { state ->
                val updated = state.activeTags.map {
                    if (it.equals(oldName, ignoreCase = true)) newName.trim() else it
                }.toSet()
                state.copy(activeTags = updated)
            }
        }
    }

    fun deleteTag(name: String) {
        viewModelScope.launch {
            repository.deleteTag(name)
            _filterState.update { state ->
                state.copy(activeTags = state.activeTags.filterNot { it.equals(name, ignoreCase = true) }.toSet())
            }
        }
    }

    fun clearAllTags() {
        viewModelScope.launch {
            repository.clearAllTags()
            _filterState.update { it.copy(activeTags = emptySet()) }
        }
    }

    // ---------- Modals & Theme ----------

    fun setMenuOpen(open: Boolean) { _modalState.update { it.copy(isMenuOpen = open) } }
    fun setAddModalOpen(open: Boolean) { _modalState.update { it.copy(isAddModalOpen = open) } }
    fun setSettingsOpen(open: Boolean) {
        _modalState.update { it.copy(isSettingsOpen = open) }
        if (open) {
            viewModelScope.launch { refreshSettingsMetadata() }
        }
    }
    fun setCategorizingPage(page: Page?) { _modalState.update { it.copy(categorizingPage = page) } }

    fun toggleTheme() {
        val newTheme = !_modalState.value.isDarkMode
        _modalState.update { it.copy(isDarkMode = newTheme) }
        viewModelScope.launch {
            repository.setSetting("theme", if (newTheme) "dark" else "light")
        }
    }

    fun completeTutorial() {
        _modalState.update { it.copy(showTutorial = false) }
        viewModelScope.launch {
            repository.setSetting("hasSeenTutorial", "true")
        }
    }

    // ---------- Page Actions ----------

    fun toggleFavorite(pageId: Long) {
        viewModelScope.launch { repository.toggleFavorite(pageId) }
    }

    fun deletePage(page: Page) {
        viewModelScope.launch { repository.deletePage(page) }
    }

    fun addCategory(name: String, parentId: Long?) {
        viewModelScope.launch { repository.insertCategory(name, parentId) }
    }

    fun savePageDetails(
        pageId: Long,
        title: String,
        categoryId: Long?,
        tags: List<String>,
        isCompleted: Boolean? = null
    ) {
        viewModelScope.launch {
            repository.updatePageDetails(pageId, title, categoryId, tags, isCompleted)
            _modalState.update { it.copy(categorizingPage = null) }
        }
    }

    // ---------- In-app Fullscreen Page Viewer ----------

    fun openPageViewer(page: Page) {
        _viewerState.value = ViewerUiState(
            page = page,
            isTempPreviewLoading = page.isPreviewOnly
        )
        if (page.isPreviewOnly) {
            viewModelScope.launch {
                val bytes = repository.fetchTempPreviewBytes(page)
                _viewerState.update { current ->
                    if (current.page?.id == page.id) {
                        current.copy(tempPreviewBytes = bytes, isTempPreviewLoading = false)
                    } else current
                }
            }
        }
    }

    fun closePageViewer() {
        _viewerState.value = ViewerUiState()
    }

    fun downloadViewingPreviewPage() {
        val page = _viewerState.value.page ?: return
        if (_viewerState.value.isDownloadingPreview) return
        viewModelScope.launch {
            _viewerState.update {
                it.copy(isDownloadingPreview = true, downloadStatus = "Downloading…")
            }
            try {
                val (added, isPdf) = repository.downloadPreviewPage(page)
                if (isPdf) {
                    _viewerState.update {
                        it.copy(
                            isDownloadingPreview = false,
                            downloadStatus = "",
                            downloadedPdfPages = added
                        )
                    }
                } else {
                    val fresh = repository.pageDao.getPageById(page.id)
                    _viewerState.update {
                        it.copy(
                            page = fresh ?: it.page,
                            isDownloadingPreview = false,
                            downloadStatus = "Downloaded — this page is now saved on this device."
                        )
                    }
                }
            } catch (e: Exception) {
                _viewerState.update {
                    it.copy(
                        isDownloadingPreview = false,
                        downloadStatus = "Download failed: ${e.message ?: "Unknown error"}"
                    )
                }
            }
        }
    }

    // ---------- Settings: Categories, Gallery, Storage, Line-art, Backups ----------

    fun seedMassiveCategories() {
        viewModelScope.launch {
            _modalState.update { it.copy(isSeeding = true) }
            repository.seedMassiveCategories()
            _modalState.update { it.copy(isSeeding = false, isSettingsOpen = false) }
        }
    }

    fun toggleGalleryFullImages(enabled: Boolean) {
        _settingsExtra.update { it.copy(galleryFullImages = enabled) }
        viewModelScope.launch {
            repository.setSetting("galleryFullImages", enabled.toString())
        }
    }

    private suspend fun refreshSettingsMetadata() {
        val pages = repository.pageDao.getAllPages()
        val unprocessed = pages.count { ImageProcessor.needsWhiteBackgroundReprocessing(it) }
        val bytes = repository.getStorageUsedBytes()
        _settingsExtra.update {
            it.copy(storageUsedBytes = bytes, unprocessedImageCount = unprocessed)
        }
        refreshDriveSettings()
    }

    fun refreshDriveSettings() {
        viewModelScope.launch {
            val connected = repository.isDriveConnected()
            val account = repository.getDriveAccountName()
            val lastSync = repository.getLastDriveSync()
            val scanOnOpen = repository.isDriveScanOnOpen()
            _settingsExtra.update {
                it.copy(
                    driveConnected = connected,
                    driveAccount = account,
                    driveLastSync = lastSync,
                    driveScanOnOpen = scanOnOpen
                )
            }
        }
    }

    fun processExistingLibrary() {
        if (_settingsExtra.value.isProcessingLibrary) return
        viewModelScope.launch {
            _settingsExtra.update {
                it.copy(
                    isProcessingLibrary = true,
                    libraryProcessDone = 0,
                    libraryProcessTotal = it.unprocessedImageCount,
                    libraryProcessStatus = "Preparing your library for black-ink conversion…"
                )
            }
            try {
                val (processed, failed) = repository.processExistingLibrary { done, total, title ->
                    _settingsExtra.update {
                        it.copy(
                            libraryProcessDone = done,
                            libraryProcessTotal = total,
                            libraryProcessStatus = if (done < total) {
                                "Converting to black ink ${done + 1} of $total: $title"
                            } else {
                                "Finishing…"
                            }
                        )
                    }
                }
                val remaining = repository.pageDao.getAllPages()
                    .count { ImageProcessor.needsWhiteBackgroundReprocessing(it) }
                val bytes = repository.getStorageUsedBytes()
                _settingsExtra.update {
                    it.copy(
                        isProcessingLibrary = false,
                        unprocessedImageCount = remaining,
                        storageUsedBytes = bytes,
                        libraryProcessStatus = "Finished: $processed processed, $failed failed." +
                            if (remaining > 0) " $remaining pages remain and can be retried." else ""
                    )
                }
            } catch (e: Exception) {
                _settingsExtra.update {
                    it.copy(
                        isProcessingLibrary = false,
                        libraryProcessStatus = "Could not process library: ${e.message ?: "Unknown error"}"
                    )
                }
            }
        }
    }

    fun exportBackup(uri: Uri) {
        if (_settingsExtra.value.backupBusy) return
        viewModelScope.launch {
            _settingsExtra.update { it.copy(backupBusy = true, backupStatus = "Exporting backup…") }
            try {
                repository.exportBackup(uri)
                _settingsExtra.update {
                    it.copy(backupBusy = false, backupStatus = "Backup exported successfully.")
                }
            } catch (e: Exception) {
                _settingsExtra.update {
                    it.copy(backupBusy = false, backupStatus = "Backup export failed: ${e.message ?: "Unknown error"}")
                }
            }
        }
    }

    fun importBackup(uri: Uri) {
        if (_settingsExtra.value.backupBusy) return
        viewModelScope.launch {
            _settingsExtra.update { it.copy(backupBusy = true, backupStatus = "Restoring backup…") }
            try {
                repository.importBackup(uri)
                refreshSettingsMetadata()
                _settingsExtra.update {
                    it.copy(backupBusy = false, backupStatus = "Backup restored. Your library is ready on this device.")
                }
            } catch (e: Exception) {
                _settingsExtra.update {
                    it.copy(backupBusy = false, backupStatus = "Backup restore failed: ${e.message ?: "Unknown error"}")
                }
            }
        }
    }

    // ---------- Google Drive Actions ----------

    fun syncDriveNow() {
        if (_settingsExtra.value.isSyncingDrive) return
        viewModelScope.launch {
            _settingsExtra.update { it.copy(isSyncingDrive = true, driveStatus = "Checking for new Drive files…") }
            try {
                val result = repository.syncDrivePreviews()
                refreshSettingsMetadata()
                _settingsExtra.update {
                    it.copy(isSyncingDrive = false, driveStatus = result.summary())
                }
            } catch (e: Exception) {
                _settingsExtra.update {
                    it.copy(isSyncingDrive = false, driveStatus = "Sync failed: ${e.message?.take(120) ?: "Unknown error"}")
                }
            }
        }
    }

    fun disconnectDrive() {
        viewModelScope.launch {
            repository.disconnectDrive()
            refreshDriveSettings()
            _settingsExtra.update { it.copy(driveStatus = "Disconnected from Google Drive") }
        }
    }

    fun setDriveScanOnOpen(enabled: Boolean) {
        _settingsExtra.update { it.copy(driveScanOnOpen = enabled) }
        viewModelScope.launch { repository.setDriveScanOnOpen(enabled) }
    }

    fun openDriveBrowser() {
        _driveBrowserState.value = DriveBrowserUiState(isOpen = true, isLoading = true)
        loadDriveBrowserFiles()
    }

    fun closeDriveBrowser() {
        _driveBrowserState.value = DriveBrowserUiState()
    }

    fun selectDriveBrowserEntry(entry: DriveBrowserEntry?) {
        _driveBrowserState.update { it.copy(selectedEntry = entry, status = "") }
    }

    fun loadDriveBrowserFiles() {
        viewModelScope.launch {
            _driveBrowserState.update { it.copy(isLoading = true, error = null, status = "") }
            try {
                val (connected, files) = repository.listDriveFiles()
                _driveBrowserState.update {
                    it.copy(
                        isLoading = false,
                        notConnected = !connected,
                        files = files
                    )
                }
            } catch (e: Exception) {
                _driveBrowserState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Could not list Drive files."
                    )
                }
            }
        }
    }

    fun downloadDriveBrowserEntry(entry: DriveBrowserEntry) {
        if (_driveBrowserState.value.downloadingId != null) return
        viewModelScope.launch {
            _driveBrowserState.update { it.copy(downloadingId = entry.id, status = "") }
            try {
                val added = repository.downloadDriveEntry(entry)
                val markSaved: (DriveBrowserEntry) -> DriveBrowserEntry = { item ->
                    if (item.id == entry.id) {
                        item.copy(
                            inLibrary = true,
                            libraryPreviewOnly = false,
                            importedPdf = if (item.isPdf) true else item.importedPdf
                        )
                    } else item
                }
                _driveBrowserState.update { state ->
                    state.copy(
                        downloadingId = null,
                        files = state.files.map(markSaved),
                        selectedEntry = state.selectedEntry?.let(markSaved),
                        status = if (added == 1) {
                            "Downloaded \"${entry.name}\"."
                        } else {
                            "Downloaded \"${entry.name}\" — $added pages added."
                        }
                    )
                }
                refreshSettingsMetadata()
            } catch (e: Exception) {
                _driveBrowserState.update {
                    it.copy(
                        downloadingId = null,
                        status = "Download failed: ${e.message ?: "Unknown error"}"
                    )
                }
            }
        }
    }

    // ---------- Duplicates Finder ----------

    fun openDuplicatesFinder() {
        _duplicatesState.value = DuplicatesUiState(isOpen = true, stage = DuplicatesStage.SCANNING)
        viewModelScope.launch {
            val groups = repository.findDuplicateGroups { done, total ->
                _duplicatesState.update { it.copy(scanDone = done, scanTotal = total) }
            }
            _duplicatesState.update {
                it.copy(
                    stage = DuplicatesStage.READY,
                    groups = groups,
                    keepByHash = groups.associate { g -> g.hash to g.pages.first().id }
                )
            }
        }
    }

    fun closeDuplicatesFinder() {
        _duplicatesState.value = DuplicatesUiState()
    }

    fun selectDuplicateToKeep(hash: String, pageId: Long) {
        _duplicatesState.update { state ->
            state.copy(keepByHash = state.keepByHash + (hash to pageId))
        }
    }

    fun deleteSelectedDuplicates() {
        val state = _duplicatesState.value
        val toDelete = mutableListOf<Page>()
        for (group in state.groups) {
            val keepId = state.keepByHash[group.hash]
            group.pages.filter { it.id != keepId }.forEach { toDelete.add(it) }
        }
        if (toDelete.isEmpty()) return

        viewModelScope.launch {
            _duplicatesState.update {
                it.copy(stage = DuplicatesStage.DELETING, deleteDone = 0, deleteTotal = toDelete.size)
            }
            val deleted = repository.deleteDuplicatePages(toDelete) { done, total ->
                _duplicatesState.update { it.copy(deleteDone = done, deleteTotal = total) }
            }
            refreshSettingsMetadata()
            _duplicatesState.update {
                it.copy(stage = DuplicatesStage.DONE, deletedCount = deleted)
            }
        }
    }

    // ---------- AI Subject Tagging ----------

    fun toggleAiAutoTag(enabled: Boolean) {
        _settingsExtra.update { it.copy(aiAutoTagUploads = enabled) }
        viewModelScope.launch {
            repository.setSetting("aiAutoTagUploads", enabled.toString())
        }
    }

    private fun buildCategoryPath(categoryId: Long, categories: List<Category>): String {
        val byId = categories.associateBy { it.id }
        val names = mutableListOf<String>()
        var current = byId[categoryId]
        while (current != null) {
            names.add(0, current.name)
            current = current.parentId?.let { byId[it] }
        }
        return names.joinToString(" / ").ifBlank { "Category" }
    }

    fun scanLibraryWithAi() {
        if (_settingsExtra.value.isAiTagging) return

        viewModelScope.launch {
            val allCategories = repository.categoryDao.getAllCategories()
            if (allCategories.isEmpty()) {
                _settingsExtra.update {
                    it.copy(
                        aiTagIsError = true,
                        aiTagStatus = "Load your default category tree before running AI sorting."
                    )
                }
                return@launch
            }
            val learning = runCatching { org.json.JSONArray(repository.getSetting("aiCategoryLearning").orEmpty()) }
                .getOrNull()?.let { arr -> (0 until arr.length()).map { arr.optString(it) } }.orEmpty()
            val pending = repository.pageDao.getAllPages().filter { page ->
                val hasImage = (page.imageUrl.isNotBlank() && File(page.imageUrl).exists()) ||
                    (!page.thumbnailUrl.isNullOrBlank() && File(page.thumbnailUrl).exists())
                hasImage && (page.aiScannedAt == null || page.categoryId == null)
            }
            if (pending.isEmpty()) {
                _settingsExtra.update {
                    it.copy(
                        aiTagIsError = false,
                        aiTagStatus = "All locally available images have already been AI-scanned."
                    )
                }
                return@launch
            }

            _settingsExtra.update {
                it.copy(
                    isAiTagging = true,
                    aiTagDone = 0,
                    aiTagTotal = pending.size,
                    aiTagIsError = false,
                    aiTagStatus = "Scanning your library…"
                )
            }

            val suggestions = mutableListOf<PendingAiSuggestion>()
            var failed = 0
            var lastError: String? = null
            for ((index, page) in pending.withIndex()) {
                _settingsExtra.update {
                    it.copy(aiTagStatus = "Scanning ${index + 1} of ${pending.size}: ${page.title.ifBlank { "Untitled" }}")
                }
                try {
                    val tags = if (page.aiScannedAt == null) repository.suggestTagsForPage(page) else page.aiSuggestedTags
                    val category = repository.suggestCategoryForPage(page, allCategories, learning)
                    val categoryName = buildCategoryPath(category.categoryId, allCategories)
                    suggestions.add(PendingAiSuggestion(page, tags, category.categoryId, categoryName, category.reason))
                } catch (e: TimeoutCancellationException) {
                    failed++
                    lastError = "Request timed out after 60 seconds."
                    Log.e("ColorNestViewModel", "AI scan timed out for page ${page.id}", e)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failed++
                    lastError = e.localizedMessage ?: "Gemini inference failed."
                    Log.e("ColorNestViewModel", "AI scan failed for page ${page.id}", e)
                }
                _settingsExtra.update { it.copy(aiTagDone = index + 1) }
            }

            if (suggestions.isNotEmpty()) {
                _settingsExtra.update {
                    it.copy(
                        isAiTagging = false,
                        pendingAiSuggestions = suggestions,
                        aiTagIsError = false,
                        aiTagStatus = "Ready to review ${suggestions.size} suggestions.${if (failed > 0) " $failed failed${lastError?.let { err -> " ($err)" } ?: "."}" else ""}"
                    )
                }
            } else {
                _settingsExtra.update {
                    it.copy(
                        isAiTagging = false,
                        aiTagIsError = true,
                        aiTagStatus = "Scan failed for $failed file(s): ${lastError ?: "Unable to reach Gemini."}"
                    )
                }
            }
        }
    }

    fun confirmApplyAiSuggestions() {
        val suggestions = _settingsExtra.value.pendingAiSuggestions ?: return
        viewModelScope.launch {
            for (item in suggestions) {
                repository.applyAiTagSuggestions(item.page, item.tags, item.categoryId)
            }
            _settingsExtra.update {
                it.copy(
                    pendingAiSuggestions = null,
                    aiTagIsError = false,
                    aiTagStatus = "Applied AI tags and category assignments to ${suggestions.size} files."
                )
            }
        }
    }

    fun dismissAiSuggestions() {
        val count = _settingsExtra.value.pendingAiSuggestions?.size ?: 0
        _settingsExtra.update {
            it.copy(
                pendingAiSuggestions = null,
                aiTagIsError = false,
                aiTagStatus = "Scanned $count files. No tags were changed; run the scan again whenever you are ready to apply them."
            )
        }
    }

    // ---------- Wipe ----------

    fun setWipeStep(step: Int) {
        _modalState.update {
            it.copy(
                wipeStep = step,
                wipeError = if (step == 0) null else it.wipeError
            )
        }
    }

    fun setDeleteCloud(checked: Boolean) {
        _modalState.update { it.copy(deleteCloud = checked) }
    }

    fun setConfirmDeleteText(text: String) {
        _modalState.update { it.copy(confirmDeleteText = text) }
    }

    fun wipeApp() {
        if (_modalState.value.confirmDeleteText != "DELETE") return
        viewModelScope.launch {
            if (_modalState.value.deleteCloud) {
                val cloudDeleted = repository.trashCloudFolder()
                if (!cloudDeleted) {
                    _modalState.update {
                        it.copy(
                            wipeError = "Google Drive could not be cleared. Your local library was left untouched. Check your connection and Drive access, then try again."
                        )
                    }
                    return@launch
                }
            }
            repository.wipeAllData()
            _filterState.update { it.copy(activeTags = emptySet(), activeCategoryId = null, showNoTagsOnly = false) }
            _modalState.update {
                it.copy(
                    wipeError = null,
                    wipeStep = 0,
                    confirmDeleteText = "",
                    deleteCloud = false,
                    isSettingsOpen = false
                )
            }
            refreshSettingsMetadata()
        }
    }

    // ---------- Imports (Picker + Shared Intents) ----------

    fun importFiles(uris: List<Uri>, removeWhiteBackground: Boolean = true) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _modalState.update { it.copy(isProcessingAdd = true, addProgressPercent = 0) }
            val total = uris.size
            for ((index, uri) in uris.withIndex()) {
                repository.importUri(uri, removeWhiteBackground) { text, percent ->
                    val overall = ((index * 100) + percent) / total
                    _modalState.update {
                        it.copy(addProgressText = text, addProgressPercent = overall)
                    }
                }
            }
            _modalState.update { it.copy(addProgressPercent = 100) }
            kotlinx.coroutines.delay(400)
            _modalState.update { it.copy(isProcessingAdd = false, isAddModalOpen = false) }
            refreshSettingsMetadata()
        }
    }


    fun generateAiColoringPage(uri: Uri) {
        if (_modalState.value.isProcessingAdd) return
        viewModelScope.launch {
            _modalState.update {
                it.copy(
                    isProcessingAdd = true,
                    addProgressPercent = 8,
                    addProgressText = "Preparing your image for Gemini…",
                    isAddModalOpen = true
                )
            }
            try {
                _modalState.update {
                    it.copy(addProgressPercent = 25, addProgressText = "AI is redrawing your image as coloring-book artwork…")
                }
                repository.generateAiColoringPage(uri)
                _modalState.update {
                    it.copy(
                        addProgressPercent = 100,
                        addProgressText = "Your AI coloring page has been saved!"
                    )
                }
                refreshSettingsMetadata()
                kotlinx.coroutines.delay(900)
                _modalState.update {
                    it.copy(isProcessingAdd = false, isAddModalOpen = false, addProgressText = "")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _modalState.update {
                    it.copy(
                        isProcessingAdd = false,
                        addProgressPercent = 0,
                        addProgressText = "AI coloring-page creation failed: ${e.localizedMessage ?: "Please try again."}"
                    )
                }
            }
        }
    }

    fun importSharedImage(uri: Uri, asFinished: Boolean) {
        viewModelScope.launch {
            _modalState.update { it.copy(importStatus = if (asFinished) "Adding finished artwork to Showcase…" else "Adding coloring page to your library…") }
            runCatching { repository.importSharedImage(uri, asFinished) }
                .onSuccess {
                    _modalState.update { it.copy(importStatus = if (asFinished) "Finished artwork added to Showcase." else "Coloring page added to your library.") }
                }
                .onFailure { error ->
                    _modalState.update { it.copy(importStatus = "Couldn't add shared image: ${error.message ?: "Unknown error"}") }
                }
        }
    }

    fun importSharedUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _modalState.update { it.copy(importStatus = "Importing ${uris.size} shared file(s)…") }
            var imported = 0
            for (uri in uris) {
                runCatching {
                    repository.importUri(uri, removeWhiteBackground = true) { text, _ ->
                        _modalState.update { it.copy(importStatus = text) }
                    }
                    imported++
                }
            }
            _modalState.update {
                it.copy(importStatus = "Imported $imported shared file${if (imported == 1) "" else "s"}.")
            }
            refreshSettingsMetadata()
            kotlinx.coroutines.delay(5000)
            _modalState.update { it.copy(importStatus = "") }
        }
    }
}
