package com.miowser.app.browser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.contextmenu.modifier.appendTextContextMenuComponents
import androidx.compose.foundation.text.contextmenu.builder.item
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextFieldDecorator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collect

/**
 * The browser's address bar: a single text field doubling as URL entry and search box (see
 * [UrlOrSearchResolver] for how input is disambiguated), a reload/stop button, an in-field loading
 * indicator, and — when the grabber has found anything on this page — a badged button opening the
 * detected-media list.
 */
@Composable
fun AddressBar(
    tabId: Long,
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    onSearchSelection: (String) -> Unit,
    onSubmit: () -> Unit,
    onReload: () -> Unit,
    onStop: () -> Unit,
    isLoading: Boolean,
    detectedCount: Int,
    onOpenDetected: () -> Unit,
    isBookmarked: Boolean,
    onToggleBookmark: () -> Unit,
    siteSecurity: SiteSecurity,
    onOpenSiteInfo: () -> Unit,
    focusRequestKey: Int,
    modifier: Modifier = Modifier,
    isPrivate: Boolean = false
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var fieldFocused by remember(tabId) { mutableStateOf(false) }
    val textState = remember(tabId) { TextFieldState(value.text, value.selection) }
    val textScroll = remember(tabId) { ScrollState(0) }
    val fieldInteraction = remember(tabId) { MutableInteractionSource() }
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val secureIconColor = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f)
        Color(0xFF81C995) else Color(0xFF137333)
    val securityAccent = when (siteSecurity) {
        SiteSecurity.SECURE -> secureIconColor
        SiteSecurity.CERTIFICATE_ERROR,
        SiteSecurity.CERTIFICATE_BYPASSED -> MaterialTheme.colorScheme.error
        SiteSecurity.CHECKING, SiteSecurity.INSECURE,
        SiteSecurity.OTHER -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    // Navigation and Home can replace the address externally; IME edits flow back to the tab.
    LaunchedEffect(tabId, value.text, value.selection) {
        if (textState.text.toString() != value.text || textState.selection != value.selection) {
            textState.edit {
                replace(0, length, value.text)
                selection = value.selection
            }
        }
    }
    LaunchedEffect(tabId) {
        snapshotFlow { TextFieldValue(textState.text.toString(), textState.selection) }
            .collect { currentOnValueChange(it) }
    }
    // A full selection should expose its trailing end; Compose's default selection follow
    // behavior otherwise leaves long URLs scrolled to their beginning.
    LaunchedEffect(fieldFocused, textState.selection, textScroll.maxValue) {
        if (fieldFocused && textState.text.isNotEmpty() &&
            textState.selection.end == textState.text.length
        ) textScroll.scrollTo(textScroll.maxValue)
    }
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
                if (isPrivate) Icon(
                    Icons.Rounded.VisibilityOff,
                    contentDescription = "Private tab",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                Box(modifier = Modifier.weight(1f)) {
                    BasicTextField(
                        state = textState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .focusRequester(focusRequester)
                            .onFocusChanged {
                                fieldFocused = it.isFocused
                                onFocusChanged(it.isFocused)
                            }
                            .appendTextContextMenuComponents {
                                val selection = textState.selection
                                if (!selection.collapsed) {
                                    val selected = textState.text.substring(selection.min, selection.max).trim()
                                    if (selected.isNotEmpty()) {
                                        item(key = "address-web-search", label = "Web search") {
                                            close()
                                            onSearchSelection(selected)
                                        }
                                    }
                                }
                            },
                        textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        onKeyboardAction = { onSubmit() },
                        lineLimits = TextFieldLineLimits.SingleLine,
                        scrollState = textScroll,
                        interactionSource = fieldInteraction,
                        decorator = TextFieldDecorator { inner ->
                            OutlinedTextFieldDefaults.DecorationBox(
                                value = textState.text.toString(),
                                innerTextField = inner,
                                enabled = true,
                                singleLine = true,
                                visualTransformation = VisualTransformation.None,
                                interactionSource = fieldInteraction,
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier.size(48.dp).background(
                                            Brush.horizontalGradient(
                                                listOf(
                                                    (if (isLoading) MaterialTheme.colorScheme.primary
                                                        else securityAccent).copy(alpha = 0.18f),
                                                    Color.Transparent
                                                )
                                            )
                                        ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        IconButton(onClick = onOpenSiteInfo) {
                                            if (isLoading) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(20.dp).semantics {
                                                        contentDescription = "Loading page"
                                                    },
                                                    color = MaterialTheme.colorScheme.primary,
                                                    strokeWidth = 2.dp
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = when (siteSecurity) {
                                                        SiteSecurity.SECURE -> Icons.Rounded.Lock
                                                        SiteSecurity.CERTIFICATE_ERROR,
                                                        SiteSecurity.CERTIFICATE_BYPASSED -> Icons.Outlined.WarningAmber
                                                        SiteSecurity.CHECKING, SiteSecurity.INSECURE,
                                                        SiteSecurity.OTHER -> Icons.Outlined.Info
                                                    },
                                                    contentDescription = "Site information: ${siteSecurity.label}",
                                                    tint = securityAccent
                                                )
                                            }
                                        }
                                    }
                                },
                                placeholder = { Text(if (isPrivate) "Private search or address" else "Search or enter address") },
                                trailingIcon = {
                                    val trailingAccent = if (fieldFocused || isBookmarked)
                                        MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                    Box(
                                        modifier = Modifier.size(48.dp).background(
                                            Brush.horizontalGradient(
                                                listOf(Color.Transparent, trailingAccent.copy(alpha = 0.16f))
                                            )
                                        ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (fieldFocused) SearchFieldActions(
                                            showClear = false,
                                            onClear = {},
                                            onSubmit = onSubmit,
                                            submitEnabled = textState.text.isNotBlank(),
                                            submitDescription = "Go to address or search"
                                        ) else IconButton(onClick = onToggleBookmark) {
                                            // StarBorder is the actual hollow bookmark state; the
                                            // ordinary Star glyph is filled in every icon style.
                                            Icon(
                                                imageVector = if (isBookmarked) Icons.Rounded.Star
                                                    else Icons.Rounded.StarBorder,
                                                contentDescription = if (isBookmarked) "Remove bookmark"
                                                    else "Add bookmark",
                                                tint = if (isBookmarked) MaterialTheme.colorScheme.primary
                                                    else LocalContentColor.current
                                            )
                                        }
                                    }
                                },
                                container = {
                                    OutlinedTextFieldDefaults.Container(
                                        enabled = true,
                                        isError = false,
                                        interactionSource = fieldInteraction,
                                        shape = MaterialTheme.shapes.extraLarge
                                    )
                                }
                            )
                        }
                    )
                }
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
    }
}
