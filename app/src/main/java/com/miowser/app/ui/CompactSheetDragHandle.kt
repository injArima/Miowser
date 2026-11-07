package com.miowser.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.miowser.app.ui.theme.UiSpacing
import com.miowser.app.ui.theme.SheetComponentTokens

/** Sheets meet the bottom edge; only their exposed top edge has rounded corners. */
val MiowserBottomSheetShape = SheetComponentTokens.topShape

/** Keep the first heading below the curved top edge without a large blank band. */
@Composable
fun CompactSheetDragHandle() {
    Box(
        modifier = Modifier.fillMaxWidth()
            .sheetTopRim(MaterialTheme.colorScheme.outlineVariant)
            .padding(top = SheetComponentTokens.handleTopPadding, bottom = SheetComponentTokens.handleBottomPadding),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.width(SheetComponentTokens.handleWidth).height(SheetComponentTokens.handleHeight)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    RoundedCornerShape(50))
        )
    }
}
