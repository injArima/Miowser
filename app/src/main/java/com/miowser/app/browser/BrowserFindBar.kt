package com.miowser.app.browser

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardReturn
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember

@Composable
internal fun BrowserFindBar(query: String, matchIndex: Int, matchCount: Int,
                            onQueryChange: (String) -> Unit, onPrevious: () -> Unit,
                            onNext: () -> Unit, onClose: () -> Unit,
                            modifier: Modifier = Modifier) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }
    Surface(shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp, shadowElevation = 6.dp,
        modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(query, onQueryChange, singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    onNext()
                    keyboard?.hide()
                }),
                decorationBox = { innerTextField ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                            if (query.isEmpty()) {
                                Text("Find in page", style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            innerTextField()
                        }
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { onQueryChange("") }, modifier = Modifier.widthIn(max = 40.dp)) {
                                Icon(Icons.Rounded.Close, "Clear search field")
                            }
                        }
                    }
                },
                modifier = Modifier.weight(1f).height(48.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outline,
                        RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp)
                    .focusRequester(focusRequester))
            Text(if (query.isBlank()) "" else "$matchIndex/$matchCount",
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 32.dp))
            IconButton(onClick = onPrevious, enabled = matchCount > 0) {
                Icon(Icons.Rounded.KeyboardArrowUp, "Previous match")
            }
            IconButton(onClick = onNext, enabled = matchCount > 0) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardReturn, "Next match")
            }
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Close search") }
        }
    }
}
