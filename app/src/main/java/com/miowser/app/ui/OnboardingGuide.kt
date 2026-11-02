package com.miowser.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private data class OnboardingPage(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val points: List<String>
)

private val onboardingPages = listOf(
    OnboardingPage(
        icon = Icons.Rounded.Language,
        title = "Welcome to Miowser",
        description = "A browser built to help you find, play, download, and organize web media.",
        points = listOf(
            "Your Hub keeps the Library and Downloads together.",
            "Tap the globe from the Hub whenever you want to browse."
        )
    ),
    OnboardingPage(
        icon = Icons.Rounded.VideoLibrary,
        title = "Find media on a page",
        description = "Miowser watches for supported video, audio, streams, qualities, and subtitles while you browse.",
        points = listOf(
            "Open Media when its badge appears to review detected items.",
            "Some sites reveal more media only after you start playback."
        )
    ),
    OnboardingPage(
        icon = Icons.Rounded.PlayCircle,
        title = "Choose how you watch",
        description = "Use the video-assistant button for enhanced controls over a playing web video, or open detected media in the rich player.",
        points = listOf(
            "The rich player adds quality, subtitle, audio-track, gesture, PiP, and background-audio controls.",
            "DRM-protected media stays in the website's own player."
        )
    ),
    OnboardingPage(
        icon = Icons.Rounded.Download,
        title = "Save downloads your way",
        description = "On your first download, choose Miowser's default Downloads folder or a folder of your own.",
        points = listOf(
            "Pause, resume, retry, and recover supported downloads.",
            "After a restore, Miowser can reconnect records to files already in that folder."
        )
    ),
    OnboardingPage(
        icon = Icons.Rounded.Folder,
        title = "Build your Library",
        description = "Save media references, remember playback progress, and arrange videos in folders and subfolders.",
        points = listOf(
            "Import device folders without copying their videos.",
            "Use private tabs, site controls, ad blocking, and local backups when you need them."
        )
    )
)

@Composable
fun OnboardingGuide(
    revisiting: Boolean,
    onDismiss: () -> Unit,
    onFinished: () -> Unit
) {
    var pageIndex by remember { mutableIntStateOf(0) }
    val page = onboardingPages[pageIndex]

    Surface(
        modifier = Modifier.fillMaxSize().testTag("onboarding_guide"),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Miowser",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onDismiss) {
                    Text(if (revisiting) "Close" else "Skip")
                }
            }

            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(
                        page.icon,
                        contentDescription = null,
                        modifier = Modifier.padding(24.dp).size(48.dp)
                    )
                }
                Spacer(Modifier.height(28.dp))
                Text(
                    page.title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    page.description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(24.dp))
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        page.points.forEach { point ->
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Box(
                                    Modifier.padding(top = 7.dp).size(7.dp)
                                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                                )
                                Text(
                                    point,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                onboardingPages.indices.forEach { index ->
                    Box(
                        Modifier.padding(horizontal = 4.dp).size(if (index == pageIndex) 10.dp else 7.dp)
                            .background(
                                if (index == pageIndex) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant,
                                CircleShape
                            )
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (pageIndex > 0) {
                    TextButton(onClick = { pageIndex-- }, modifier = Modifier.weight(1f)) {
                        Text("Back")
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                }
                Button(
                    onClick = {
                        if (pageIndex == onboardingPages.lastIndex) onFinished() else pageIndex++
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (pageIndex == onboardingPages.lastIndex) "Start using Miowser" else "Next")
                }
            }
        }
    }
}
