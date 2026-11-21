@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.miowser.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import com.miowser.app.player.PlayerSubtitleStyleStore
import com.miowser.app.player.resolvedOutlineEnabled
import com.miowser.app.settings.AppSettings
import com.miowser.app.settings.SettingsSearchAvailability
import com.miowser.app.settings.SettingsSearchEntry
import com.miowser.app.settings.SettingsSearchIndex
import com.miowser.app.settings.SettingsSearchPage
import com.miowser.app.settings.SettingsSearchScope
import com.miowser.app.ui.theme.LocalMotionPolicy
import com.miowser.app.ui.theme.SegmentedListTokens
import com.miowser.app.ui.theme.SettingsSearchTokens
import com.miowser.app.ui.theme.UiSpacing
import kotlinx.coroutines.delay
import com.miowser.app.ui.AppText as Text

private data class SearchJump(val entry: SettingsSearchEntry, val sequence: Int)
private data class SearchSession(
    val page: SettingsSearchPage,
    val jump: SearchJump?,
    val open: () -> Unit,
    val consume: (Int) -> Unit
)
private val LocalSettingsSearch = staticCompositionLocalOf<SearchSession?> { null }

/** Keeps search and jump state above the existing settings destination/saveable-state owners. */
@Composable
internal fun SettingsSearchHost(
    page: SettingsSearchPage?,
    hasSavedMedia: Boolean,
    onNavigate: (SettingsSearchPage) -> Unit,
    content: @Composable () -> Unit
) {
    var openScope by rememberSaveable { mutableStateOf<String?>(null) }
    var browserQuery by rememberSaveable { mutableStateOf("") }
    var mediaQuery by rememberSaveable { mutableStateOf("") }
    var targetId by rememberSaveable { mutableStateOf<String?>(null) }
    var sequence by rememberSaveable { mutableIntStateOf(0) }
    val entry = targetId?.let { SettingsSearchIndex.byId(it) }
    val jumpSequence = sequence
    val consume: (Int) -> Unit = remember {
        { expected -> if (sequence == expected) targetId = null }
    }
    LaunchedEffect(page) {
        if (page == null) openScope = null
        if (sequence == jumpSequence && entry != null && entry.page != page) targetId = null
    }
    LaunchedEffect(targetId, jumpSequence) {
        if (targetId != null) {
            delay(SettingsSearchTokens.jumpTimeoutMillis)
            consume(jumpSequence)
        }
    }
    if (page == null) {
        content()
        return
    }
    val session = remember(page, entry, sequence) {
        SearchSession(page, entry?.let { SearchJump(it, sequence) },
            open = { targetId = null; openScope = page.scope.name }, consume = consume)
    }
    CompositionLocalProvider(LocalSettingsSearch provides session) {
        Box(Modifier.fillMaxSize()) {
            content()
            val scope = SettingsSearchScope.entries.firstOrNull { it.name == openScope }
            if (scope != null) {
                val context = LocalContext.current
                val settings = AppSettings.current
                val outline = remember(scope) {
                    scope != SettingsSearchScope.MEDIA || PlayerSubtitleStyleStore.load(context).resolvedOutlineEnabled()
                }
                val availability = SettingsSearchAvailability(
                    discovery = settings.deviceDiscoveryEnabled,
                    library = settings.libraryBehavior.isAvailable,
                    savedMedia = hasSavedMedia,
                    subtitleOutline = outline
                )
                SettingsSearchOverlay(
                    scope = scope,
                    query = if (scope == SettingsSearchScope.BROWSER) browserQuery else mediaQuery,
                    onQuery = { if (scope == SettingsSearchScope.BROWSER) browserQuery = it else mediaQuery = it },
                    availability = availability,
                    onDismiss = { openScope = null },
                    onChoose = { chosen ->
                        openScope = null
                        sequence++
                        targetId = chosen.id
                        onNavigate(chosen.page)
                    }
                )
            }
        }
    }
}

/** Default scaffold action is visible only within the settings search host's six destinations. */
@Composable
internal fun SettingsSearchAction() {
    val session = LocalSettingsSearch.current ?: return
    IconButton(onClick = session.open, modifier = Modifier.testTag("settings_search_open")) {
        Icon(Icons.Rounded.Search, contentDescription = "Search ${session.page.scope.label.lowercase()}")
    }
}

@Composable
private fun SettingsSearchOverlay(
    scope: SettingsSearchScope,
    query: String,
    onQuery: (String) -> Unit,
    availability: SettingsSearchAvailability,
    onDismiss: () -> Unit,
    onChoose: (SettingsSearchEntry) -> Unit
) {
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    fun dismiss() { focusManager.clearFocus(); keyboard?.hide(); onDismiss() }
    BackHandler(onBack = ::dismiss)
    val results = remember(query, scope, availability) { SettingsSearchIndex.search(query, scope, availability) }
    LaunchedEffect(Unit) { focus.requestFocus() }
    SimplePageScaffold(
        title = "Search",
        onBack = ::dismiss,
        actions = {}
    ) { modifier ->
        Column(modifier.fillMaxSize().imePadding()) {
            OutlinedTextField(
                value = query,
                onValueChange = { onQuery(it.take(SettingsSearchTokens.maxQueryLength)) },
                label = { Text(scope.label) },
                placeholder = { Text("Search settings") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = if (query.isNotEmpty()) ({
                    IconButton(onClick = { onQuery("") }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Clear settings search")
                    }
                }) else null,
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxWidth().padding(horizontal = UiSpacing.l, vertical = UiSpacing.s)
                    .focusRequester(focus).testTag("settings_search_query")
            )
            if (results.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth().padding(UiSpacing.l)) {
                    ExpressiveEmptyState("No matching settings", "Try a setting name or another related word.", Icons.Rounded.SearchOff)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth().testTag("settings_search_results"),
                    contentPadding = PaddingValues(UiSpacing.l),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(SegmentedListTokens.gap)
                ) {
                    item(key = "count") {
                        Text(if (query.isBlank()) "All settings" else "${results.size} matching settings",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(bottom = UiSpacing.m))
                    }
                    itemsIndexed(results, key = { _, result -> result.id }) { index, result ->
                        ListItem(
                            onClick = {
                                focusManager.clearFocus()
                                keyboard?.hide()
                                onChoose(result)
                            },
                            shapes = segmentedListShapes(index, results.size),
                            modifier = Modifier.segmentedListItem(nativeSurface = true)
                                .testTag("settings_search_result_${result.id}"),
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            supportingContent = {
                                Text("${result.path}\n${result.summary}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        ) { Text(result.title) }
                    }
                }
            }
        }
    }
}

/** Only a requested target installs scroll/draw owners; ordinary settings rows stay unchanged. */
@Composable
internal fun Modifier.settingsSearchAnchor(label: String): Modifier {
    val session = LocalSettingsSearch.current ?: return this
    val jump = session.jump?.takeIf { it.entry.page == session.page && it.entry.anchor == label } ?: return this
    val requester = remember { BringIntoViewRequester() }
    val pulse = remember { Animatable(0f) }
    var bounds by remember { mutableStateOf(IntSize.Zero) }
    val motion = LocalMotionPolicy.current
    val color = MaterialTheme.colorScheme.primary
    val visibleHeight = with(LocalDensity.current) { SettingsSearchTokens.anchorVisibleHeight.toPx() }
    LaunchedEffect(jump.sequence, bounds, motion.reduced) {
        if (bounds.width == 0 || bounds.height == 0) return@LaunchedEffect
        try {
            pulse.snapTo(0f)
            withFrameNanos { }
            requester.bringIntoView(Rect(0f, 0f, bounds.width.toFloat(), minOf(bounds.height.toFloat(), visibleHeight)))
            if (motion.reduced) {
                pulse.snapTo(1f)
                delay(SettingsSearchTokens.reducedHighlightMillis)
                pulse.snapTo(0f)
            } else {
                repeat(SettingsSearchTokens.pulseCount) {
                    pulse.animateTo(1f, motion.spec(SettingsSearchTokens.pulseEnter))
                    pulse.animateTo(0f, motion.spec(SettingsSearchTokens.pulseExit))
                }
            }
        } finally { session.consume(jump.sequence) }
    }
    return this.bringIntoViewRequester(requester)
        .onGloballyPositioned { if (it.isAttached) bounds = it.size }
        .drawWithContent {
            drawContent()
            val stroke = SettingsSearchTokens.highlightWidth.toPx()
            if (size.width > stroke && size.height > stroke) drawRoundRect(
                color = color.copy(alpha = pulse.value * SettingsSearchTokens.highlightAlpha),
                topLeft = Offset(stroke / 2, stroke / 2),
                size = Size(size.width - stroke, size.height - stroke),
                cornerRadius = CornerRadius(SegmentedListTokens.outerCorner.toPx()),
                style = Stroke(stroke)
            )
        }
}
