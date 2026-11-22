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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.miowser.app.ui.AppText as Text
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import com.miowser.app.settings.Settings as AppPreferences
import com.miowser.app.settings.GuideTopic
import com.miowser.app.settings.onboardingPages
import com.miowser.app.ui.theme.OnboardingTokens
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import com.miowser.app.ui.theme.UiControlSize
import com.miowser.app.ui.theme.UiSpacing

@Composable
fun OnboardingGuide(
    revisiting: Boolean,
    settings: AppPreferences,
    onDismiss: () -> Unit,
    onFinished: () -> Unit
) {
    val visiblePages = remember(settings) { onboardingPages(settings) }
    var savedPageIndex by rememberSaveable { mutableIntStateOf(0) }
    val pageIndex = savedPageIndex.coerceIn(visiblePages.indices)
    val page = visiblePages[pageIndex]
    fun back() { if (pageIndex > 0) savedPageIndex = pageIndex - 1 else onDismiss() }
    BackHandler(onBack = ::back)

    Surface(
        modifier = Modifier.fillMaxSize().testTag("onboarding_guide"),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = UiSpacing.xxl, vertical = UiSpacing.m)
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
                MiowserActionButton(onClick = onDismiss, style = AppButtonStyle.TEXT) {
                    Text(if (revisiting) "Close" else "Skip")
                }
            }

            key(page.topic) {
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                        .padding(vertical = UiSpacing.xxl).testTag("onboarding_page_${page.topic.name}"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.tertiary
                    ) {
                        Icon(
                            page.topic.icon(),
                            contentDescription = null,
                            modifier = Modifier.padding(UiSpacing.xxl).size(UiControlSize.touchTarget)
                        )
                    }
                    Spacer(Modifier.height(OnboardingTokens.iconTitleGap))
                    Text(
                        page.title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(UiSpacing.m))
                    Text(
                        page.description,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(UiSpacing.xxl))
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = UiSpacing.roomy, vertical = UiSpacing.l),
                            verticalArrangement = Arrangement.spacedBy(UiSpacing.m)
                        ) {
                            page.points.forEach { point ->
                                Row(horizontalArrangement = Arrangement.spacedBy(UiSpacing.m)) {
                                    Box(
                                        Modifier.padding(top = OnboardingTokens.bulletTopPadding).size(OnboardingTokens.bulletSize)
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
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = UiSpacing.m)
                    .semantics { contentDescription = "Page ${pageIndex + 1} of ${visiblePages.size}" },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                visiblePages.indices.forEach { index ->
                    Box(
                        Modifier.padding(horizontal = UiSpacing.xs).size(if (index == pageIndex) OnboardingTokens.activeDotSize else OnboardingTokens.dotSize)
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
                horizontalArrangement = Arrangement.spacedBy(UiSpacing.m),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (pageIndex > 0) {
                    MiowserActionButton(onClick = ::back, style = AppButtonStyle.TEXT, modifier = Modifier.weight(1f)) {
                        Text("Back")
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                }
                MiowserActionButton(
                    onClick = {
                        if (pageIndex == visiblePages.lastIndex) onFinished() else savedPageIndex = pageIndex + 1
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (pageIndex == visiblePages.lastIndex) (if (revisiting) "Done" else "Get started") else "Next")
                }
            }
        }
    }
}

private fun GuideTopic.icon(): ImageVector = when (this) {
    GuideTopic.WELCOME -> Icons.Rounded.PlayCircle
    GuideTopic.DEVICE -> Icons.Rounded.Folder
    GuideTopic.WEB_VIDEO -> Icons.Rounded.Language
    GuideTopic.SUBTITLES -> Icons.Rounded.Subtitles
    GuideTopic.DOWNLOADS -> Icons.Rounded.Download
    GuideTopic.WATCH_CATALOG -> Icons.Rounded.VideoLibrary
    GuideTopic.CUSTOMIZE -> Icons.Rounded.Tune
    GuideTopic.SETTINGS -> Icons.Rounded.Settings
}
