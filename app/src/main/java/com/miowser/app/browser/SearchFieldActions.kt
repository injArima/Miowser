package com.miowser.app.browser

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardReturn
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.miowser.app.ui.theme.UiControlSize

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
            IconButton(onClick = onClear, modifier = Modifier.size(UiControlSize.compact)) {
                Icon(Icons.Rounded.Close, contentDescription = "Clear search field")
            }
        }
        FilledTonalIconButton(onClick = onSubmit, enabled = submitEnabled,
            modifier = Modifier.size(UiControlSize.compact),
            shapes = IconButtonDefaults.shapes()) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardReturn, contentDescription = submitDescription)
        }
    }
}
