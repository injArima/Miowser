package com.miowser.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import com.miowser.app.R
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
    val onAction: () -> Unit,
    val secondaryActionLabel: String? = null,
    val onSecondaryAction: (() -> Unit)? = null
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
                        Row {
                            val visuals = data.visuals as? MiowserSnackbarVisuals
                            visuals?.secondaryActionLabel?.let { secondaryLabel ->
                                TextButton(
                                    onClick = {
                                        try { visuals.onSecondaryAction?.invoke() }
                                        finally { data.performAction() }
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = MaterialTheme.colorScheme.primary
                                    )
                                ) { Text(secondaryLabel) }
                            }
                            TextButton(
                                onClick = {
                                    // Run navigation synchronously, then resolve the snackbar.
                                    try { visuals?.onAction?.invoke() }
                                    finally { data.performAction() }
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text(label)
                            }
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_miowser_mark),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(data.visuals.message)
                }
            }
        }
    }
}
