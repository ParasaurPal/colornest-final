package com.yourname.colornest.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yourname.colornest.ui.components.AddPageDialog
import com.yourname.colornest.ui.components.CategorizeDialog
import com.yourname.colornest.ui.components.OnboardingDialog
import com.yourname.colornest.ui.components.PageCard
import com.yourname.colornest.ui.components.SettingsDialog
import com.yourname.colornest.ui.components.SideMenuDrawer
import com.yourname.colornest.ui.theme.PurplePrimary
import com.yourname.colornest.ui.viewmodel.ColorNestViewModel
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    viewModel: ColorNestViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

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
                        .padding(horizontal = 16.dp, vertical = 12.dp)
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
                                modifier = Modifier.testTag("menu_button")
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
                                val currentCategoryName = if (uiState.activeCategoryId != null) {
                                    uiState.categories.find { it.id == uiState.activeCategoryId }?.name ?: "Category"
                                } else {
                                    "All Pages"
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
                                modifier = Modifier.testTag("settings_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Settings",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            IconButton(
                                onClick = { viewModel.toggleTheme() },
                                modifier = Modifier.testTag("theme_toggle_button")
                            ) {
                                Icon(
                                    imageVector = if (uiState.isDarkMode) Icons.Default.WbSunny else Icons.Default.Nightlight,
                                    contentDescription = "Toggle Theme",
                                    tint = if (uiState.isDarkMode) Color(0xFFFBBF24) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Search input
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        placeholder = { Text("Search titles or tags...", fontSize = 14.sp) },
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
            modifier = modifier.testTag("home_screen")
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                if (uiState.filteredPages.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (uiState.searchQuery.isNotBlank()) "No coloring pages found" else "No pages yet",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (uiState.searchQuery.isNotBlank()) "Try searching for a different title or tag" else "Tap the + button below to add your first coloring page or PDF!",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("pages_grid")
                    ) {
                        items(uiState.filteredPages, key = { it.id }) { page ->
                            PageCard(
                                page = page,
                                isDarkMode = uiState.isDarkMode,
                                onToggleFavorite = { viewModel.toggleFavorite(page.id) },
                                onDelete = { viewModel.deletePage(page) },
                                onEditDetails = { viewModel.setCategorizingPage(page) }
                            )
                        }
                    }
                }
            }
        }
    }

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
        onSave = { pageId, title, categoryId, tags ->
            viewModel.savePageDetails(pageId, title, categoryId, tags)
        },
        onClose = { viewModel.setCategorizingPage(null) }
    )

    // Settings Dialog
    SettingsDialog(
        isOpen = uiState.isSettingsOpen,
        aiEnabled = uiState.aiCategorizationEnabled,
        isSeeding = uiState.isSeeding,
        wipeStep = uiState.wipeStep,
        deleteCloud = uiState.deleteCloud,
        confirmDeleteText = uiState.confirmDeleteText,
        onToggleAi = { viewModel.toggleAiEnabled() },
        onSeedCategories = { viewModel.seedMassiveCategories() },
        onResetAiLearning = { viewModel.resetAiLearning() },
        onSetWipeStep = { viewModel.setWipeStep(it) },
        onSetDeleteCloud = { viewModel.setDeleteCloud(it) },
        onSetConfirmText = { viewModel.setConfirmDeleteText(it) },
        onWipeApp = { viewModel.wipeApp() },
        onClose = { viewModel.setSettingsOpen(false) }
    )

    // Onboarding Tutorial Dialog
    OnboardingDialog(
        show = uiState.showTutorial,
        onComplete = { viewModel.completeTutorial() }
    )
}
