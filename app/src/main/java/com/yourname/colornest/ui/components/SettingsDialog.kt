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
                    Text("Get your API key", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        "1. Open Google AI Studio: aistudio.google.com/app/apikey\n2. Sign in with your Google account.\n3. Select Create API key (or copy an existing key).\n4. Paste it below and tap Test connection & connect.\n5. A successful test automatically enables Gemini for subject detection.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                        "Your key is stored encrypted on this device, not built into the APK. Gemini sends selected images to Google for analysis. On the free tier, Google may use submitted content to improve its products. Check current limits, pricing, and data-use terms before enabling it. Never share your key.",
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
