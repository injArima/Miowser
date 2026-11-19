package com.miowser.app.ui.theme

import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable

/** Family 2 owns selected controls; family 4 owns their text and boundaries. */
object AppSelectionColors {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun toggleColors() = ToggleButtonDefaults.colors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
        checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer
    )

    @Composable
    fun switchColors() = SwitchDefaults.colors(
        checkedThumbColor = MaterialTheme.colorScheme.onSecondaryContainer,
        checkedTrackColor = MaterialTheme.colorScheme.secondaryContainer,
        checkedBorderColor = MaterialTheme.colorScheme.outline,
        checkedIconColor = MaterialTheme.colorScheme.secondaryContainer,
        uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        uncheckedBorderColor = MaterialTheme.colorScheme.outline,
        uncheckedIconColor = MaterialTheme.colorScheme.surface
    )

    @Composable
    fun checkboxColors() = CheckboxDefaults.colors(
        checkedColor = MaterialTheme.colorScheme.secondary,
        uncheckedColor = MaterialTheme.colorScheme.outline,
        checkmarkColor = MaterialTheme.colorScheme.onSecondary
    )

    @Composable
    fun radioColors() = RadioButtonDefaults.colors(
        selectedColor = MaterialTheme.colorScheme.secondary,
        unselectedColor = MaterialTheme.colorScheme.outline
    )
}
