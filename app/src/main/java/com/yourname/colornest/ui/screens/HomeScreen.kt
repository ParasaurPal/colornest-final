package com.yourname.colornest.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterAltOff
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yourname.colornest.ui.components.AddPageDialog
import com.yourname.colornest.ui.components.CategorizeDialog
import com.yourname.colornest.ui.components.DriveBrowserDialog
import com.yourname.colornest.ui.components.DuplicatesDialog
import com.yourname.colornest.ui.components.OnboardingDialog
import com.yourname.colornest.ui.components.PageCard
import com.yourname.colornest.ui.components.PageViewerDialog
import com.yourname.colornest.ui.components.SettingsDialog
import com.yourname.colornest.ui.components.SideMenuDrawer
import com.yourname.colornest.ui.theme.AccentRed
import com.yourname.colornest.ui.theme.PurplePrimary
import com.yourname.colornest.ui.viewmodel.ColorNestViewModel
import com.yourname.colornest.ui.viewmodel.SortOption
import com.yourname.colornest.ui.viewmodel.TagMatchMode
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    viewModel: ColorNestViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val settingsExtra by viewModel.settingsExtra.collectAsState()
    val viewerState by viewModel.viewerState.collectAsState()
    val driveBrowserState by viewModel.driveBrowserState.collectAsState()
    val duplicatesState by viewModel.duplicatesState.collectAsState()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var isTagBrowserOpen by remember { mutableStateOf(false) }
    var isManagingTags by remember { mutableStateOf(false) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var showShowcase by remember { mutableStateOf(false) }
    val visiblePages = if (showShowcase) uiState.pages.filter { it.isCompleted && !it.isPreviewOnly } else uiState.filteredPages

    var showCreateTagDialog by remember { mutableStateOf(false) }
    var newTagInput by remember { mutableStateOf("") }
    var newTagError by remember { mutableStateOf<String?>(null) }

    var renamingTag by remember { mutableStateOf<String?>(null) }
    var renameTagInput by remember { mutableStateOf("") }
    var deletingTag by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = showShowcase) { showShowcase = false }

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    val activeFilterCount = (if (uiState.activeCategoryId != null) 1 else 0) +
        uiState.activeTags.size +
        (if (uiState.showNoTagsOnly) 1 else 0) +
        (if (uiState.searchQuery.isNotBlank()) 1 else 0)

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            SideMenuDrawer(
                categories = uiState.categories,
                activeCategoryId = uiState.activeCategoryId,
                onSelectCategory = { id ->
                    viewModel.setActiveCategory(id)
                    scope.launch { drawerState.close() }
                },
                onAddCategory = { name, parentId ->
                    viewModel.addCategory(name, parentId)
                },
                onOpenSettings = {
                    scope.launch { drawerState.close() }
                    viewModel.setSettingsOpen(true)
                },
                onClose = {
                    scope.launch { drawerState.close() }
                }
            )
        }
    ) {
        Scaffold(
            topBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    // Top App Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { scope.launch { drawerState.open() } },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("menu_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Menu",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Column(modifier = Modifier.padding(start = 4.dp)) {
                                Text(
                                    text = "ColorNest",
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                val currentCategoryName = if (showShowcase) "Showcase" else when (uiState.activeCategoryId) {
                                    -1L -> "Untagged"
                                    null -> "All Pages"
                                    else -> uiState.categories.find { it.id == uiState.activeCategoryId }?.name ?: "Category"
                                }
                                Text(
                                    text = currentCategoryName,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Right actions (Settings + Theme)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { viewModel.setSettingsOpen(true) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("settings_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Settings",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            IconButton(
                                onClick = { viewModel.toggleTheme() },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("theme_toggle_button")
                            ) {
                                Icon(
                                    imageVector = if (uiState.isDarkMode) Icons.Default.WbSunny else Icons.Default.Nightlight,
                                    contentDescription = "Toggle Theme",
                                    tint = if (uiState.isDarkMode) Color(0xFFFBBF24) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Search input
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        placeholder = { Text("Search titles or tags...", fontSize = 14.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        trailingIcon = {
                            if (uiState.searchQuery.isNotBlank()) {
                                IconButton(
                                    onClick = { viewModel.setSearchQuery("") },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("clear_search_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear search",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = { focusManager.clearFocus() }
                        ),
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            unfocusedBorderColor = Color.Transparent,
                            focusedBorderColor = PurplePrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("search_text_field")
                    )
                }
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { viewModel.setAddModalOpen(true) },
                    containerColor = PurplePrimary,
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier
                        .size(56.dp)
                        .testTag("fab_add_page")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add Pages", modifier = Modifier.size(28.dp))
                }
            },

            bottomBar = {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 4.dp,
                    shadowElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showShowcase = !showShowcase }
                            .padding(horizontal = 20.dp, vertical = 13.dp)
                            .testTag("showcase_banner"),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (showShowcase) "← Back to coloring pages" else "✦  Showcase  ·  ${uiState.pages.count { it.isCompleted && !it.isPreviewOnly }} finished pages",
                            color = PurplePrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            },
            modifier = modifier.testTag("home_screen")
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // Drive scan or shared import banner
                val scanProg = uiState.scanProgress
                if (scanProg != null && scanProg.active) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(PurplePrimary.copy(alpha = 0.1f))
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = scanProg.message,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = PurplePrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { (scanProg.percent / 100f).coerceIn(0f, 1f) },
                            color = PurplePrimary,
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
                        )
                    }
                }

                if (uiState.importStatus.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(PurplePrimary.copy(alpha = 0.12f))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = uiState.importStatus,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = PurplePrimary
                        )
                    }
                }

                // Filter controls row: Browse tags, No tags, Clear filters
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                        .testTag("tag_filter_bar")
                ) {
                    item(key = "__browse_tags__") {
                        val isHighlighted = isTagBrowserOpen || uiState.activeTags.isNotEmpty()
                        FilterChip(
                            selected = isHighlighted,
                            onClick = { isTagBrowserOpen = !isTagBrowserOpen },
                            label = {
                                Text(
                                    text = if (uiState.activeTags.isNotEmpty()) {
                                        "Tags (${uiState.activeTags.size}/${uiState.tagCounts.size})"
                                    } else {
                                        "Browse tags (${uiState.tagCounts.size})"
                                    },
                                    fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.sp
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Label,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            shape = RoundedCornerShape(20.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PurplePrimary,
                                selectedLabelColor = Color.White,
                                selectedLeadingIconColor = Color.White
                            ),
                            modifier = Modifier.testTag("browse_tags_button")
                        )
                    }

                    item(key = "__no_tags__") {
                        FilterChip(
                            selected = uiState.showNoTagsOnly,
                            onClick = { viewModel.toggleNoTagsOnly() },
                            label = {
                                Text(
                                    text = "Untagged only",
                                    fontWeight = if (uiState.showNoTagsOnly) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.sp
                                )
                            },
                            leadingIcon = if (uiState.showNoTagsOnly) {
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
                            modifier = Modifier.testTag("no_tags_filter_button")
                        )
                    }

                    if (activeFilterCount > 0) {
                        item(key = "__clear_all__") {
                            FilterChip(
                                selected = false,
                                onClick = { viewModel.clearAllFilters() },
                                label = {
                                    Text(
                                        text = "Clear ($activeFilterCount)",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.FilterAltOff,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp)
                                    )
                                },
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.testTag("clear_all_filters_button")
                            )
                        }
                    }

                    // Quick single-tap chips for active/top tags when tag browser is collapsed
                    if (!isTagBrowserOpen && uiState.availableTags.isNotEmpty()) {
                        items(uiState.availableTags, key = { it.lowercase() }) { tag ->
                            val isSelected = uiState.activeTags.any { it.equals(tag, ignoreCase = true) }
                            val safeTagId = tag.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.toggleTagFilter(tag) },
                                label = {
                                    Text(
                                        text = tag,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 12.sp
                                    )
                                },
                                leadingIcon = if (isSelected) {
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
                                modifier = Modifier.testTag("tag_filter_$safeTagId")
                            )
                        }
                    }
                }

                // Expandable Tag Browser Panel (Multi-select + Match Mode + Create/Rename/Delete)
                AnimatedVisibility(visible = isTagBrowserOpen) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 1.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                RoundedCornerShape(16.dp)
                            )
                            .clip(RoundedCornerShape(16.dp))
                            .testTag("tag_browser_panel")
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "Tags",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(PurplePrimary.copy(alpha = 0.12f))
                                            .clickable {
                                                viewModel.setTagMatchMode(
                                                    if (uiState.tagMatchMode == TagMatchMode.ALL) TagMatchMode.ANY
                                                    else TagMatchMode.ALL
                                                )
                                            }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                            .testTag("tag_match_mode_button")
                                    ) {
                                        Text(
                                            text = if (uiState.tagMatchMode == TagMatchMode.ALL) "Match: All" else "Match: Any",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = PurplePrimary
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (uiState.activeTags.isNotEmpty()) {
                                        TextButton(
                                            onClick = { viewModel.clearTagFilters() },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text("Clear", fontSize = 12.sp)
                                        }
                                    }
                                    TextButton(
                                        onClick = { isManagingTags = !isManagingTags },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.testTag("manage_tags_toggle_button")
                                    ) {
                                        Text(
                                            text = if (isManagingTags) "Done" else "Edit",
                                            fontSize = 12.sp
                                        )
                                    }
                                    TextButton(
                                        onClick = {
                                            newTagInput = ""
                                            newTagError = null
                                            showCreateTagDialog = true
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.testTag("new_tag_button")
                                    ) {
                                        Text("+ New", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PurplePrimary)
                                    }
                                }
                            }

                            if (uiState.tagCounts.isEmpty()) {
                                Text(
                                    text = "No tags yet. Tap + New to create a tag or add tags on any page.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    uiState.tagCounts.forEach { (tag, count) ->
                                        val isSelected = uiState.activeTags.any { it.equals(tag, ignoreCase = true) }
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(
                                                    if (isSelected) PurplePrimary
                                                    else MaterialTheme.colorScheme.surfaceVariant
                                                )
                                                .clickable { viewModel.toggleTagFilter(tag) }
                                                .padding(
                                                    start = 10.dp,
                                                    end = if (isManagingTags) 4.dp else 10.dp,
                                                    top = 5.dp,
                                                    bottom = 5.dp
                                                )
                                        ) {
                                            Text(
                                                text = "$tag ($count)",
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            if (isManagingTags) {
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Icon(
                                                    imageVector = Icons.Default.Edit,
                                                    contentDescription = "Rename $tag",
                                                    tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier
                                                        .size(16.dp)
                                                        .clickable {
                                                            renamingTag = tag
                                                            renameTagInput = tag
                                                        }
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Icon(
                                                    imageVector = Icons.Default.DeleteOutline,
                                                    contentDescription = "Delete $tag",
                                                    tint = if (isSelected) Color.White else AccentRed,
                                                    modifier = Modifier
                                                        .size(16.dp)
                                                        .clickable { deletingTag = tag }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Result count + Sort selector bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (showShowcase) "${visiblePages.size} finished page${if (visiblePages.size == 1) "" else "s"} · Showcase" else "${visiblePages.size} page${if (visiblePages.size == 1) "" else "s"}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Box {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { sortMenuExpanded = true }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                                .testTag("sort_dropdown_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Sort,
                                contentDescription = "Sort order",
                                tint = PurplePrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = uiState.sortBy.label,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = PurplePrimary
                            )
                        }

                        DropdownMenu(
                            expanded = sortMenuExpanded,
                            onDismissRequest = { sortMenuExpanded = false }
                        ) {
                            SortOption.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = option.label,
                                            fontWeight = if (option == uiState.sortBy) FontWeight.Bold else FontWeight.Normal,
                                            color = if (option == uiState.sortBy) PurplePrimary else MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    onClick = {
                                        viewModel.setSortOption(option)
                                        sortMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (visiblePages.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            val hasFilter = activeFilterCount > 0
                            Text(
                                text = if (showShowcase) "Your Showcase is ready" else if (hasFilter) "No coloring pages found" else "No pages yet",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = when {
                                    showShowcase -> "Mark a page as Completed in its edit details to feature it here."
                                    uiState.showNoTagsOnly ->
                                        "Every page in this view already has at least one tag."
                                    uiState.activeTags.isNotEmpty() ->
                                        "No pages match the selected tags."
                                    uiState.searchQuery.isNotBlank() ->
                                        "Try searching for a different title or tag"
                                    uiState.activeCategoryId != null ->
                                        "No pages in this category yet."
                                    else ->
                                        "Tap the + button below to add your first coloring page or PDF!"
                                },
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            if (hasFilter) {
                                Spacer(modifier = Modifier.height(12.dp))
                                TextButton(
                                    onClick = { viewModel.clearAllFilters() },
                                    modifier = Modifier.testTag("clear_tag_filter_button")
                                ) {
                                    Text("Clear Filters", color = PurplePrimary, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 160.dp),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 92.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("pages_grid")
                        ) {
                            items(visiblePages, key = { it.id }) { page ->
                                PageCard(
                                    page = page,
                                    isDarkMode = uiState.isDarkMode,
                                    galleryFullImages = uiState.galleryFullImages,
                                    onViewPage = { viewModel.openPageViewer(page) },
                                    onToggleFavorite = { viewModel.toggleFavorite(page.id) },
                                    onDelete = { viewModel.deletePage(page) },
                                    onEditDetails = { viewModel.setCategorizingPage(page) },
                                    selectedTag = uiState.selectedTag,
                                    activeTags = uiState.activeTags,
                                    onTagClick = { tag -> viewModel.toggleTagFilter(tag) },
                                    onDownloadDriveCache = { p -> viewModel.repository.downloadDriveFileToCache(p) },
                                    onDownloadFullPage = { viewModel.openPageViewer(page) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Create Tag Dialog
    if (showCreateTagDialog) {
        AlertDialog(
            onDismissRequest = { showCreateTagDialog = false },
            title = { Text("New Tag", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = newTagInput,
                        onValueChange = {
                            newTagInput = it
                            newTagError = null
                        },
                        label = { Text("Tag name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("create_tag_input")
                    )
                    if (newTagError != null) {
                        Text(text = newTagError!!, color = AccentRed, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = newTagInput.trim()
                        if (trimmed.isNotEmpty()) {
                            viewModel.createTag(trimmed) {
                                newTagError = "\"$trimmed\" already exists."
                            }
                            if (newTagError == null) {
                                showCreateTagDialog = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                    modifier = Modifier.testTag("confirm_create_tag_button")
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateTagDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Rename Tag Dialog
    renamingTag?.let { oldTag ->
        AlertDialog(
            onDismissRequest = { renamingTag = null },
            title = { Text("Rename Tag", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = renameTagInput,
                    onValueChange = { renameTagInput = it },
                    label = { Text("New tag name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("rename_tag_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = renameTagInput.trim()
                        if (trimmed.isNotEmpty()) {
                            viewModel.renameTag(oldTag, trimmed)
                            renamingTag = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary)
                ) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { renamingTag = null }) { Text("Cancel") }
            }
        )
    }

    // Delete Tag Confirmation Dialog
    deletingTag?.let { targetTag ->
        AlertDialog(
            onDismissRequest = { deletingTag = null },
            title = { Text("Delete \"$targetTag\"?", fontWeight = FontWeight.Bold) },
            text = {
                Text("This removes the tag \"$targetTag\" from all pages. Your images will not be deleted.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteTag(targetTag)
                        deletingTag = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deletingTag = null }) { Text("Cancel") }
            }
        )
    }

    // In-App Fullscreen Page Viewer
    PageViewerDialog(
        state = viewerState,
        isDarkMode = uiState.isDarkMode,
        onClose = { viewModel.closePageViewer() },
        onToggleFavorite = { viewModel.toggleFavorite(it) },
        onEditDetails = { page -> viewModel.setCategorizingPage(page) },
        onDownloadFullPage = { viewModel.downloadViewingPreviewPage() },
        onDownloadDriveCache = { p -> viewModel.repository.downloadDriveFileToCache(p) }
    )

    // Add Page Dialog
    AddPageDialog(
        isOpen = uiState.isAddModalOpen,
        isProcessing = uiState.isProcessingAdd,
        progressText = uiState.addProgressText,
        progressPercent = uiState.addProgressPercent,
        onFilesSelected = { uris, removeWhite -> viewModel.importFiles(uris, removeWhite) },
        onClose = { viewModel.setAddModalOpen(false) }
    )

    // Categorize / Edit Details Dialog
    CategorizeDialog(
        page = uiState.categorizingPage,
        categories = uiState.categories,
        onSave = { pageId, title, categoryId, tags, isCompleted ->
            viewModel.savePageDetails(pageId, title, categoryId, tags, isCompleted)
        },
        onClose = { viewModel.setCategorizingPage(null) }
    )

    // Settings Dialog
    SettingsDialog(
        isOpen = uiState.isSettingsOpen,
        extra = settingsExtra,
        isSeeding = uiState.isSeeding,
        wipeStep = uiState.wipeStep,
        deleteCloud = uiState.deleteCloud,
        confirmDeleteText = uiState.confirmDeleteText,
        wipeError = uiState.wipeError,
        onSeedCategories = { viewModel.seedMassiveCategories() },
        onToggleGalleryFullImages = { viewModel.toggleGalleryFullImages(it) },
        onProcessExistingLibrary = { viewModel.processExistingLibrary() },
        onOpenDuplicates = {
            viewModel.setSettingsOpen(false)
            viewModel.openDuplicatesFinder()
        },
        onExportBackup = { viewModel.exportBackup(it) },
        onImportBackup = { viewModel.importBackup(it) },
        onSyncDriveNow = { viewModel.syncDriveNow() },
        onOpenDriveBrowser = {
            viewModel.setSettingsOpen(false)
            viewModel.openDriveBrowser()
        },
        onDisconnectDrive = { viewModel.disconnectDrive() },
        onSetDriveScanOnOpen = { viewModel.setDriveScanOnOpen(it) },
        onRefreshDriveSettings = { viewModel.refreshDriveSettings() },
        onUpdateGeminiKey = { viewModel.updateGeminiApiKeyInput(it) },
        onSaveGeminiSettings = { viewModel.saveGeminiSettings() },
        onToggleAiAutoTag = { viewModel.toggleAiAutoTag(it) },
        onScanLibraryWithAi = { viewModel.scanLibraryWithAi() },
        onConfirmApplyAiSuggestions = { viewModel.confirmApplyAiSuggestions() },
        onDismissAiSuggestions = { viewModel.dismissAiSuggestions() },
        onClearAllTags = { viewModel.clearAllTags() },
        onSetWipeStep = { viewModel.setWipeStep(it) },
        onSetDeleteCloud = { viewModel.setDeleteCloud(it) },
        onSetConfirmText = { viewModel.setConfirmDeleteText(it) },
        onWipeApp = { viewModel.wipeApp() },
        onClose = { viewModel.setSettingsOpen(false) }
    )

    // Google Drive Browser Dialog
    DriveBrowserDialog(
        state = driveBrowserState,
        isDarkMode = uiState.isDarkMode,
        onRefresh = { viewModel.loadDriveBrowserFiles() },
        onSelectEntry = { viewModel.selectDriveBrowserEntry(it) },
        onDownloadEntry = { viewModel.downloadDriveBrowserEntry(it) },
        onClose = { viewModel.closeDriveBrowser() }
    )

    // Duplicates Finder Dialog
    DuplicatesDialog(
        state = duplicatesState,
        isDarkMode = uiState.isDarkMode,
        onSelectToKeep = { hash, pageId -> viewModel.selectDuplicateToKeep(hash, pageId) },
        onDeleteDuplicates = { viewModel.deleteSelectedDuplicates() },
        onClose = { viewModel.closeDuplicatesFinder() }
    )

    // Onboarding Tutorial Dialog
    OnboardingDialog(
        show = uiState.showTutorial,
        onComplete = { viewModel.completeTutorial() }
    )
}
