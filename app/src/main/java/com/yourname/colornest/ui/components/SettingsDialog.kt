package com.yourname.colornest.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.yourname.colornest.ui.theme.AccentGreen
import com.yourname.colornest.ui.theme.AccentRed
import com.yourname.colornest.ui.theme.PurpleContainerLight
import com.yourname.colornest.ui.theme.PurplePrimary

@Composable
fun SettingsDialog(
    isOpen: Boolean,
    isSeeding: Boolean,
    wipeStep: Int,
    deleteCloud: Boolean,
    confirmDeleteText: String,
    onSeedCategories: () -> Unit,
    onClearAllTags: () -> Unit,
    onSetWipeStep: (Int) -> Unit,
    onSetDeleteCloud: (Boolean) -> Unit,
    onSetConfirmText: (String) -> Unit,
    onWipeApp: () -> Unit,
    onClose: () -> Unit
) {
    if (!isOpen) return
    var showFullAppGuide by remember { mutableStateOf(false) }
    var showClearTagsConfirm by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onClose) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("settings_dialog")
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = onClose, modifier = Modifier.testTag("close_settings_button")) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Complete in-app user guide.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(PurpleContainerLight.copy(alpha = 0.35f))
                        .padding(16.dp)
                ) {
                    Text("ColorNest User Guide", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = PurplePrimary)
                    Text("Learn how to import, organize, find, edit, and protect your coloring collection.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { showFullAppGuide = !showFullAppGuide },
                        modifier = Modifier.fillMaxWidth().testTag("full_app_guide_toggle")
                    ) { Text(if (showFullAppGuide) "Close complete app guide" else "Open complete app guide") }
                    if (showFullAppGuide) {
                        Column(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("1. Your collection", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("The home screen shows your coloring pages. Choose All Pages in the side menu to browse everything, or choose a category to narrow the collection. Untagged contains pages that do not yet have a category.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("2. Add images and PDF books", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Tap the + button to import from your device. Select one or more images, or choose a PDF. PDF pages are rendered into individual page entries. Keep the app open while a large import finishes; the progress indicator shows its status. If an import appears incomplete, check the selected file and available device storage, then try again.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("3. Categories and subcategories", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Open the side menu to browse your category tree. Categories can contain subcategories, for example Animals > Mammals > Canines > Dogs. Use the add-category action to create a category under the currently selected parent. Settings > Load Default Categories can populate the starter category tree; avoid loading it repeatedly if you have already customized your tree.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("4. Search, favorites, and page details", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Use the search box to find page titles or tags. Open a page's details/edit action to change its title, category, and tags. Use the heart on a page card to add or remove it from Favorites. Tags help you find pages by subject, style, or any labels you choose. Use the No tags filter on the home screen to find pages that still need tags.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("5. Appearance and viewing", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Use the sun/moon control on the home screen to switch light and dark themes. Dark mode is intended for comfortable viewing and can display coloring line art with an inverted appearance where supported.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("6. Open or share a page", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Use the page card's available open/share actions to send an image to a compatible drawing, printing, or sharing app. The receiving app controls its own editing and print settings.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("7. Troubleshooting and data safety", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("If a page is missing, check All Pages, the selected category, and the search text. Keep separate backups of images you cannot replace. Erase All Images & Data permanently removes the app's local collection and settings after its confirmation steps; it cannot be undone.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Setup Categories card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(PurpleContainerLight.copy(alpha = 0.35f))
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = PurplePrimary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Setup Categories", fontWeight = FontWeight.Bold, color = PurplePrimary, fontSize = 15.sp)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onSeedCategories,
                        enabled = !isSeeding,
                        colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("load_categories_button")
                    ) {
                        if (isSeeding) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Building Tree...")
                        } else {
                            Text("Load Default Categories", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Danger Zone
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, AccentRed.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Security, contentDescription = null, tint = AccentRed, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Danger Zone", fontWeight = FontWeight.Bold, color = AccentRed, fontSize = 15.sp)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = { showClearTagsConfirm = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("clear_all_tags_button")
                    ) {
                        Icon(imageVector = Icons.Outlined.Delete, contentDescription = null, tint = AccentRed, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Clear All Tags", color = AccentRed, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { onSetWipeStep(1) },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("erase_data_button")
                    ) {
                        Icon(imageVector = Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Erase All Images & Data", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Clear-all-tags confirmation dialog
    if (showClearTagsConfirm) {
        AlertDialog(
            onDismissRequest = { showClearTagsConfirm = false },
            title = { Text("Clear all tags?") },
            text = { Text("This removes every tag from every page. Titles, categories, favorites, and completion status will not change.") },
            confirmButton = {
                Button(
                    onClick = {
                        showClearTagsConfirm = false
                        onClearAllTags()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                    modifier = Modifier.testTag("confirm_clear_tags_button")
                ) { Text("Clear tags") }
            },
            dismissButton = {
                TextButton(onClick = { showClearTagsConfirm = false }) { Text("Cancel") }
            }
        )
    }

    // Wipe confirmation dialog sequence
    if (wipeStep > 0) {
        Dialog(onDismissRequest = { onSetWipeStep(0) }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .testTag("wipe_modal_step_$wipeStep")
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (wipeStep) {
                        1 -> {
                            Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = AccentRed, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Erase App Data?", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("This will permanently delete every uploaded image, PDF, tag, and category from your device.", textAlign = TextAlign.Center, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .padding(8.dp)
                            ) {
                                Checkbox(
                                    checked = deleteCloud,
                                    onCheckedChange = onSetDeleteCloud,
                                    colors = CheckboxDefaults.colors(checkedColor = AccentRed)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("Also wipe Cloud Folder", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("Syncs deletion to Google Drive / iCloud", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedButton(onClick = { onSetWipeStep(0) }, modifier = Modifier.weight(1f)) { Text("Cancel") }
                                Button(onClick = { onSetWipeStep(2) }, colors = ButtonDefaults.buttonColors(containerColor = AccentRed), modifier = Modifier.weight(1f)) { Text("Continue") }
                            }
                        }

                        2 -> {
                            Icon(imageVector = Icons.Default.Security, contentDescription = null, tint = AccentRed, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Final Warning!", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = AccentRed)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("There is no undo. All images saved on this device will be permanently lost.", textAlign = TextAlign.Center, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(20.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedButton(onClick = { onSetWipeStep(0) }, modifier = Modifier.weight(1f)) { Text("Cancel") }
                                Button(onClick = { onSetWipeStep(3) }, colors = ButtonDefaults.buttonColors(containerColor = AccentRed), modifier = Modifier.weight(1f)) { Text("I Understand") }
                            }
                        }

                        3 -> {
                            if (deleteCloud) {
                                Icon(imageVector = Icons.Default.Cloud, contentDescription = null, tint = AccentRed, modifier = Modifier.size(48.dp))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Cloud Data Loss!", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = AccentRed)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("You chose to wipe the cloud folder. This permanently deletes your cloud backups.", textAlign = TextAlign.Center, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(48.dp))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Cloud Folder Safe", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("You did not select to wipe cloud folder. Cloud backups remain intact.", textAlign = TextAlign.Center, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(modifier = Modifier.height(20.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedButton(onClick = { onSetWipeStep(0) }, modifier = Modifier.weight(1f)) { Text("Cancel") }
                                Button(onClick = { onSetWipeStep(4) }, colors = ButtonDefaults.buttonColors(containerColor = AccentRed), modifier = Modifier.weight(1f)) { Text("Next") }
                            }
                        }

                        4 -> {
                            Text("Type \"DELETE\" to confirm", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = confirmDeleteText,
                                onValueChange = onSetConfirmText,
                                placeholder = { Text("DELETE") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().testTag("wipe_confirm_input")
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedButton(onClick = { onSetWipeStep(0) }, modifier = Modifier.weight(1f)) { Text("Cancel") }
                                Button(
                                    onClick = onWipeApp,
                                    enabled = confirmDeleteText == "DELETE",
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                                    modifier = Modifier.weight(1f).testTag("confirm_wipe_button")
                                ) {
                                    Text("Erase Now")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
