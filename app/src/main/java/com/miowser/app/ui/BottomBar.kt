package com.miowser.app.ui

import com.miowser.app.ui.theme.LocalMotionPolicy
import com.miowser.app.ui.theme.MotionTokens
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.miowser.app.R
import com.miowser.app.ui.icons.HotelClassRoundedOutline
import com.miowser.app.ui.icons.HomeRoundedOutline
import com.miowser.app.ui.theme.ExpressiveTokens

/**
 * The real browser toolbar: Back, Forward, Bookmarks, Home, Tabs, and an overflow (hamburger)
 * menu — the standard mobile-browser bottom-bar layout, plain icon buttons rather than a labeled
 * [androidx.compose.material3.NavigationBar] (which is meant for 3-5 top-level destinations with
 * room for labels; six icons need the compactness a plain Row gives).
 *
 * Back/Forward reflect real WebView history state via [canGoBack]/[canGoForward] — disabled
 * (dimmed, non-clickable) exactly when there's nowhere to go, the same state the hardware/gesture
 * back button already reacts to (see BrowserScreen's BackHandler), so the on-screen button and
 * the system gesture never disagree about whether "back" is available.
 *
 * Every button here is a plain navigation action — Bookmarks/Tabs/Downloads/History/Settings/
 * Media are all full pages (see [Screen]), not sheets, so there's no "currently open" state left
 * to highlight (a page fully covers this bar anyway). Unlike AddressBar's star, this Bookmarks
 * button has no per-tap state of its own: it's not a toggle for the current page, just navigation
 * to the saved-bookmarks list — the unfilled Hotel Class star cluster says that plainly regardless of
 * whether the active tab happens to be bookmarked.
 */
@Composable
fun BottomBar(
    canGoBack: Boolean,
    canGoForward: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onBookmarks: () -> Unit,
    onHome: () -> Unit,
    onHomeLongClick: () -> Unit,
    tabCount: Int,
    onTabs: () -> Unit,
    onTabsLongClick: () -> Unit,
    activeDownloadCount: Int,
    onMenu: () -> Unit,
    ready: Boolean = true
) {
    val motion = LocalMotionPolicy.current
    val homeInteraction = remember { MutableInteractionSource() }
    val homePressed by homeInteraction.collectIsPressedAsState()
    val homePressColor = animateColorAsState(
        targetValue = if (homePressed) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        animationSpec = motion.spec(if (homePressed) MotionTokens.press else MotionTokens.release),
        label = "Home button press"
    )
    // BottomBar sits in Scaffold's bottomBar slot, which — unlike Material3's own NavigationBar
    // — doesn't apply any window-insets padding on its own; a raw custom composable there draws
    // straight under the gesture/navigation bar without this. Scaffold sizes the WebView
    // content's bottom padding to match this Surface's actual measured height, so growing it by
    // the inset amount pushes the content up to clear it automatically.
    Surface(
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
    ) {
        BoxWithConstraints {
            val sidePadding = if (maxWidth >= 336.dp) 12.dp else 4.dp
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = sidePadding, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
            IconButton(onClick = onBack, enabled = ready && canGoBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.nav_back))
            }
            IconButton(onClick = onForward, enabled = ready && canGoForward) {
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = stringResource(R.string.nav_forward))
            }
            IconButton(onClick = onBookmarks, enabled = ready) {
                Icon(HotelClassRoundedOutline, contentDescription = stringResource(R.string.nav_bookmarks), tint = MaterialTheme.colorScheme.primary)
            }
            Box(
                modifier = Modifier
                    .size(ExpressiveTokens.minimumTouchTarget)
                    .clip(CircleShape)
                    .drawBehind { drawRect(homePressColor.value) }
                    .combinedClickable(
                        enabled = ready,
                        interactionSource = homeInteraction,
                        indication = null,
                        role = Role.Button,
                        onClick = onHome,
                        onLongClickLabel = stringResource(R.string.nav_focus_address),
                        onLongClick = onHomeLongClick
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(HomeRoundedOutline, contentDescription = stringResource(R.string.nav_home))
            }
            Box(
                modifier = Modifier
                    .size(ExpressiveTokens.minimumTouchTarget)
                    .testTag("browser_tabs")
                    .clip(CircleShape)
                    .combinedClickable(
                        enabled = ready,
                        role = Role.Button,
                        onClick = onTabs,
                        onLongClickLabel = stringResource(R.string.nav_open_new_tab),
                        onLongClick = onTabsLongClick
                    ),
                contentAlignment = Alignment.Center
            ) {
                TabCountIcon(count = tabCount)
            }
            IconButton(onClick = onMenu, enabled = ready) {
                if (activeDownloadCount > 0) {
                    BadgedBox(badge = { Badge { Text(activeDownloadCount.coerceAtMost(99).toString()) } }) {
                        Icon(Icons.Rounded.Menu, contentDescription = pluralStringResource(R.plurals.active_downloads, activeDownloadCount, activeDownloadCount))
                    }
                } else {
                    Icon(Icons.Rounded.Menu, contentDescription = stringResource(R.string.nav_more))
                }
            }
            }
        }
    }
}

/**
 * A rounded-square outline with the open-tab count inside — the standard mobile-browser "tabs"
 * icon; no ready-made one exists in material-icons-core. `clearAndSetSemantics` (rather than
 * plain `semantics`) replaces the inner Text's own auto-generated "3" reading with the full
 * label, so a screen reader announces "Tabs (3 open)" once instead of stacking both.
 */
@Composable
private fun TabCountIcon(count: Int) {
    val tint = LocalContentColor.current
    val description = pluralStringResource(R.plurals.open_tabs, count, count)
    Box(
        modifier = Modifier
            .size(24.dp)
            .border(width = 2.dp, color = tint, shape = RoundedCornerShape(5.dp))
            .clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = count.coerceAtMost(99).toString(),
            style = MaterialTheme.typography.labelSmall,
            color = tint
        )
    }
}
