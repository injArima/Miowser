package com.miowser.app.browser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

/**
 * The browser's address bar: a single text field doubling as URL entry and search box (see
 * [UrlOrSearchResolver] for how input is disambiguated), a reload/stop button, a slim progress
 * indicator while a page is loading, and — when the grabber has found anything on this page —
 * a badged button opening the detected-media list.
 */
@Composable
fun AddressBar(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    onReload: () -> Unit,
    onStop: () -> Unit,
    isLoading: Boolean,
    loadProgress: () -> Float,
    detectedCount: Int,
    onOpenDetected: () -> Unit,
    isBookmarked: Boolean,
    onToggleBookmark: () -> Unit,
    isPrivate: Boolean = false,
    siteSecurity: SiteSecurity,
    onOpenSiteInfo: () -> Unit,
    focusRequestKey: Int,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val lastHandledFocusRequest = remember { mutableIntStateOf(focusRequestKey) }
    LaunchedEffect(focusRequestKey) {
        if (focusRequestKey > lastHandledFocusRequest.intValue) {
            lastHandledFocusRequest.intValue = focusRequestKey
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }
    // AddressBar sits in Scaffold's topBar slot, which — unlike Material3's own TopAppBar —
    // doesn't apply any window-insets padding on its own; a raw custom composable there draws
    // straight under the status bar (and any display cutout) without this. Scaffold sizes the
    // WebView content's top padding to match this Surface's actual measured height, so growing
    // it by the inset amount pushes the content down to match automatically — no separate fix
    // needed on the content side.
    Surface(
        tonalElevation = 2.dp,
        color = if (isPrivate) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isPrivate) Icon(
                    Icons.Rounded.VisibilityOff,
                    contentDescription = "Private tab",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                // Leftmost, ahead of the URL field itself — the detected-media badge stays
                // clustered with reload/stop on the right instead.
                IconButton(onClick = onToggleBookmark) {
                    // Icons.Outlined.Star is NOT a hollow star — in this icon set "outlined" is
                    // just a corner-rounding style, and the star glyph is solid-filled in every
                    // one of Filled/Outlined/Rounded/Sharp/TwoTone (confirmed on-device: it
                    // rendered as a fully solid star even when unbookmarked). The genuinely
                    // hollow line-star is a separate glyph, StarBorder. Pairing that real shape
                    // change with a color shift (the trick every favorite/save toggle uses, e.g.
                    // a heart or star turning red/gold) makes "did that register?" obvious.
                    Icon(
                        imageVector = if (isBookmarked) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        contentDescription = if (isBookmarked) "Remove bookmark" else "Add bookmark",
                        tint = if (isBookmarked) MaterialTheme.colorScheme.primary else LocalContentColor.current
                    )
                }
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .onFocusChanged { onFocusChanged(it.isFocused) },
                    singleLine = true,
                    leadingIcon = {
                        IconButton(onClick = onOpenSiteInfo) {
                            Icon(
                                imageVector = if (siteSecurity == SiteSecurity.SECURE) Icons.Rounded.Lock
                                    else Icons.Rounded.LockOpen,
                                contentDescription = "Site information: ${siteSecurity.label}",
                                tint = when (siteSecurity) {
                                    SiteSecurity.SECURE -> MaterialTheme.colorScheme.primary
                                    SiteSecurity.INSECURE, SiteSecurity.CERTIFICATE_ERROR,
                                    SiteSecurity.CERTIFICATE_BYPASSED -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    },
                    placeholder = { Text(if (isPrivate) "Private search or address" else "Search or enter address") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { onSubmit() }),
                    shape = MaterialTheme.shapes.extraLarge
                )
                if (detectedCount > 0) {
                    Box(
                        modifier = Modifier.size(48.dp)
                            .clickable(role = Role.Button, onClick = onOpenDetected)
                    ) {
                        Icon(
                            Icons.Rounded.PlayArrow,
                            contentDescription = "Detected media ($detectedCount)",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.Center)
                        )
                        // Badge has its own measured space inside the tap target. Keeping it
                        // outside IconButton avoids the button's circular clip at the top edge.
                        Badge(modifier = Modifier.align(Alignment.TopEnd)
                            .padding(top = 2.dp, end = 2.dp)) {
                            Text(detectedCount.coerceAtMost(99).toString())
                        }
                    }
                }
                IconButton(onClick = if (isLoading) onStop else onReload) {
                    Icon(
                        imageVector = if (isLoading) Icons.Rounded.Close else Icons.Rounded.Refresh,
                        contentDescription = if (isLoading) "Stop loading" else "Reload"
                    )
                }
            }
            if (isLoading) {
                LinearProgressIndicator(
                    progress = loadProgress,
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
