package com.yourname.colornest.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yourname.colornest.data.model.Category
import com.yourname.colornest.ui.theme.PurplePrimary

@Composable
fun SideMenuDrawer(
    categories: List<Category>,
    activeCategoryId: Long?,
    onSelectCategory: (Long?) -> Unit,
    onAddCategory: (String, Long?) -> Unit,
    onOpenSettings: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }
    val expandedMap = remember { mutableStateMapOf<Long, Boolean>() }

    if (showAddCategoryDialog) {
        AlertDialog(
            onDismissRequest = { showAddCategoryDialog = false },
            title = { Text("New Root Category", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newCategoryName,
                    onValueChange = { newCategoryName = it },
                    label = { Text("Category Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("new_category_name_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newCategoryName.isNotBlank()) {
                            onAddCategory(newCategoryName.trim(), null)
                            newCategoryName = ""
                            showAddCategoryDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                    modifier = Modifier.testTag("save_category_button")
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddCategoryDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
            .width(320.dp)
            .fillMaxHeight()
            .testTag("side_menu_drawer")
    ) {
        Column(modifier = Modifier.fillMaxHeight()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Categories",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onClose, modifier = Modifier.testTag("close_menu_button")) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Menu",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(color = DividerDefaults.color.copy(alpha = 0.5f))

            // Category Tree list
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(8.dp)
            ) {
                // Central inbox for imports that have not been tagged yet.
                item {
                    val isUntaggedActive = activeCategoryId == -1L
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isUntaggedActive) PurplePrimary.copy(alpha = 0.15f) else Color.Transparent)
                            .clickable { onSelectCategory(-1L) }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .testTag("untagged_menu_item")
                    ) {
                        Text(
                            text = "Untagged",
                            fontWeight = if (isUntaggedActive) FontWeight.Bold else FontWeight.Normal,
                            color = if (isUntaggedActive) PurplePrimary else MaterialTheme.colorScheme.onSurface,
                            fontSize = 16.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // All Pages option
                item {
                    val isAllActive = activeCategoryId == null
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isAllActive) PurplePrimary.copy(alpha = 0.15f) else Color.Transparent
                            )
                            .clickable { onSelectCategory(null) }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .testTag("all_pages_menu_item")
                    ) {
                        Text(
                            text = "All Pages",
                            fontWeight = if (isAllActive) FontWeight.Bold else FontWeight.Normal,
                            color = if (isAllActive) PurplePrimary else MaterialTheme.colorScheme.onSurface,
                            fontSize = 16.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Recursive root nodes
                val rootNodes = categories.filter { it.parentId == null }
                items(rootNodes.size, key = { rootNodes[it].id }) { index ->
                    CategoryTreeItem(
                        node = rootNodes[index],
                        allCategories = categories,
                        depth = 0,
                        activeCategoryId = activeCategoryId,
                        expandedMap = expandedMap,
                        onSelectCategory = onSelectCategory,
                        onToggleExpand = { id ->
                            expandedMap[id] = !(expandedMap[id] ?: false)
                        }
                    )
                }
            }

            HorizontalDivider(color = DividerDefaults.color.copy(alpha = 0.5f))

            // Footer buttons
            Column(modifier = Modifier.padding(16.dp)) {
                Button(
                    onClick = { showAddCategoryDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("add_root_category_button")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Root Category", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onOpenSettings,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("settings_menu_button")
                ) {
                    Icon(imageVector = Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Settings", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun CategoryTreeItem(
    node: Category,
    allCategories: List<Category>,
    depth: Int,
    activeCategoryId: Long?,
    expandedMap: Map<Long, Boolean>,
    onSelectCategory: (Long?) -> Unit,
    onToggleExpand: (Long) -> Unit
) {
    val children = allCategories.filter { it.parentId == node.id }
    val hasChildren = children.isNotEmpty()
    val isExpanded = expandedMap[node.id] ?: false
    val isActive = activeCategoryId == node.id

    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = (depth * 16).dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (isActive) PurplePrimary.copy(alpha = 0.15f) else Color.Transparent
                )
                .clickable { onSelectCategory(node.id) }
                .padding(horizontal = 8.dp, vertical = 10.dp)
                .testTag("category_item_${node.id}")
        ) {
            if (hasChildren) {
                IconButton(
                    onClick = { onToggleExpand(node.id) },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(24.dp))
            }

            Spacer(modifier = Modifier.width(6.dp))

            Text(
                text = node.name,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                color = if (isActive) PurplePrimary else MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
        }

        if (hasChildren && isExpanded) {
            children.forEach { child ->
                CategoryTreeItem(
                    node = child,
                    allCategories = allCategories,
                    depth = depth + 1,
                    activeCategoryId = activeCategoryId,
                    expandedMap = expandedMap,
                    onSelectCategory = onSelectCategory,
                    onToggleExpand = onToggleExpand
                )
            }
        }
    }
}
