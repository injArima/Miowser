package com.miowser.app.persistence

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.miowser.app.ui.theme.UiElevation
import com.miowser.app.ui.theme.UiSpacing

/** Routine background saves stay quiet; an actual failure remains visible and retryable. */
@Composable
fun PersistenceNotice(modifier: Modifier = Modifier) {
    val state = Persistence.state
    if (state.failedKeys.isEmpty()) return
    Surface(modifier, shape = MaterialTheme.shapes.medium, tonalElevation = UiElevation.medium) {
        Row(Modifier.padding(horizontal = UiSpacing.l, vertical = UiSpacing.s)) {
            Text("Changes weren't saved. Keep the app open and retry.", modifier = Modifier.weight(1f))
            TextButton(onClick = Persistence::retryFailed, enabled = state.pending == 0) { Text("Retry") }
        }
    }
}
