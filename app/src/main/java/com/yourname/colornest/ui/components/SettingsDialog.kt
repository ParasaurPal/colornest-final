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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
    aiEnabled: Boolean,
    geminiEnabled: Boolean,
    geminiApiKey: String,
    geminiConnectionStatus: String,
    isSeeding: Boolean,
    wipeStep: Int,
    deleteCloud: Boolean,
    confirmDeleteText: String,
    onToggleAi: () -> Unit,
    onGeminiApiKeyChange: (String) -> Unit,
    onConnectGemini: () -> Unit,
    onToggleGemini: () -> Unit,
    onSeedCategories: () -> Unit,
    onResetAiLearning: () -> Unit,
    onSetWipeStep: (Int) -> Unit,
    onSetDeleteCloud: (Boolean) -> Unit,
    onSetConfirmText: (String) -> Unit,
    onWipeApp: () -> Unit,
    onClose: () -> Unit
) {
    if (!isOpen) return
    val context = LocalContext.current
    var showGeminiKeyGuide by remember { mutableStateOf(false) }
    var showFullAppGuide by remember { mutableStateOf(false) }

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

                // Complete in-app user guide, including Gemini setup.
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
                            Text("Use the search box to find page titles or tags. Open a page's details/edit action to change its title, category, and tags. Use the heart on a page card to add or remove it from Favorites. Tags help you find pages by subject, style, or any labels you choose.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("5. AI subject detection", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("The AI Subject Detection button processes uncategorized images. Choose to process the whole untagged collection or select individual images. Make sure your categories exist first so the AI has suitable destinations. Detection can take time; wait for processing to finish before starting another batch. With Gemini off, ColorNest uses its on-device recognition and learned local patterns.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("6. Teach the AI with corrections", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("When a page is assigned to the wrong category, edit its details and move it to the correct category. ColorNest can use these corrections and visual similarities as local learning signals for future suggestions. Add clear, useful tags too. Learning is not guaranteed to identify every image correctly. Reset AI Learning in Settings clears the learned patterns; use it only if you want to start learning over.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("7. Gemini AI setup (optional)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Gemini analyzes selected images using Google's API. It is optional: leave it off to use on-device recognition. Open the Gemini section below and follow these steps:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("• Open Google AI Studio and sign in.\n• Find an existing API key or create one for a project. Some existing projects may need to be imported into AI Studio.\n• Check that the project and selected model are eligible for the Free Tier. Do not set up billing or link a paid billing account if you want to avoid charges.\n• Copy the key privately, return to ColorNest, paste it into the Gemini API key field, and tap Test connection & connect.\n• If the test succeeds, Gemini is enabled. If it fails, check the key, internet connection, project eligibility, and current AI Studio quota.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Button(
                                onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikeys"))) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary)
                            ) { Text("Open Google AI Studio") }
                            Text("Privacy and cost: the API key is stored encrypted on this device and is not bundled into the APK. Images processed with Gemini are sent to Google. Google says content submitted on the unpaid Free Tier may be used to improve its products, so do not send sensitive images. Free quotas, models, and eligibility can change. Monitor usage in AI Studio. Gemini is not unlimited or guaranteed to remain free.", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                            Text("8. Appearance and viewing", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Use the sun/moon control on the home screen to switch light and dark themes. Dark mode is intended for comfortable viewing and can display coloring line art with an inverted appearance where supported.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("9. Open or share a page", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Use the page card's available open/share actions to send an image to a compatible drawing, printing, or sharing app. The receiving app controls its own editing and print settings.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("10. Troubleshooting and data safety", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("If a page is missing, check All Pages, the selected category, and the search text. If AI does not run, confirm AI Categorization is on, images are in Untagged, and (for Gemini) the connection test succeeds. If Gemini reports quota or permission errors, check the project's plan and usage in AI Studio; do not add billing unless you intend to use a paid service. Keep separate backups of images you cannot replace. Erase All Images & Data permanently removes the app's local collection and settings after its confirmation steps; it cannot be undone.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // AI Categorization toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "AI Categorization",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Allow AI to assign folders automatically.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = aiEnabled,
                        onCheckedChange = { onToggleAi() },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = PurplePrimary),
                        modifier = Modifier.testTag("ai_toggle_switch")
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Optional Gemini image understanding provider
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Gemini AI Subject Detection", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                            Text("Optional cloud-based image understanding. Off uses free on-device recognition.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = geminiEnabled,
                            onCheckedChange = { onToggleGemini() },
                            enabled = geminiApiKey.isNotBlank(),
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = PurplePrimary),
                            modifier = Modifier.testTag("gemini_toggle_switch")
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Get a FREE Gemini API key", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        "You can start on Google's Free Tier without adding a payment method, if your account and selected model are eligible. The free tier has usage limits and is not unlimited.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { showGeminiKeyGuide = !showGeminiKeyGuide },
                        modifier = Modifier.fillMaxWidth().testTag("gemini_key_guide_toggle")
                    ) { Text(if (showGeminiKeyGuide) "Hide setup guide" else "Show step-by-step free key guide") }
                    if (showGeminiKeyGuide) {
                        Column(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("1. Open Google AI Studio", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("Tap below and sign in with your Google account.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Button(
                                onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikeys"))) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary)
                            ) { Text("Open Google AI Studio") }

                            Text("2. Find or create a project", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("New users may already have a default project and API key. If so, open API Keys and copy the key. Otherwise choose Create API key and follow the prompts. If you use an existing Google Cloud project, you may need to import it into AI Studio first.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                            Text("3. Stay on the Free Tier", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("Check the project's plan or tier in AI Studio and use a project marked Free. Do NOT tap Set up billing or link a paid billing account if you only want free usage. Free access depends on model, region, account eligibility, and current rate limits.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                            Text("4. Copy your API key", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("Use the copy control beside the key. Treat it like a password. Never post it in screenshots, messages, public repositories, or share it.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                            Text("5. Connect it to ColorNest", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("Return here, paste the key into the Gemini API key field, then tap Test connection & connect. ColorNest verifies the key and enables Gemini if the test succeeds.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                            Text("If Google says Free Tier unavailable or asks for billing or verification, your account or project may not be eligible. Creating another key in the same project will not remove a project-level restriction. Do not continue into billing if you want to avoid charges.", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = geminiApiKey,
                        onValueChange = onGeminiApiKeyChange,
                        modifier = Modifier.fillMaxWidth().testTag("gemini_api_key_input"),
                        label = { Text("Gemini API key") },
                        placeholder = { Text("Paste your key") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onConnectGemini,
                        enabled = geminiApiKey.isNotBlank() && !geminiConnectionStatus.startsWith("Testing"),
                        modifier = Modifier.fillMaxWidth().testTag("connect_gemini_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary)
                    ) { Text(if (geminiConnectionStatus.startsWith("Testing")) "Testing connection..." else "Test connection & connect") }
                    if (geminiConnectionStatus.isNotBlank()) {
                        Text(
                            geminiConnectionStatus,
                            fontSize = 12.sp,
                            color = if (geminiConnectionStatus.startsWith("Connected")) AccentGreen else MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag("gemini_connection_status")
                        )
                    }
                    Text(
                        "Your key is stored encrypted on this device, not built into the APK. Gemini sends selected images to Google for analysis. Google states that content submitted on the unpaid Free Tier may be used to improve its products; avoid sending sensitive images. Free quotas and eligible models can change. Check AI Studio's current plan and usage page.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                        onClick = onResetAiLearning,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("reset_ai_button")
                    ) {
                        Icon(imageVector = Icons.Outlined.Delete, contentDescription = null, tint = AccentRed, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Reset AI Learning", color = AccentRed, fontWeight = FontWeight.Bold)
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
