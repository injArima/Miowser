package com.miowser.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Snackbar payload whose action runs directly from the visible button's click handler. Keeping
 * the callback beside the visuals avoids relying on a suspended showSnackbar caller being resumed
 * before navigation state is changed — the cause of Switch visibly dismissing but not selecting
 * its background tab on-device.
 */
class MiowserSnackbarVisuals(
    override val message: String,
    override val actionLabel: String,
    override val withDismissAction: Boolean,
    override val duration: SnackbarDuration,
    val onAction: () -> Unit
) : SnackbarVisuals

/** Rounded Material You snackbars with manual dismissal in either horizontal direction. */
@Composable
fun MiowserSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(hostState = hostState, modifier = modifier.padding(horizontal = 12.dp)) { data ->
        val swipeState = rememberSwipeToDismissBoxState()
        SwipeToDismissBox(
            state = swipeState,
            enableDismissFromStartToEnd = true,
            enableDismissFromEndToStart = true,
            onDismiss = { direction ->
                if (direction != SwipeToDismissBoxValue.Settled) data.dismiss()
            },
            // A colored swipe background appeared as a second snackbar whenever even a tiny
            // horizontal drag began. Keep the gesture, but leave its background empty so the
            // user always sees one rounded snackbar surface.
            backgroundContent = {}
        ) {
            val actionLabel = data.visuals.actionLabel
            Snackbar(
                action = actionLabel?.let { label ->
                    {
                        TextButton(
                            onClick = {
                                // Run navigation/Undo/Share synchronously from the actual click,
                                // then resolve SnackbarHostState so it removes this snackbar.
                                // finally guarantees dismissal even if an action itself fails.
                                try {
                                    (data.visuals as? MiowserSnackbarVisuals)?.onAction?.invoke()
                                } finally {
                                    data.performAction()
                                }
                            },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(label)
                        }
                    }
                },
                dismissAction = if (data.visuals.withDismissAction) {
                    {
                        IconButton(onClick = data::dismiss) {
                            Icon(Icons.Rounded.Close, contentDescription = "Dismiss")
                        }
                    }
                } else {
                    null
                },
                shape = MaterialTheme.shapes.large,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
                actionContentColor = MaterialTheme.colorScheme.primary,
                dismissActionContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                Text(data.visuals.message)
            }
        }
    }
}
