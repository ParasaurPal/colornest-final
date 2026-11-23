package com.yourname.colornest

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.yourname.colornest.data.model.Page
import com.yourname.colornest.ui.theme.AccentPink
import com.yourname.colornest.ui.theme.ColorNestTheme
import com.yourname.colornest.ui.theme.PurplePrimary
import com.yourname.colornest.ui.viewmodel.ColorNestViewModel
import java.io.File

private const val PICKER_FILE_PROVIDER_AUTHORITY = "com.yourname.colornest.fileprovider"
private const val FILTER_ALL = -100L
private const val FILTER_FAVORITES = -2L
private const val FILTER_UNTAGGED = -1L

class PickPageActivity : ComponentActivity() {
    private val viewModel: ColorNestViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val allowMultiple = intent?.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false) == true
        val requestedMime = intent?.type?.lowercase()

        setContent {
            val uiState by viewModel.uiState.collectAsState()
            ColorNestTheme(darkTheme = uiState.isDarkMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PickPageScreen(
                        viewModel = viewModel,
                        allowMultiple = allowMultiple,
                        requestedMime = requestedMime,
                        onPickPages = { selectedPages -> returnSelectedPages(selectedPages) },
                        onCancel = {
                            setResult(RESULT_CANCELED)
                            finish()
                        }
                    )
                }
            }
        }
    }

    private fun returnSelectedPages(pages: List<Page>) {
        val validFiles = pages.mapNotNull { page ->
            val file = File(page.imageUrl)
            if (file.exists() && file.isFile) page to file else null
        }

        if (validFiles.isEmpty()) {
            Toast.makeText(this, getString(R.string.picker_file_missing), Toast.LENGTH_SHORT).show()
            return
        }

        val uris = ArrayList<Pair<Uri, String>>(validFiles.size)
        for ((page, file) in validFiles) {
            val uri = FileProvider.getUriForFile(this, PICKER_FILE_PROVIDER_AUTHORITY, file)
            val mime = resolveMimeType(file.absolutePath)
            val label = page.title.ifBlank { "ColorNest_Page_${page.id}" }
            uris.add(uri to "$mime|$label")
        }

        val firstUri = uris.first().first
        val firstMeta = uris.first().second.split("|", limit = 2)
        val firstMime = firstMeta[0]
        val firstLabel = firstMeta[1]

        val clipData = ClipData.newUri(contentResolver, firstLabel, firstUri)
        for (index in 1 until uris.size) {
            clipData.addItem(ClipData.Item(uris[index].first))
        }

        val resultIntent = Intent().apply {
            if (uris.size == 1) {
                setDataAndType(firstUri, firstMime)
            } else {
                type = "image/*"
            }
            this.clipData = clipData
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val callingPkg = callingPackage
        if (!callingPkg.isNullOrBlank()) {
            for ((uri, _) in uris) {
                grantUriPermission(callingPkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }

        setResult(RESULT_OK, resultIntent)
        finish()
    }

    private fun resolveMimeType(path: String): String {
        return if (path.endsWith(".png", ignoreCase = true)) "image/png" else "image/jpeg"
    }
}

@Composable
private fun PickPageScreen(
    viewModel: ColorNestViewModel,
    allowMultiple: Boolean,
    requestedMime: String?,
    onPickPages: (List<Page>) -> Unit,
    onCancel: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var filterMode by remember { mutableStateOf(FILTER_ALL) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }

    val displayedPages = remember(
        uiState.pages,
        uiState.categories,
        uiState.selectedTag,
        uiState.searchQuery,
        filterMode,
        requestedMime
    ) {
        val byMime = uiState.pages.filter { page ->
            val file = File(page.imageUrl)
            if (!file.exists() || !file.isFile) return@filter false
            when (requestedMime) {
                "image/png" -> page.imageUrl.endsWith(".png", ignoreCase = true)
                "image/jpeg", "image/jpg" ->
                    page.imageUrl.endsWith(".jpg", ignoreCase = true) ||
                        page.imageUrl.endsWith(".jpeg", ignoreCase = true)
                else -> true
            }
        }

        byMime.filter { page ->
            val matchesCategory = when (filterMode) {
                FILTER_ALL -> true
                FILTER_FAVORITES -> page.isFavorite
                FILTER_UNTAGGED -> page.isUncategorized
                else -> {
                    val allowedIds = collectCategorySubtree(filterMode, uiState.categories)
                    page.categoryId in allowedIds
                }
            }
            if (!matchesCategory) return@filter false

            val selectedTag = uiState.selectedTag
            if (selectedTag != null) {
                val matchesTag = page.tags.any { it.trim().equals(selectedTag, ignoreCase = true) }
                if (!matchesTag) return@filter false
            }

            if (uiState.searchQuery.isNotBlank()) {
                val q = uiState.searchQuery.lowercase()
                val matchTitle = page.title.lowercase().contains(q)
                val matchTags = page.tags.any { it.lowercase().contains(q) }
                if (!matchTitle && !matchTags) return@filter false
            }
            true
        }
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.choose_coloring_page),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (allowMultiple) {
                                stringResource(R.string.picker_subtitle_multiple)
                            } else {
                                stringResource(R.string.picker_subtitle_single)
                            },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("pick_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.cancel),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    placeholder = { Text(stringResource(R.string.search_hint), fontSize = 14.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotBlank()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear search",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = PurplePrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pick_search_field")
                )
            }
        },
        bottomBar = {
            if (allowMultiple && selectedIds.isNotEmpty()) {
                Surface(
                    tonalElevation = 4.dp,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { selectedIds = emptySet() },
                            modifier = Modifier.testTag("pick_clear_selection_button")
                        ) {
                            Text(stringResource(R.string.clear_selection))
                        }

                        Button(
                            onClick = {
                                val chosen = uiState.pages.filter { it.id in selectedIds }
                                onPickPages(chosen)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("pick_confirm_multiple_button")
                        ) {
                            Text(
                                text = stringResource(R.string.open_selected_count, selectedIds.size),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        },
        modifier = Modifier.testTag("pick_page_screen")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Category filter row
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("pick_category_filter_bar")
            ) {
                item(key = "cat_all") {
                    PickerFilterChip(
                        label = stringResource(R.string.all_pages),
                        selected = filterMode == FILTER_ALL,
                        onClick = { filterMode = FILTER_ALL },
                        testTag = "pick_cat_all"
                    )
                }
                item(key = "cat_fav") {
                    PickerFilterChip(
                        label = stringResource(R.string.favorites_title),
                        selected = filterMode == FILTER_FAVORITES,
                        onClick = { filterMode = FILTER_FAVORITES },
                        testTag = "pick_cat_favorites"
                    )
                }
                item(key = "cat_untagged") {
                    PickerFilterChip(
                        label = stringResource(R.string.untagged_title),
                        selected = filterMode == FILTER_UNTAGGED,
                        onClick = { filterMode = FILTER_UNTAGGED },
                        testTag = "pick_cat_untagged"
                    )
                }
                items(uiState.categories.filter { it.parentId == null }, key = { "cat_${it.id}" }) { category ->
                    PickerFilterChip(
                        label = category.name,
                        selected = filterMode == category.id,
                        onClick = { filterMode = category.id },
                        testTag = "pick_cat_${category.id}"
                    )
                }
            }

            // Unique tag filter row
            if (uiState.availableTags.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pick_tag_filter_bar")
                ) {
                    item(key = "tag_all") {
                        PickerFilterChip(
                            label = stringResource(R.string.all_tags),
                            selected = uiState.selectedTag == null,
                            onClick = { viewModel.selectTag(null) },
                            testTag = "pick_tag_all"
                        )
                    }
                    items(uiState.availableTags, key = { "tag_${it.lowercase()}" }) { tag ->
                        val isSelected = uiState.selectedTag != null &&
                            uiState.selectedTag.equals(tag, ignoreCase = true)
                        PickerFilterChip(
                            label = tag,
                            selected = isSelected,
                            onClick = { viewModel.selectTag(tag) },
                            testTag = "pick_tag_${tag.lowercase().replace(Regex("[^a-z0-9]+"), "_")}"
                        )
                    }
                }
            }

            if (displayedPages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.picker_empty_title),
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.picker_empty_subtitle),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 156.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .testTag("pick_pages_grid")
                ) {
                    items(displayedPages, key = { it.id }) { page ->
                        val isSelected = page.id in selectedIds
                        PickPageGridCard(
                            page = page,
                            isDarkMode = uiState.isDarkMode,
                            isSelected = isSelected,
                            allowMultiple = allowMultiple,
                            onClick = {
                                if (allowMultiple) {
                                    selectedIds = if (isSelected) {
                                        selectedIds - page.id
                                    } else {
                                        selectedIds + page.id
                                    }
                                } else {
                                    onPickPages(listOf(page))
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 12.sp
            )
        },
        leadingIcon = if (selected) {
            {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        } else null,
        shape = RoundedCornerShape(20.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = PurplePrimary,
            selectedLabelColor = Color.White,
            selectedLeadingIconColor = Color.White
        ),
        modifier = Modifier.testTag(testTag)
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PickPageGridCard(
    page: Page,
    isDarkMode: Boolean,
    isSelected: Boolean,
    allowMultiple: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val invertMatrix = remember {
        ColorMatrix(
            floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f
            )
        )
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) PurplePrimary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                shape = RoundedCornerShape(20.dp)
            )
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .testTag("pick_page_card_${page.id}")
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isDarkMode) Color(0xFF18181B) else Color(0xFFF4F4F5))
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(File(page.imageUrl))
                        .crossfade(true)
                        .build(),
                    contentDescription = page.title.ifBlank { "Coloring page" },
                    contentScale = ContentScale.Crop,
                    colorFilter = if (isDarkMode) ColorFilter.colorMatrix(invertMatrix) else null,
                    modifier = Modifier.fillMaxSize()
                )

                if (page.isFavorite) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .size(26.dp)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Favorite,
                            contentDescription = null,
                            tint = AccentPink,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                if (allowMultiple && isSelected) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(28.dp)
                            .background(Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Selected",
                            tint = PurplePrimary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = page.title.ifBlank { "Untitled page" },
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (page.tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    page.tags.take(3).forEach { tag ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = tag,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun collectCategorySubtree(
    catId: Long,
    categories: List<com.yourname.colornest.data.model.Category>
): List<Long> {
    val result = mutableListOf(catId)
    for (child in categories.filter { it.parentId == catId }) {
        result.addAll(collectCategorySubtree(child.id, categories))
    }
    return result
}
