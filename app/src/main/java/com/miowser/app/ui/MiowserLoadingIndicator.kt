package com.miowser.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LoadingIndicatorDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.miowser.app.ui.theme.LoadingIndicatorTokens
import com.miowser.app.ui.theme.LocalMotionPolicy

/** Indeterminate work only. Keep percentage and byte progress on determinate progress bars. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MiowserLoadingIndicator(
    modifier: Modifier = Modifier,
    size: Dp? = null,
    contained: Boolean = false,
    description: String = "Loading"
) {
    val indicatorModifier = (if (size == null) modifier else modifier.size(size))
        .semantics { contentDescription = description }
    if (LocalMotionPolicy.current.reduced) {
        val staticModifier = if (size == null) indicatorModifier.size(
            LoadingIndicatorDefaults.ContainerWidth,
            LoadingIndicatorDefaults.ContainerHeight
        ) else indicatorModifier
        Box(
            modifier = if (contained) staticModifier.background(
                LoadingIndicatorDefaults.containedContainerColor,
                LoadingIndicatorDefaults.containerShape
            ) else staticModifier,
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.HourglassEmpty,
                contentDescription = null,
                modifier = Modifier.size(LoadingIndicatorTokens.compact),
                tint = if (contained) LoadingIndicatorDefaults.containedIndicatorColor
                    else MaterialTheme.colorScheme.primary
            )
        }
    } else if (contained) {
        ContainedLoadingIndicator(modifier = indicatorModifier)
    } else {
        LoadingIndicator(modifier = indicatorModifier)
    }
}

/** Webpage navigation uses the wavy circular progress style shown in Material 3 guidelines. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WebPageProgressIndicator(modifier: Modifier = Modifier) {
    val indicatorModifier = modifier.size(LoadingIndicatorTokens.address)
        .semantics { contentDescription = "Loading page" }
    if (LocalMotionPolicy.current.reduced) {
        Box(indicatorModifier, contentAlignment = Alignment.Center) {
            Icon(
                Icons.Rounded.HourglassEmpty,
                contentDescription = null,
                modifier = Modifier.size(LoadingIndicatorTokens.compact),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    } else {
        CircularWavyProgressIndicator(modifier = indicatorModifier)
    }
}
