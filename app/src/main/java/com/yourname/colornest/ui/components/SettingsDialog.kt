package com.yourname.colornest.ui.components

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yourname.colornest.DriveConnectActivity
import com.yourname.colornest.ui.theme.AccentGreen
import com.yourname.colornest.ui.theme.AccentRed
import com.yourname.colornest.ui.theme.PurpleContainerLight
import com.yourname.colornest.ui.theme.PurplePrimary
import com.yourname.colornest.ui.viewmodel.SettingsExtraUiState
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsDialog(
    isOpen: Boolean,
    extra: SettingsExtraUiState,
    isSeeding: Boolean,
    wipeStep: Int,
    deleteCloud: Boolean,
    confirmDeleteText: String,
    wipeError: String? = null,
    onSeedCategories: () -> Unit,
    onToggleGalleryFullImages: (Boolean) -> Unit,
    onProcessExistingLibrary: () -> Unit,
    onOpenDuplicates: () -> Unit,
    onExportBackup: (Uri) -> Unit,
    onImportBackup: (Uri) -> Unit,
    onSyncDriveNow: () -> Unit,
    onOpenDriveBrowser: () -> Unit,
    onDisconnectDrive: () -> Unit,
    onSetDriveScanOnOpen: (Boolean) -> Unit,
    onRefreshDriveSettings: () -> Unit,
    onUpdateGeminiKey: (String) -> Unit,
    onSaveGeminiSettings: () -> Unit,
    onToggleAiAutoTag: (Boolean) -> Unit,
    onScanLibraryWithAi: () -> Unit,
    onConfirmApplyAiSuggestions: () -> Unit,
    onDismissAiSuggestions: () -> Unit,
    onClearAllTags: () -> Unit,
    onSetWipeStep: (Int) -> Unit,
    onSetDeleteCloud: (Boolean) -> Unit,
    onSetConfirmText: (String) -> Unit,
    onWipeApp: () -> Unit,
    onClose: () -> Unit
) {
    if (!isOpen) return
    val context = LocalContext.current
    var showFullAppGuide by remember { mutableStateOf(false) }
    var showGeminiKey by remember { mutableStateOf(false) }
    var showClearTagsConfirm by remember { mutableStateOf(false) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }

    val driveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        onRefreshDriveSettings()
    }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) onExportBackup(uri)
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) pendingRestoreUri = uri
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 20.dp)
                .testTag("settings_dialog")
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
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

                // 1. User Guide
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(PurpleContainerLight.copy(alpha = 0.35f))
                        .padding(16.dp)
                ) {
                    Text("ColorNest User Guide", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = PurplePrimary)
                    Text(
                        "Learn how to import, organize, tag, back up, and open pages in drawing apps.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { showFullAppGuide = !showFullAppGuide },
                        modifier = Modifier.fillMaxWidth().testTag("full_app_guide_toggle")
                    ) { Text(if (showFullAppGuide) "Close complete app guide" else "Open complete app guide") }
                    if (showFullAppGuide) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("1. Your collection", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("The home screen shows your coloring pages. Choose All Pages in the side menu to browse everything, or choose a category to narrow the collection. Untagged contains pages that do not yet have a category.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("2. Add images and PDF books", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Tap the + button to import from your device. Select one or more images, or choose a PDF. Keep 'Remove white paper' checked to create transparent PNG linework, or uncheck it to keep white backgrounds.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("3. Categories, tags, and sorting", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Open the side menu to browse your category tree or create subcategories. Use Browse tags on the home screen to select multiple tags with All/Any matching, create or rename tags, or sort your gallery.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("4. Search, favorites, and viewing", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Tap any page thumbnail to open the fullscreen viewer with pinch-to-zoom. Dark mode displays coloring line art inverted for comfortable viewing.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("5. Open from or send to other apps", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Tap Open or Share inside ColorNest to send a page to Clip Studio Paint or another drawing app as a new image. When opening files inside another app, choose ColorNest in Android Files or image picker.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("6. Backups & Google Drive", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Export a JSON backup anytime or connect Google Drive to browse files, import fast previews, and download full-resolution pages on demand.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 2. Setup Categories
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
                    Spacer(modifier = Modifier.height(10.dp))
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

                Spacer(modifier = Modifier.height(14.dp))

                // 3. Gallery display
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(PurpleContainerLight.copy(alpha = 0.35f))
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.PhotoLibrary, contentDescription = null, tint = PurplePrimary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Gallery", fontWeight = FontWeight.Bold, color = PurplePrimary, fontSize = 15.sp)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleGalleryFullImages(!extra.galleryFullImages) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text("Show full images in the gallery", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text(
                                "Uses sharper originals in the grid instead of compact thumbnails. Slower scrolling on large libraries.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = extra.galleryFullImages,
                            onCheckedChange = onToggleGalleryFullImages,
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = PurplePrimary),
                            modifier = Modifier.testTag("gallery_full_images_switch")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 4. This device & backups + Line art processing + Duplicate finder
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(PurpleContainerLight.copy(alpha = 0.35f))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Backup, contentDescription = null, tint = PurplePrimary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("This device & backups", fontWeight = FontWeight.Bold, color = PurplePrimary, fontSize = 15.sp)
                    }

                    val mbUsed = String.format(Locale.US, "%.1f MB", extra.storageUsedBytes / (1024.0 * 1024.0))
                    Text(
                        text = "Local storage used: $mbUsed",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                                exportBackupLauncher.launch("colornest-backup-$stamp.json")
                            },
                            enabled = !extra.backupBusy,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).testTag("export_backup_button")
                        ) {
                            Text("Export Backup", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        OutlinedButton(
                            onClick = { importBackupLauncher.launch(arrayOf("application/json", "*/*")) },
                            enabled = !extra.backupBusy,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).testTag("restore_backup_button")
                        ) {
                            Text("Restore Backup", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    if (extra.backupStatus.isNotBlank()) {
                        Text(
                            text = extra.backupStatus,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Line art batch processing
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.AutoFixHigh, contentDescription = null, tint = PurplePrimary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Black line art & transparent paper", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Text(
                            text = if (extra.unprocessedImageCount > 0) {
                                "${extra.unprocessedImageCount} local page(s) can be upgraded to clean black ink on transparent paper."
                            } else {
                                "All local pages already use clean black-ink transparent line art."
                            },
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (extra.isProcessingLibrary) {
                            val prog = if (extra.libraryProcessTotal > 0) {
                                extra.libraryProcessDone.toFloat() / extra.libraryProcessTotal.toFloat()
                            } else 0f
                            LinearProgressIndicator(
                                progress = { prog },
                                color = PurplePrimary,
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                            )
                        }

                        if (extra.libraryProcessStatus.isNotBlank()) {
                            Text(
                                text = extra.libraryProcessStatus,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Button(
                            onClick = onProcessExistingLibrary,
                            enabled = !extra.isProcessingLibrary && extra.unprocessedImageCount > 0,
                            colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().testTag("process_library_button")
                        ) {
                            Text(
                                text = if (extra.isProcessingLibrary) "Converting…" else "Convert Remaining Pages (${extra.unprocessedImageCount})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Find duplicates
                    OutlinedButton(
                        onClick = onOpenDuplicates,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("find_duplicates_button")
                    ) {
                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Find Duplicate Pages", fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 5. Google Drive
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(PurpleContainerLight.copy(alpha = 0.35f))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Cloud, contentDescription = null, tint = PurplePrimary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Google Drive", fontWeight = FontWeight.Bold, color = PurplePrimary, fontSize = 15.sp)
                    }

                    if (extra.driveConnected) {
                        Text(
                            text = "Connected: ${extra.driveAccount ?: "Google account"}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        extra.driveLastSync?.let { ts ->
                            val formatted = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(ts))
                            Text(
                                text = "Last synced: $formatted",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSetDriveScanOnOpen(!extra.driveScanOnOpen) }
                        ) {
                            Checkbox(
                                checked = extra.driveScanOnOpen,
                                onCheckedChange = onSetDriveScanOnOpen,
                                colors = CheckboxDefaults.colors(checkedColor = PurplePrimary)
                            )
                            Text(
                                text = "Import new Drive previews when app opens",
                                fontSize = 12.sp
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onSyncDriveNow,
                                enabled = !extra.isSyncingDrive,
                                colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).testTag("drive_import_previews_button")
                            ) {
                                Text(
                                    text = if (extra.isSyncingDrive) "Scanning…" else "Import Previews",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            OutlinedButton(
                                onClick = onOpenDriveBrowser,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).testTag("browse_drive_button")
                            ) {
                                Icon(imageVector = Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Browse Drive", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    driveLauncher.launch(
                                        Intent(context, DriveConnectActivity::class.java)
                                            .putExtra(DriveConnectActivity.EXTRA_PICK_ACCOUNT, true)
                                    )
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Switch Account", fontSize = 11.sp)
                            }
                            OutlinedButton(
                                onClick = onDisconnectDrive,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).testTag("disconnect_drive_button")
                            ) {
                                Text("Disconnect", fontSize = 11.sp, color = AccentRed)
                            }
                        }
                    } else {
                        Text(
                            "Keep your coloring library synced in Google Drive. Connect your account to import fast previews, browse PDF Inbox, and download full-resolution pages.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = {
                                driveLauncher.launch(Intent(context, DriveConnectActivity::class.java))
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().testTag("connect_drive_button")
                        ) { Text("Connect / Sync Google Drive", fontWeight = FontWeight.Bold) }
                        OutlinedButton(
                            onClick = {
                                driveLauncher.launch(
                                    Intent(context, DriveConnectActivity::class.java)
                                        .putExtra(DriveConnectActivity.EXTRA_PICK_ACCOUNT, true)
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Choose a different Google account") }
                    }

                    if (extra.driveStatus.isNotBlank()) {
                        Text(
                            text = extra.driveStatus,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 6. AI Subject Tagging
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(PurpleContainerLight.copy(alpha = 0.35f))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, tint = PurplePrimary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("AI Subject Tagging & Category Sorting (Optional)", fontWeight = FontWeight.Bold, color = PurplePrimary, fontSize = 15.sp)
                    }
                    Text(
                        "Gemini identifies subjects, adds descriptive tags, and assigns pages to the best matching category already in your category tree. It learns from category corrections you make to AI-tagged pages. Suggestions are reviewed before they are applied.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = extra.geminiApiKey,
                        onValueChange = onUpdateGeminiKey,
                        label = { Text("Gemini API Key") },
                        singleLine = true,
                        visualTransformation = if (showGeminiKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            TextButton(onClick = { showGeminiKey = !showGeminiKey }) {
                                Text(if (showGeminiKey) "Hide" else "Show", fontSize = 11.sp)
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("gemini_api_key_input")
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = extra.aiAutoTagUploads,
                            onCheckedChange = onToggleAiAutoTag,
                            colors = CheckboxDefaults.colors(checkedColor = PurplePrimary)
                        )
                        Text("Automatically tag & sort new uploads", fontSize = 12.sp)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onSaveGeminiSettings,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).testTag("save_gemini_settings_button")
                        ) {
                            Text("Save Key", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Button(
                            onClick = onScanLibraryWithAi,
                            enabled = !extra.isAiTagging && extra.geminiApiKey.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).testTag("scan_ai_tags_button")
                        ) {
                            Text(
                                text = if (extra.isAiTagging) "Scanning…" else "Scan Library",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (extra.isAiTagging) {
                        val prog = if (extra.aiTagTotal > 0) extra.aiTagDone.toFloat() / extra.aiTagTotal.toFloat() else 0f
                        LinearProgressIndicator(
                            progress = { prog },
                            color = PurplePrimary,
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                        )
                    }

                    if (extra.aiTagStatus.isNotBlank()) {
                        Text(
                            text = extra.aiTagStatus,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 7. Danger Zone
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, AccentRed.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Security, contentDescription = null, tint = AccentRed, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Danger Zone", fontWeight = FontWeight.Bold, color = AccentRed, fontSize = 15.sp)
                    }

                    OutlinedButton(
                        onClick = { showClearTagsConfirm = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("clear_all_tags_button")
                    ) {
                        Text("Clear All Tags", color = AccentRed, fontWeight = FontWeight.SemiBold)
                    }

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

    // Confirm restore backup
    if (pendingRestoreUri != null) {
        AlertDialog(
            onDismissRequest = { pendingRestoreUri = null },
            title = { Text("Restore Backup?", fontWeight = FontWeight.Bold) },
            text = {
                Text("This will replace your current local pages, categories, and tags with the contents of this backup file.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        val uri = pendingRestoreUri
                        pendingRestoreUri = null
                        if (uri != null) onImportBackup(uri)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary)
                ) { Text("Restore") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestoreUri = null }) { Text("Cancel") }
            }
        )
    }

    // Confirm clear all tags
    if (showClearTagsConfirm) {
        AlertDialog(
            onDismissRequest = { showClearTagsConfirm = false },
            title = { Text("Clear All Tags?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Removes every tag from your tag catalog and from all pages. Your images and categories will not be deleted.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearTagsConfirm = false
                        onClearAllTags()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed)
                ) { Text("Clear Tags") }
            },
            dismissButton = {
                TextButton(onClick = { showClearTagsConfirm = false }) { Text("Cancel") }
            }
        )
    }

    // Review AI suggestions dialog
    val suggestions = extra.pendingAiSuggestions
    if (!suggestions.isNullOrEmpty()) {
        AlertDialog(
            onDismissRequest = onDismissAiSuggestions,
            title = {
                Text(
                    text = "Review AI Tag & Category Suggestions (${suggestions.size})",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Review the suggested category and tags below. Pages are only assigned to categories that already exist:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(suggestions, key = { it.page.id }) { item ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .padding(10.dp)
                            ) {
                                Text(
                                    text = item.page.title.ifBlank { "Untitled page" },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Category: ${item.categoryPath}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = PurplePrimary
                                )
                                Text(item.reason, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = if (item.tags.isEmpty()) "No new tags" else "Tags: ${item.tags.joinToString(", ")}",
                                    fontSize = 12.sp,
                                    color = PurplePrimary
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = onConfirmApplyAiSuggestions,
                    colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                    modifier = Modifier.testTag("apply_ai_suggestions_button")
                ) {
                    Text("Apply Tags & Sort", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismissAiSuggestions,
                    modifier = Modifier.testTag("dismiss_ai_suggestions_button")
                ) {
                    Text("Discard")
                }
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
                                    Text("Also clear Google Drive folder", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("Moves the ColorNest backup folder to Google Drive Trash.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                Text("The ColorNest backup folder will be moved to Google Drive Trash. You can recover it from Drive Trash while it remains there.", textAlign = TextAlign.Center, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            if (wipeError != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(wipeError, color = AccentRed, fontSize = 12.sp, textAlign = TextAlign.Center)
                            }
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
