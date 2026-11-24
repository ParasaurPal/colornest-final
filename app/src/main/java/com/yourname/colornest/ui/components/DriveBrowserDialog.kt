package com.yourname.colornest.ui.components

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.yourname.colornest.data.drive.DriveBrowserEntry
import com.yourname.colornest.data.drive.DriveSyncManager
import com.yourname.colornest.ui.theme.AccentGreen
import com.yourname.colornest.ui.theme.PurplePrimary
import com.yourname.colornest.ui.viewmodel.DriveBrowserUiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DriveBrowserDialog(
    state: DriveBrowserUiState,
    isDarkMode: Boolean,
    onRefresh: () -> Unit,
    onSelectEntry: (DriveBrowserEntry?) -> Unit,
    onDownloadEntry: (DriveBrowserEntry) -> Unit,
    onClose: () -> Unit
) {
    if (!state.isOpen) return
    val context = LocalContext.current

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f)
                .testTag("drive_browser_dialog")
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        if (state.selectedEntry != null) {
                            IconButton(
                                onClick = { onSelectEntry(null) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("drive_browser_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back to list"
                                )
                            }
                        }
                        Column {
                            Text(
                                text = state.selectedEntry?.name ?: "Browse Google Drive",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (state.selectedEntry != null) "Drive file details" else "${state.files.size} files in ColorNest Drive",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (state.selectedEntry == null) {
                            IconButton(
                                onClick = onRefresh,
                                enabled = !state.isLoading,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("drive_browser_refresh_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Refresh Drive files",
                                    tint = PurplePrimary
                                )
                            }
                        }
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("drive_browser_close_button")
                        ) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                }

                if (state.status.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(PurplePrimary.copy(alpha = 0.1f))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = state.status,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = PurplePrimary
                        )
                    }
                }

                when {
                    state.isLoading -> {
                        Box(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = PurplePrimary)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Listing ColorNest Drive files…",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    state.notConnected -> {
                        Box(
                            modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Google Drive is not connected yet. Connect your Google account in Settings first.",
                                textAlign = TextAlign.Center,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    state.error != null -> {
                        Box(
                            modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = state.error,
                                    textAlign = TextAlign.Center,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedButton(onClick = onRefresh) {
                                    Text("Retry")
                                }
                            }
                        }
                    }
                    state.selectedEntry != null -> {
                        val entry = state.selectedEntry
                        val largeThumb = DriveSyncManager.largeThumbnailUrl(entry.thumbnailLink)
                        val isDownloading = state.downloadingId == entry.id
                        val isSavedLocally = entry.inLibrary && !entry.libraryPreviewOnly

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(320.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (isDarkMode) Color(0xFF18181B) else Color(0xFFF4F4F5)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!largeThumb.isNullOrBlank()) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(largeThumb)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = entry.name,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize().padding(8.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Description,
                                        contentDescription = null,
                                        tint = PurplePrimary,
                                        modifier = Modifier.size(64.dp)
                                    )
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(PurplePrimary.copy(alpha = 0.14f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = if (entry.isPdf) "PDF Book" else "Image",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PurplePrimary
                                    )
                                }

                                val statusText = when {
                                    entry.importedPdf || isSavedLocally -> "Saved on device"
                                    entry.inLibrary && entry.libraryPreviewOnly -> "Preview in library"
                                    else -> "Only on Google Drive"
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isSavedLocally || entry.importedPdf) AccentGreen.copy(alpha = 0.15f)
                                            else MaterialTheme.colorScheme.surfaceVariant
                                        )
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = statusText,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isSavedLocally || entry.importedPdf) AccentGreen else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            val metaTags = entry.metadata?.tags.orEmpty()
                            if (metaTags.isNotEmpty()) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    metaTags.forEach { tag ->
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(text = tag, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }

                            Button(
                                onClick = { onDownloadEntry(entry) },
                                enabled = !isDownloading && state.downloadingId == null,
                                colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("drive_browser_download_button")
                            ) {
                                if (isDownloading) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Downloading…", fontWeight = FontWeight.Bold)
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.CloudDownload,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = when {
                                            entry.isPdf -> "Download & Import PDF Pages"
                                            isSavedLocally -> "Re-download Image"
                                            else -> "Download to Library"
                                        },
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                    state.files.isEmpty() -> {
                        Box(
                            modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No images or PDFs found in your ColorNest Google Drive folder yet.",
                                textAlign = TextAlign.Center,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    else -> {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 148.dp),
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .testTag("drive_browser_grid")
                        ) {
                            items(state.files, key = { it.id }) { entry ->
                                DriveBrowserEntryCard(
                                    entry = entry,
                                    isDarkMode = isDarkMode,
                                    isDownloading = state.downloadingId == entry.id,
                                    onClick = { onSelectEntry(entry) },
                                    onDownload = { onDownloadEntry(entry) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DriveBrowserEntryCard(
    entry: DriveBrowserEntry,
    isDarkMode: Boolean,
    isDownloading: Boolean,
    onClick: () -> Unit,
    onDownload: () -> Unit
) {
    val context = LocalContext.current
    val isSaved = entry.importedPdf || (entry.inLibrary && !entry.libraryPreviewOnly)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                RoundedCornerShape(16.dp)
            )
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("drive_entry_card_${entry.id}")
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(124.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isDarkMode) Color(0xFF18181B) else Color(0xFFF4F4F5)),
                contentAlignment = Alignment.Center
            ) {
                if (!entry.thumbnailLink.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(entry.thumbnailLink)
                            .crossfade(true)
                            .build(),
                        contentDescription = entry.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = PurplePrimary,
                        modifier = Modifier.size(36.dp)
                    )
                }

                if (entry.isPdf) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(PurplePrimary)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("PDF", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                if (isSaved) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Saved on device",
                        tint = AccentGreen,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = entry.metadata?.title?.takeIf { it.isNotBlank() } ?: entry.name,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))

            OutlinedButton(
                onClick = onDownload,
                enabled = !isDownloading,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                modifier = Modifier.fillMaxWidth().height(36.dp)
            ) {
                if (isDownloading) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                } else {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isSaved) "Saved" else "Download",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
