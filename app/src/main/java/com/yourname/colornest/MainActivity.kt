package com.yourname.colornest

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.yourname.colornest.ui.screens.HomeScreen
import com.yourname.colornest.ui.theme.ColorNestTheme
import com.yourname.colornest.ui.viewmodel.ColorNestViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: ColorNestViewModel by viewModels()
    private val incomingSharedImage = mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applyImmersiveFullscreen()

        if (savedInstanceState == null) {
            handleIncomingShareIntent(intent)
        }

        setContent {
            val uiState by viewModel.uiState.collectAsState()
            ColorNestTheme(darkTheme = uiState.isDarkMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    androidx.compose.foundation.layout.Box {
                        HomeScreen(viewModel = viewModel)
                        incomingSharedImage.value?.let { sharedUri ->
                            AlertDialog(
                                onDismissRequest = { incomingSharedImage.value = null },
                                title = { Text("Where should this image go?", fontWeight = FontWeight.Bold) },
                                text = { Text("Choose whether this is a page you still want to color, finished artwork for your Showcase, or an original image to send directly to a coloring app. Saving either option makes a separate copy in ColorNest and leaves the original untouched.") },
                                confirmButton = {
                                    Column(
                                        modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                viewModel.importSharedImage(sharedUri, false)
                                                incomingSharedImage.value = null
                                            },
                                            modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                                            colors = ButtonDefaults.buttonColors(containerColor = com.yourname.colornest.ui.theme.PurplePrimary)
                                        ) { Text("Add as coloring page") }
                                        Button(
                                            onClick = {
                                                viewModel.importSharedImage(sharedUri, true)
                                                incomingSharedImage.value = null
                                            },
                                            modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                                            colors = ButtonDefaults.buttonColors(containerColor = com.yourname.colornest.ui.theme.PurplePrimary)
                                        ) { Text("Add finished page to Showcase") }
                                        TextButton(
                                            onClick = {
                                                shareOriginalForColoring(sharedUri)
                                                incomingSharedImage.value = null
                                            },
                                            modifier = androidx.compose.ui.Modifier.fillMaxWidth()
                                        ) { Text("Share original for coloring") }
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { incomingSharedImage.value = null }) { Text("Cancel") }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun applyImmersiveFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyImmersiveFullscreen()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingShareIntent(intent)
    }

    private fun handleIncomingShareIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action ?: return
        if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) return

        val uris = LinkedHashSet<Uri>()
        if (action == Intent.ACTION_SEND) {
            getParcelableUri(intent, Intent.EXTRA_STREAM)?.let { uris.add(it) }
        } else {
            getParcelableUriList(intent, Intent.EXTRA_STREAM).forEach { uris.add(it) }
        }

        val clipData = intent.clipData
        if (clipData != null) {
            for (i in 0 until clipData.itemCount) {
                clipData.getItemAt(i).uri?.let { uris.add(it) }
            }
        }

        val singleImageUri = uris.firstOrNull()?.takeIf { uris.size == 1 }
        val isSingleImage = singleImageUri != null && (
            intent.type?.startsWith("image/") == true ||
                contentResolver.getType(singleImageUri)?.startsWith("image/") == true
            )
        if (isSingleImage) {
            incomingSharedImage.value = singleImageUri
        } else if (uris.isNotEmpty()) {
            viewModel.importSharedUris(uris.toList())
        }
    }

    private fun shareOriginalForColoring(uri: Uri) {
        val mimeType = contentResolver.getType(uri) ?: "image/*"
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = android.content.ClipData.newUri(contentResolver, "Original coloring image", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(sendIntent, "Choose a coloring app"))
    }

    @Suppress("DEPRECATION")
    private fun getParcelableUri(intent: Intent, key: String): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(key, Uri::class.java)
        } else {
            intent.getParcelableExtra(key)
        }
    }

    @Suppress("DEPRECATION")
    private fun getParcelableUriList(intent: Intent, key: String): List<Uri> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(key, Uri::class.java).orEmpty()
        } else {
            intent.getParcelableArrayListExtra<Uri>(key).orEmpty()
        }
    }
}
