package com.yourname.colornest.ui.components

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.yourname.colornest.data.model.Page
import com.yourname.colornest.ui.theme.AccentGreen
import com.yourname.colornest.ui.theme.AccentPink
import com.yourname.colornest.ui.theme.PurplePrimary
import kotlinx.coroutines.launch
import java.io.File

private const val FILE_PROVIDER_AUTHORITY = "com.yourname.colornest.fileprovider"

private val CLIP_STUDIO_PACKAGES = listOf(
    "jp.co.celsys.clipstudiopaint.googleplay",
    "jp.co.celsys.clipstudiopaint.galaxystore",
    "jp.co.celsys.clipstudiopaint"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PageCard(
    page: Page,
    isDarkMode: Boolean,
    galleryFullImages: Boolean = false,
    onViewPage: () -> Unit = {},
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
    onEditDetails: () -> Unit,
    selectedTag: String? = null,
    activeTags: Set<String> = emptySet(),
    onTagClick: (String) -> Unit = {},
    onDownloadDriveCache: (suspend (Page) -> File?)? = null,
    onDownloadFullPage: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showShareDialog by remember { mutableStateOf(false) }

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

    val displayFile = remember(page.imageUrl, page.thumbnailUrl, galleryFullImages) {
        val full = page.imageUrl.takeIf { it.isNotBlank() }?.let { File(it) }?.takeIf { it.exists() }
        val thumb = page.thumbnailUrl?.takeIf { it.isNotBlank() }?.let { File(it) }?.takeIf { it.exists() }
        if (galleryFullImages) {
            full ?: thumb
        } else {
            thumb ?: full
        }
    }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                RoundedCornerShape(24.dp)
            )
            .testTag("page_card_${page.id}")
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // Image Box (click opens fullscreen in-app viewer)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (isDarkMode) Color(0xFF18181B) else Color(0xFFF4F4F5))
                    .clickable { onViewPage() }
                    .testTag("page_image_${page.id}")
            ) {
                if (displayFile != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(displayFile)
                            .crossfade(true)
                            .build(),
                        contentDescription = page.title.ifBlank { "Coloring page" },
                        contentScale = ContentScale.Crop,
                        colorFilter = if (isDarkMode) ColorFilter.colorMatrix(invertMatrix) else null,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Cloud,
                            contentDescription = "Drive preview",
                            tint = PurplePrimary.copy(alpha = 0.5f),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                // Badges on top-start (Preview / Completed)
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (page.isPreviewOnly) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(PurplePrimary.copy(alpha = 0.9f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Preview",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                    if (page.isCompleted) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(AccentGreen.copy(alpha = 0.9f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Completed",
                                    tint = Color.White,
                                    modifier = Modifier.size(10.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = "Done",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                // Favorite Button
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .testTag("favorite_button_${page.id}")
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (page.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (page.isFavorite) AccentPink else Color.Gray,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Edit Details Overlay button for untagged imports
                if (page.isUncategorized) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.55f))
                            .clickable { onEditDetails() }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Edit Details",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Title, Edit, and Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = page.title.ifBlank { "Untitled page" },
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onEditDetails() }
                        .padding(end = 4.dp)
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onEditDetails,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("edit_button_${page.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Page Details",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("delete_button_${page.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = "Delete Page",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Tags
            if (page.tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    page.tags.forEach { tag ->
                        val isSelectedTag = (selectedTag != null && tag.trim().equals(selectedTag, ignoreCase = true)) ||
                            activeTags.any { it.equals(tag.trim(), ignoreCase = true) }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (isSelectedTag) PurplePrimary.copy(alpha = if (isDarkMode) 0.3f else 0.14f)
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable { onTagClick(tag) }
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = tag,
                                fontSize = 10.sp,
                                fontWeight = if (isSelectedTag) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelectedTag) PurplePrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Open / View & Share buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(PurplePrimary.copy(alpha = if (isDarkMode) 0.25f else 0.1f))
                        .clickable {
                            if (page.isPreviewOnly) {
                                onViewPage()
                            } else {
                                openAsNewImage(context, page.imageUrl, page.title)
                            }
                        }
                        .padding(vertical = 10.dp, horizontal = 8.dp)
                        .testTag("open_page_button_${page.id}"),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (page.isPreviewOnly) Icons.Default.CloudDownload else Icons.Default.OpenInNew,
                        contentDescription = null,
                        tint = PurplePrimary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (page.isPreviewOnly) "Preview" else "Open",
                        color = PurplePrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { showShareDialog = true }
                        .padding(vertical = 10.dp, horizontal = 10.dp)
                        .testTag("share_page_button_${page.id}"),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share Page",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Share",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }
        }
    }

    if (showShareDialog) {
        SharePageDialog(
            page = page,
            onDownloadDriveCache = onDownloadDriveCache,
            onDownloadFullPage = onDownloadFullPage,
            onDismiss = { showShareDialog = false }
        )
    }
}

@Composable
fun SharePageDialog(
    page: Page,
    onDownloadDriveCache: (suspend (Page) -> File?)? = null,
    onDownloadFullPage: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isFetchingDriveFile by remember { mutableStateOf(false) }
    var cachedFileForSave by remember { mutableStateOf<File?>(null) }

    val localFile = remember(page.imageUrl) {
        page.imageUrl.takeIf { it.isNotBlank() }?.let { File(it) }?.takeIf { it.exists() }
    }
    val initialPath = localFile?.absolutePath ?: "page.png"
    val mimeType = remember(initialPath) { resolveImageMimeType(initialPath) }
    val suggestedName = remember(page.title, initialPath) {
        val ext = if (mimeType == "image/png") "png" else "jpg"
        val cleanBase = page.title.ifBlank { "ColorNest_Page_${page.id}" }
            .replace(Regex("[^A-Za-z0-9._-]+"), "_")
            .trim('_')
            .ifBlank { "ColorNest_Page_${page.id}" }
        "$cleanBase.$ext"
    }

    var launchCspAfterSave by remember { mutableStateOf(false) }

    val saveDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(mimeType)
    ) { destinationUri ->
        val sourcePath = cachedFileForSave?.absolutePath ?: page.imageUrl
        if (destinationUri != null) {
            val ok = copyLocalFileToUri(context, sourcePath, destinationUri)
            if (ok) {
                if (launchCspAfterSave) {
                    launchCspAfterSave = false
                    Toast.makeText(
                        context,
                        "Saved image. In Clip Studio Paint, open it via File > Create from image.",
                        Toast.LENGTH_LONG
                    ).show()
                    launchClipStudioPaintHome(context)
                    onDismiss()
                } else {
                    Toast.makeText(context, "Image saved.", Toast.LENGTH_SHORT).show()
                    onDismiss()
                }
            } else {
                launchCspAfterSave = false
                Toast.makeText(context, "Could not save image.", Toast.LENGTH_SHORT).show()
            }
        } else {
            launchCspAfterSave = false
        }
    }

    fun withReadyImageFile(action: (File) -> Unit) {
        if (localFile != null) {
            action(localFile)
            return
        }
        val fetcher = onDownloadDriveCache
        if (fetcher == null) {
            Toast.makeText(context, "Download the full image first from Preview.", Toast.LENGTH_LONG).show()
            return
        }
        scope.launch {
            isFetchingDriveFile = true
            val file = runCatching { fetcher(page) }.getOrNull()
            isFetchingDriveFile = false
            if (file != null && file.exists()) {
                cachedFileForSave = file
                action(file)
            } else {
                Toast.makeText(
                    context,
                    "Could not fetch file from Google Drive. Try downloading the full page in the viewer.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isFetchingDriveFile) onDismiss() },
        title = {
            Text(
                text = "Open or Share Page",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Android's standard Share action (ACTION_SEND) imports images as a new layer in Clip Studio Paint. Use the new-image options below to open it as its own canvas.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (page.isPreviewOnly && localFile == null && onDownloadFullPage != null) {
                    OutlinedButton(
                        onClick = {
                            onDownloadFullPage()
                            onDismiss()
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("share_download_full_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Download Full Page to Device", fontWeight = FontWeight.SemiBold)
                    }
                }

                if (isFetchingDriveFile) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    ) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Fetching full image from Drive…", fontSize = 12.sp)
                    }
                }

                Button(
                    onClick = {
                        withReadyImageFile { file ->
                            val launched = openInClipStudioAsNewImage(
                                context = context,
                                imagePath = file.absolutePath,
                                title = page.title,
                                suggestedName = suggestedName,
                                onNeedLegacySafSave = {
                                    launchCspAfterSave = true
                                    saveDocumentLauncher.launch(suggestedName)
                                }
                            )
                            if (launched) onDismiss()
                        }
                    },
                    enabled = !isFetchingDriveFile,
                    colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("share_csp_new_image_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Brush,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Clip Studio Paint (New Image)", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = {
                        withReadyImageFile { file ->
                            openAsNewImage(context, file.absolutePath, page.title)
                            onDismiss()
                        }
                    },
                    enabled = !isFetchingDriveFile,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("share_view_new_image_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open as New Image (Chooser)")
                }

                OutlinedButton(
                    onClick = {
                        withReadyImageFile { file ->
                            openForEditInApp(context, file.absolutePath, page.title)
                            onDismiss()
                        }
                    },
                    enabled = !isFetchingDriveFile,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("share_edit_in_app_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Edit in Drawing App")
                }

                OutlinedButton(
                    onClick = {
                        withReadyImageFile { file ->
                            val resolvedMime = resolveImageMimeType(file.absolutePath)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                val saved = saveToPicturesGallery(context, file.absolutePath, suggestedName, resolvedMime)
                                Toast.makeText(
                                    context,
                                    if (saved != null) "Saved to Pictures/ColorNest ($suggestedName)"
                                    else "Could not save copy to Pictures.",
                                    Toast.LENGTH_LONG
                                ).show()
                                if (saved != null) onDismiss()
                            } else {
                                launchCspAfterSave = false
                                saveDocumentLauncher.launch(suggestedName)
                            }
                        }
                    },
                    enabled = !isFetchingDriveFile,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("save_to_pictures_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.SaveAlt,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save to Pictures/ColorNest")
                }

                OutlinedButton(
                    onClick = {
                        withReadyImageFile { file ->
                            shareViaSystemSheet(context, file.absolutePath, page.title)
                            onDismiss()
                        }
                    },
                    enabled = !isFetchingDriveFile,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("share_system_sheet_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("System Share Sheet (Layer / File)")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, enabled = !isFetchingDriveFile) {
                Text("Close")
            }
        }
    )
}

private fun resolveImageMimeType(filePath: String): String {
    return if (filePath.endsWith(".png", ignoreCase = true)) "image/png" else "image/jpeg"
}

private fun buildPageUri(context: Context, filePath: String): Pair<Uri, String>? {
    val file = File(filePath)
    if (!file.exists()) return null
    val uri = FileProvider.getUriForFile(context, FILE_PROVIDER_AUTHORITY, file)
    return uri to resolveImageMimeType(filePath)
}

private fun buildViewOrEditIntent(
    context: Context,
    action: String,
    uri: Uri,
    mimeType: String,
    label: String,
    targetPackage: String? = null
): Intent {
    return Intent(action).apply {
        setDataAndType(uri, mimeType)
        clipData = ClipData.newUri(context.contentResolver, label, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        if (targetPackage != null) {
            setPackage(targetPackage)
            context.grantUriPermission(targetPackage, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}

private fun findInstalledClipStudioPackage(context: Context): String? {
    val pm = context.packageManager
    for (pkg in CLIP_STUDIO_PACKAGES) {
        val installed = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(pkg, 0)
            }
        }.isSuccess
        if (installed) return pkg
    }
    return null
}

private fun canResolveIntent(context: Context, intent: Intent): Boolean {
    val pm = context.packageManager
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        pm.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())) != null
    } else {
        @Suppress("DEPRECATION")
        pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null
    }
}

private fun launchClipStudioPaintHome(context: Context): Boolean {
    val pkg = findInstalledClipStudioPackage(context) ?: return false
    val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false
    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return runCatching {
        context.startActivity(launchIntent)
        true
    }.getOrDefault(false)
}

private fun openInClipStudioAsNewImage(
    context: Context,
    imagePath: String,
    title: String,
    suggestedName: String,
    onNeedLegacySafSave: () -> Unit
): Boolean {
    val pkg = findInstalledClipStudioPackage(context)
    if (pkg == null) {
        Toast.makeText(
            context,
            "Clip Studio Paint is not installed on this device.",
            Toast.LENGTH_LONG
        ).show()
        return false
    }

    val (uri, mimeType) = buildPageUri(context, imagePath) ?: run {
        Toast.makeText(context, "Image file not found.", Toast.LENGTH_SHORT).show()
        return false
    }
    val label = title.ifBlank { "Coloring Page" }

    val viewIntent = buildViewOrEditIntent(context, Intent.ACTION_VIEW, uri, mimeType, label, pkg)
    if (canResolveIntent(context, viewIntent)) {
        runCatching {
            context.startActivity(viewIntent)
            return true
        }
    }

    val editIntent = buildViewOrEditIntent(context, Intent.ACTION_EDIT, uri, mimeType, label, pkg)
    if (canResolveIntent(context, editIntent)) {
        runCatching {
            context.startActivity(editIntent)
            return true
        }
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val savedUri = saveToPicturesGallery(context, imagePath, suggestedName, mimeType)
        if (savedUri != null) {
            Toast.makeText(
                context,
                "Saved to Pictures/ColorNest ($suggestedName). Open via Create from image in Clip Studio Paint.",
                Toast.LENGTH_LONG
            ).show()
            launchClipStudioPaintHome(context)
            return true
        }
        Toast.makeText(context, "Could not save image for Clip Studio Paint.", Toast.LENGTH_SHORT).show()
        return false
    } else {
        onNeedLegacySafSave()
        return false
    }
}

private fun openAsNewImage(context: Context, imagePath: String, title: String) {
    val (uri, mimeType) = buildPageUri(context, imagePath) ?: return
    val label = title.ifBlank { "Coloring Page" }

    val viewIntent = buildViewOrEditIntent(context, Intent.ACTION_VIEW, uri, mimeType, label)
    if (canResolveIntent(context, viewIntent)) {
        runCatching {
            val chooser = Intent.createChooser(viewIntent, "Open as New Image")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
            return
        }
    }

    val editIntent = buildViewOrEditIntent(context, Intent.ACTION_EDIT, uri, mimeType, label)
    if (canResolveIntent(context, editIntent)) {
        runCatching {
            val chooser = Intent.createChooser(editIntent, "Open as New Image")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
            return
        }
    }

    shareViaSystemSheet(context, imagePath, title)
}

private fun openForEditInApp(context: Context, imagePath: String, title: String) {
    val (uri, mimeType) = buildPageUri(context, imagePath) ?: return
    val label = title.ifBlank { "Coloring Page" }
    val editIntent = buildViewOrEditIntent(context, Intent.ACTION_EDIT, uri, mimeType, label)
    if (canResolveIntent(context, editIntent)) {
        runCatching {
            val chooser = Intent.createChooser(editIntent, "Edit in Drawing App")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
            return
        }
    }
    Toast.makeText(
        context,
        "No app found supporting Edit intent for this image. Try Open as New Image or Save to Pictures.",
        Toast.LENGTH_LONG
    ).show()
}

private fun shareViaSystemSheet(context: Context, imagePath: String, title: String) {
    val (uri, mimeType) = buildPageUri(context, imagePath) ?: return
    val label = title.ifBlank { "Coloring Page" }
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TITLE, label)
        clipData = ClipData.newUri(context.contentResolver, label, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching {
        val chooser = Intent.createChooser(sendIntent, "Share Coloring Page")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}

private fun saveToPicturesGallery(
    context: Context,
    sourcePath: String,
    displayName: String,
    mimeType: String
): Uri? {
    val sourceFile = File(sourcePath)
    if (!sourceFile.exists()) return null
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
        put(MediaStore.Images.Media.MIME_TYPE, mimeType)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ColorNest")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    }
    val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    val itemUri = resolver.insert(collection, values) ?: return null
    return try {
        resolver.openOutputStream(itemUri)?.use { output ->
            sourceFile.inputStream().use { input ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Unable to open MediaStore output stream")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val done = ContentValues().apply {
                put(MediaStore.Images.Media.IS_PENDING, 0)
            }
            resolver.update(itemUri, done, null, null)
        }
        itemUri
    } catch (_: Exception) {
        runCatching { resolver.delete(itemUri, null, null) }
        null
    }
}

private fun copyLocalFileToUri(context: Context, sourcePath: String, destinationUri: Uri): Boolean {
    val sourceFile = File(sourcePath)
    if (!sourceFile.exists()) return false
    return runCatching {
        context.contentResolver.openOutputStream(destinationUri)?.use { output ->
            sourceFile.inputStream().use { input ->
                input.copyTo(output)
            }
        } ?: return false
        true
    }.getOrDefault(false)
}
