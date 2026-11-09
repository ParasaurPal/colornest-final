package com.yourname.colornest.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yourname.colornest.data.model.Category
import com.yourname.colornest.data.model.Page
import com.yourname.colornest.data.repository.ColorNestRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class UiState(
    val pages: List<Page> = emptyList(),
    val filteredPages: List<Page> = emptyList(),
    val categories: List<Category> = emptyList(),
    val activeCategoryId: Long? = null,
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
    val aiCategorizationEnabled: Boolean = true,
    val wipeStep: Int = 0,
    val deleteCloud: Boolean = false,
    val confirmDeleteText: String = ""
)

class ColorNestViewModel(application: Application) : AndroidViewModel(application) {
    val repository = ColorNestRepository(application)

    private val _activeCategoryId = MutableStateFlow<Long?>(null)
    private val _searchQuery = MutableStateFlow("")
    private val _isDarkMode = MutableStateFlow(false)
    private val _showTutorial = MutableStateFlow(false)
    private val _isAddModalOpen = MutableStateFlow(false)
    private val _isMenuOpen = MutableStateFlow(false)
    private val _isSettingsOpen = MutableStateFlow(false)
    private val _categorizingPage = MutableStateFlow<Page?>(null)
    private val _isProcessingAdd = MutableStateFlow(false)
    private val _addProgressText = MutableStateFlow("")
    private val _addProgressPercent = MutableStateFlow(0)
    private val _isSeeding = MutableStateFlow(false)
    private val _aiCategorizationEnabled = MutableStateFlow(true)
    private val _wipeStep = MutableStateFlow(0)
    private val _deleteCloud = MutableStateFlow(false)
    private val _confirmDeleteText = MutableStateFlow("")

    val pagesFlow = repository.getAllPagesFlow()
    val categoriesFlow = repository.getAllCategoriesFlow()

    init {
        repository.startBackgroundWorker(viewModelScope)
        viewModelScope.launch {
            val seen = repository.getSetting("hasSeenTutorial")
            if (seen == null || seen != "true") {
                _showTutorial.value = true
            }

            val theme = repository.getSetting("theme")
            if (theme == "dark") {
                _isDarkMode.value = true
            }

            val aiSetting = repository.getSetting("aiCategorizationEnabled")
            if (aiSetting != null) {
                _aiCategorizationEnabled.value = aiSetting.toBooleanStrictOrNull() ?: true
            }
        }
    }

    val uiState: StateFlow<UiState> = combine(
        pagesFlow,
        categoriesFlow,
        _activeCategoryId,
        _searchQuery,
        _isDarkMode,
        _showTutorial,
        _isAddModalOpen,
        _isMenuOpen,
        _isSettingsOpen,
        _categorizingPage,
        _isProcessingAdd,
        _addProgressText,
        _addProgressPercent,
        _isSeeding,
        _aiCategorizationEnabled,
        _wipeStep,
        _deleteCloud,
        _confirmDeleteText
    ) { args: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        val pages = args[0] as List<Page>
        @Suppress("UNCHECKED_CAST")
        val categories = args[1] as List<Category>
        val activeCatId = args[2] as Long?
        val query = args[3] as String
        val isDark = args[4] as Boolean
        val showTut = args[5] as Boolean
        val addModal = args[6] as Boolean
        val menuOpen = args[7] as Boolean
        val settingsOpen = args[8] as Boolean
        val catPage = args[9] as Page?
        val procAdd = args[10] as Boolean
        val progText = args[11] as String
        val progPct = args[12] as Int
        val seeding = args[13] as Boolean
        val aiEnabled = args[14] as Boolean
        val wipeSt = args[15] as Int
        val delCloud = args[16] as Boolean
        val confText = args[17] as String

        val filtered = filterPages(pages, categories, activeCatId, query)

        UiState(
            pages = pages,
            filteredPages = filtered,
            categories = categories,
            activeCategoryId = activeCatId,
            searchQuery = query,
            isDarkMode = isDark,
            showTutorial = showTut,
            isAddModalOpen = addModal,
            isMenuOpen = menuOpen,
            isSettingsOpen = settingsOpen,
            categorizingPage = catPage,
            isProcessingAdd = procAdd,
            addProgressText = progText,
            addProgressPercent = progPct,
            isSeeding = seeding,
            aiCategorizationEnabled = aiEnabled,
            wipeStep = wipeSt,
            deleteCloud = delCloud,
            confirmDeleteText = confText
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

    private fun filterPages(
        pages: List<Page>,
        categories: List<Category>,
        activeCatId: Long?,
        query: String
    ): List<Page> {
        return pages.filter { page ->
            if (activeCatId != null) {
                val validIds = getCategoryAndChildren(activeCatId, categories)
                if (page.categoryId !in validIds) return@filter false
            }
            if (query.isNotBlank()) {
                val q = query.lowercase()
                val matchTitle = page.title.lowercase().contains(q)
                val matchTags = page.tags.any { it.lowercase().contains(q) }
                if (!matchTitle && !matchTags) return@filter false
            }
            true
        }
    }

    fun setSearchQuery(q: String) { _searchQuery.value = q }
    fun setActiveCategory(id: Long?) { _activeCategoryId.value = id }
    fun setMenuOpen(open: Boolean) { _isMenuOpen.value = open }
    fun setAddModalOpen(open: Boolean) { _isAddModalOpen.value = open }
    fun setSettingsOpen(open: Boolean) { _isSettingsOpen.value = open }
    fun setCategorizingPage(page: Page?) { _categorizingPage.value = page }

    fun toggleTheme() {
        val newTheme = !_isDarkMode.value
        _isDarkMode.value = newTheme
        viewModelScope.launch {
            repository.setSetting("theme", if (newTheme) "dark" else "light")
        }
    }

    fun completeTutorial() {
        _showTutorial.value = false
        viewModelScope.launch {
            repository.setSetting("hasSeenTutorial", "true")
        }
    }

    fun toggleFavorite(pageId: Long) {
        viewModelScope.launch {
            repository.toggleFavorite(pageId)
        }
    }

    fun deletePage(page: Page) {
        viewModelScope.launch {
            repository.deletePage(page)
        }
    }

    fun addCategory(name: String, parentId: Long?) {
        viewModelScope.launch {
            repository.insertCategory(name, parentId)
        }
    }

    fun savePageDetails(pageId: Long, title: String, categoryId: Long?, tags: List<String>) {
        viewModelScope.launch {
            repository.updatePageDetails(pageId, title, categoryId, tags)
            _categorizingPage.value = null
        }
    }

    fun toggleAiEnabled() {
        val newVal = !_aiCategorizationEnabled.value
        _aiCategorizationEnabled.value = newVal
        viewModelScope.launch {
            repository.setSetting("aiCategorizationEnabled", newVal.toString())
        }
    }

    fun resetAiLearning() {
        viewModelScope.launch {
            repository.resetAiBrain()
        }
    }

    fun seedMassiveCategories() {
        viewModelScope.launch {
            _isSeeding.value = true
            repository.seedMassiveCategories()
            _isSeeding.value = false
            _isSettingsOpen.value = false
        }
    }

    fun setWipeStep(step: Int) { _wipeStep.value = step }
    fun setDeleteCloud(checked: Boolean) { _deleteCloud.value = checked }
    fun setConfirmDeleteText(text: String) { _confirmDeleteText.value = text }

    fun wipeApp() {
        if (_confirmDeleteText.value != "DELETE") return
        viewModelScope.launch {
            repository.wipeAllData()
            _wipeStep.value = 0
            _confirmDeleteText.value = ""
            _deleteCloud.value = false
            _isSettingsOpen.value = false
        }
    }

    fun importFiles(uris: List<Uri>, removeWhiteBackground: Boolean = true) {
        viewModelScope.launch {
            _isProcessingAdd.value = true
            _addProgressPercent.value = 0
            val total = uris.size

            for ((index, uri) in uris.withIndex()) {
                repository.importUri(uri, removeWhiteBackground) { text, percent ->
                    _addProgressText.value = text
                    val overall = ((index * 100) + percent) / total
                    _addProgressPercent.value = overall
                }
            }
            _addProgressPercent.value = 100
            kotlinx.coroutines.delay(400)
            _isProcessingAdd.value = false
            _isAddModalOpen.value = false
        }
    }
}
