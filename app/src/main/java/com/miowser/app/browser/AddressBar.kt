package com.miowser.app.browser

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star as FilledStar
import androidx.compose.material.icons.outlined.Star as OutlinedStar
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
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
    loadProgress: Float,
    detectedCount: Int,
    onOpenDetected: () -> Unit,
    isBookmarked: Boolean,
    onToggleBookmark: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(tonalElevation = 2.dp, modifier = modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { onFocusChanged(it.isFocused) },
                    singleLine = true,
                    placeholder = { Text("Search or enter address") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { onSubmit() })
                )
                if (detectedCount > 0) {
                    IconButton(onClick = onOpenDetected) {
                        BadgedBox(badge = { Badge { Text(detectedCount.coerceAtMost(99).toString()) } }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Detected media ($detectedCount)")
                        }
                    }
                }
                IconButton(onClick = onToggleBookmark) {
                    Icon(
                        imageVector = if (isBookmarked) Icons.Filled.FilledStar else Icons.Outlined.OutlinedStar,
                        contentDescription = if (isBookmarked) "Remove bookmark" else "Add bookmark"
                    )
                }
                IconButton(onClick = if (isLoading) onStop else onReload) {
                    Icon(
                        imageVector = if (isLoading) Icons.Default.Close else Icons.Default.Refresh,
                        contentDescription = if (isLoading) "Stop loading" else "Reload"
                    )
                }
            }
            if (isLoading) {
                LinearProgressIndicator(
                    progress = { loadProgress },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
