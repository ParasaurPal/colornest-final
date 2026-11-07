package com.yourname.colornest.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.yourname.colornest.ui.theme.AccentGreen
import com.yourname.colornest.ui.theme.AccentRed
import com.yourname.colornest.ui.theme.PurplePrimary

@Composable
fun OnboardingDialog(
    show: Boolean,
    onComplete: () -> Unit
) {
    if (!show) return

    val context = LocalContext.current
    var step by remember { mutableIntStateOf(0) }

    fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {}
    }

    Dialog(onDismissRequest = {}) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("onboarding_dialog")
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .height(480.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Icon & Title
                when (step) {
                    0 -> {
                        Icon(imageVector = Icons.Default.Book, contentDescription = null, tint = PurplePrimary, modifier = Modifier.size(56.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Welcome to ColorNest", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                    1 -> {
                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(56.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Organize & Finish", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                    2 -> {
                        Icon(imageVector = Icons.Default.OpenInNew, contentDescription = null, tint = PurplePrimary, modifier = Modifier.size(56.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Free Resources", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    when (step) {
                        0 -> {
                            Text("Important Disclaimer", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "ColorNest is NOT a digital art app, a coloring app, or a photo storage app.\n\n" +
                                        "It is a beautiful, AI-powered catalog designed to help you organize the physical coloring books, printed pages, and line art you already own.\n\n" +
                                        "Think of it as a library index for your hobby, not the drawing canvas itself.",
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        1 -> {
                            Text("Track Your Progress", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Upload photos of your pages, and use our AI to automatically sort them into deeply nested folders (like Animals > Birds > Owls).\n\n" +
                                        "When you are done coloring a page, you can mark it as Completed.\n\n" +
                                        "This gives you a dedicated Finished section, allowing you to easily browse and show off all your finished artwork in one beautiful gallery at once.",
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        2 -> {
                            Text(
                                "Need new pages to color? Here are direct links to external websites offering free line art and coloring pages:",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            val links = listOf(
                                Pair("SuperColoring", "https://www.supercoloring.com"),
                                Pair("Crayola Free Pages", "https://www.crayola.com/free-coloring-pages"),
                                Pair("Coloring Pages Pro", "https://coloringpagespro.com"),
                                Pair("Just Color", "https://www.justcolor.net"),
                                Pair("OpenClipart", "https://openclipart.org")
                            )

                            links.forEach { (name, url) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { openUrl(url) }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("• ", color = PurplePrimary, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = name,
                                        color = PurplePrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        textDecoration = TextDecoration.Underline
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(AccentRed.copy(alpha = 0.1f))
                                    .padding(12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = AccentRed, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Adult Content Advisory", fontWeight = FontWeight.Bold, color = AccentRed, fontSize = 12.sp)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "When browsing user-uploaded art repositories (e.g. DeviantArt), search results may include unfiltered community content. Please use safe-search filters.",
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = DividerDefaults.color.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 12.dp))

                // Bottom Controls: Step Dots + Next
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (i in 0..2) {
                            Box(
                                modifier = Modifier
                                    .size(if (i == step) 10.dp else 8.dp)
                                    .clip(CircleShape)
                                    .background(if (i == step) PurplePrimary else Color.LightGray)
                            )
                        }
                    }

                    Button(
                        onClick = {
                            if (step < 2) {
                                step++
                            } else {
                                onComplete()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.testTag("onboarding_next_button")
                    ) {
                        Text(if (step == 2) "Let's Go!" else "Next", fontWeight = FontWeight.Bold)
                        if (step < 2) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}
