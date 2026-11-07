package com.miowser.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.miowser.app.ui.theme.UiElevation
import com.miowser.app.ui.theme.UiSpacing
import com.miowser.app.ui.theme.ConfirmationComponentTokens

internal enum class ConfirmationTone { NORMAL, CAUTION }

internal data class ConfirmationAction(
    val label: String,
    val onClick: () -> Unit,
    val tone: ConfirmationTone = ConfirmationTone.NORMAL,
    val enabled: Boolean = true,
    /** When present, the first tap reveals this explanation; only a second tap activates. */
    val firstTapDescription: String? = null
)

/** Shared layout for decisions, including destructive prompts with more than two choices. */
@Composable
internal fun MiowserConfirmationDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    actions: List<ConfirmationAction>
) {
    var revealedAction by remember(title) { mutableIntStateOf(-1) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = UiElevation.medium,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = ConfirmationComponentTokens.horizontalPadding,
                    vertical = ConfirmationComponentTokens.verticalPadding)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(UiSpacing.roomy)
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Text(message, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Column(verticalArrangement = Arrangement.spacedBy(ConfirmationComponentTokens.actionGap)) {
                    actions.chunked(2).forEachIndexed { rowIndex, rowActions ->
                        Row(horizontalArrangement = Arrangement.spacedBy(ConfirmationComponentTokens.actionGap),
                            modifier = Modifier.fillMaxWidth()) {
                            rowActions.forEachIndexed { columnIndex, action ->
                                val actionIndex = rowIndex * 2 + columnIndex
                                val isRevealed = revealedAction == actionIndex
                                val caution = action.tone == ConfirmationTone.CAUTION
                                val tint = if (caution) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.primary
                                OutlinedButton(
                                    onClick = {
                                        if (action.firstTapDescription != null && !isRevealed) {
                                            revealedAction = actionIndex
                                        } else action.onClick()
                                    },
                                    enabled = action.enabled,
                                    modifier = Modifier.weight(1f).heightIn(min = ConfirmationComponentTokens.actionMinHeight),
                                    shape = MaterialTheme.shapes.extraLarge,
                                    border = BorderStroke(1.dp, tint.copy(alpha = if (isRevealed)
                                        ConfirmationComponentTokens.revealedBorderAlpha else ConfirmationComponentTokens.normalBorderAlpha)),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = tint.copy(alpha = if (isRevealed)
                                            ConfirmationComponentTokens.revealedFillAlpha else if (caution)
                                            ConfirmationComponentTokens.cautionFillAlpha else ConfirmationComponentTokens.normalFillAlpha),
                                        contentColor = tint
                                    )
                                ) {
                                    Text(action.label, textAlign = TextAlign.Center,
                                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                        val selected = actions.getOrNull(revealedAction)
                            ?.takeIf { revealedAction / 2 == rowIndex && it.firstTapDescription != null }
                        if (selected != null) {
                            Text(selected.firstTapDescription.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = UiSpacing.xs))
                            Text("Tap ${selected.label} again to confirm.",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected.tone == ConfirmationTone.CAUTION)
                                    MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}
