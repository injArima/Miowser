package com.miowser.app.browser

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardReturn
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun SearchFieldActions(
    showClear: Boolean,
    onClear: () -> Unit,
    onSubmit: () -> Unit,
    submitEnabled: Boolean,
    submitDescription: String = "Search"
) {
    Row {
        if (showClear) {
            IconButton(onClick = onClear, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Rounded.Close, contentDescription = "Clear search field")
            }
        }
        IconButton(onClick = onSubmit, enabled = submitEnabled, modifier = Modifier.size(40.dp)) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardReturn, contentDescription = submitDescription)
        }
    }
}
