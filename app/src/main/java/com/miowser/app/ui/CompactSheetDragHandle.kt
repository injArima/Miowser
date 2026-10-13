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

/** Sheets meet the bottom edge; only their exposed top edge has rounded corners. */
val MiowserBottomSheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

/** Keep the first heading below the curved top edge without a large blank band. */
@Composable
fun CompactSheetDragHandle() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(top = 7.dp, bottom = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.width(32.dp).height(4.dp)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    RoundedCornerShape(50))
        )
    }
}
