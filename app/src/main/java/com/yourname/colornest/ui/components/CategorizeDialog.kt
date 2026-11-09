package com.yourname.colornest.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.yourname.colornest.data.model.Category
import com.yourname.colornest.data.model.Page
import com.yourname.colornest.ui.theme.PurplePrimary

@Composable
fun CategorizeDialog(
    page: Page?,
    categories: List<Category>,
    onSave: (pageId: Long, title: String, categoryId: Long?, tags: List<String>) -> Unit,
    onClose: () -> Unit
) {
    if (page == null) return

    var title by remember { mutableStateOf(page.title) }
    var selectedCategoryId by remember { mutableStateOf(page.categoryId) }
    var tagsText by remember { mutableStateOf(page.tags.joinToString(", ")) }
    var categoryDropdownExpanded by remember { mutableStateOf(false) }

    fun buildCategoryOptions(parentId: Long? = null, depth: Int = 0): List<Pair<Category, Int>> {
        val list = mutableListOf<Pair<Category, Int>>()
        val nodes = categories.filter { it.parentId == parentId }
        for (node in nodes) {
            list.add(Pair(node, depth))
            list.addAll(buildCategoryOptions(node.id, depth + 1))
        }
        return list
    }

    val flattenedCategories = remember(categories) {
        buildCategoryOptions(null, 0)
    }

    val selectedCategoryName = categories.find { it.id == selectedCategoryId }?.name ?: "Leave as Uncategorized"

    Dialog(onDismissRequest = onClose) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("categorize_dialog")
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Edit Details",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Title field
                Text(
                    text = "Title",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_title_input")
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Category dropdown
                Text(
                    text = "Category (Train AI)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = selectedCategoryName,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = {
                            Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null)
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { categoryDropdownExpanded = true }
                            .testTag("category_dropdown_trigger")
                    )

                    DropdownMenu(
                        expanded = categoryDropdownExpanded,
                        onDismissRequest = { categoryDropdownExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.85f)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Leave as Uncategorized") },
                            onClick = {
                                selectedCategoryId = null
                                categoryDropdownExpanded = false
                            }
                        )
                        flattenedCategories.forEach { (category, depth) ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "${"— ".repeat(depth)}${category.name}",
                                        fontSize = 14.sp
                                    )
                                },
                                onClick = {
                                    selectedCategoryId = category.id
                                    categoryDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Tags field
                Text(
                    text = "Tags (comma separated)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = tagsText,
                    onValueChange = { tagsText = it },
                    placeholder = { Text("e.g. easy, flowers, gift") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_tags_input")
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onClose,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            val tagList = tagsText
                                .split(",")
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }
                            onSave(page.id, title.trim(), selectedCategoryId, tagList)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("save_categorize_button")
                    ) {
                        Text("Save", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
