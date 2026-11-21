@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.miowser.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import com.miowser.app.ui.theme.SegmentedListTokens

private val LocalSegmentedGroup = staticCompositionLocalOf { false }

/** Outer clipping follows the actual visible children, including conditional settings rows. */
@Composable
internal fun SegmentedListGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    CompositionLocalProvider(LocalSegmentedGroup provides true) {
        Column(
            modifier = modifier.fillMaxWidth().clip(SegmentedListTokens.outerShape),
            verticalArrangement = Arrangement.spacedBy(SegmentedListTokens.gap),
            content = content
        )
    }
}

@Composable
internal fun segmentedListItemShape(): Shape =
    if (LocalSegmentedGroup.current) SegmentedListTokens.innerShape else SegmentedListTokens.outerShape

/** Apply before click/toggle modifiers so feedback respects the item's rounded bounds. */
@Composable
internal fun Modifier.segmentedListItem(selected: Boolean = false, nativeSurface: Boolean = false): Modifier {
    val base = fillMaxWidth().heightIn(min = SegmentedListTokens.minRowHeight)
    // Native ListItem must own its background: a second fill would mask the
    // rounded corners when its pressed/focused shape morphs.
    return if (nativeSurface) base else base
        .clip(if (selected) SegmentedListTokens.outerShape else segmentedListItemShape())
        .background(if (selected) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainerLow)
}

/** Native Expressive interaction shapes for grouped rows or independently composed lazy items. */
@Composable
internal fun segmentedListShapes(index: Int? = null, count: Int = 1): ListItemShapes {
    val shapes = ListItemDefaults.shapes(
        shape = if (index == null) segmentedListItemShape() else SegmentedListTokens.innerShape,
        selectedShape = SegmentedListTokens.outerShape,
        pressedShape = SegmentedListTokens.outerShape,
        focusedShape = SegmentedListTokens.outerShape,
        hoveredShape = SegmentedListTokens.outerShape,
        draggedShape = SegmentedListTokens.outerShape
    )
    return if (index == null) shapes else {
        // Native grouping resolves CornerLarge through MaterialTheme. Pin the base
        // geometry to this shared contract as well, so custom app shapes cannot
        // give lazy rows different outer corners from the settings groups.
        ListItemDefaults.segmentedShapes(index, count, shapes)
            .copy(shape = segmentedListShape(index, count))
    }
}

/** Matching geometry for rows whose custom content requires a Surface rather than ListItem. */
internal fun segmentedListShape(index: Int, count: Int): Shape = when {
    count <= 1 -> SegmentedListTokens.outerShape
    index == 0 -> SegmentedListTokens.firstShape
    index == count - 1 -> SegmentedListTokens.lastShape
    else -> SegmentedListTokens.innerShape
}
