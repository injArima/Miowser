package com.miowser.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.miowser.app.ui.theme.ActionButtonTokens

/** An action's emphasis is chosen at the call site; all variants remain Material 3 buttons. */
enum class AppButtonStyle { PRIMARY, TONAL, OUTLINED, TEXT, CAUTION, CAUTION_OUTLINED }

@Composable
fun MiowserActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: AppButtonStyle = AppButtonStyle.PRIMARY,
    content: @Composable RowScope.() -> Unit
) {
    val buttonModifier = modifier.heightIn(min = ActionButtonTokens.minHeight)
    when (style) {
        AppButtonStyle.PRIMARY -> Button(onClick = onClick, modifier = buttonModifier,
            enabled = enabled, shape = ActionButtonTokens.shape, content = content)
        AppButtonStyle.TONAL -> FilledTonalButton(onClick = onClick, modifier = buttonModifier,
            enabled = enabled, shape = ActionButtonTokens.shape, content = content)
        AppButtonStyle.OUTLINED -> OutlinedButton(onClick = onClick, modifier = buttonModifier,
            enabled = enabled, shape = ActionButtonTokens.shape, content = content)
        AppButtonStyle.TEXT -> TextButton(onClick = onClick, modifier = buttonModifier,
            enabled = enabled, shape = ActionButtonTokens.shape, content = content)
        AppButtonStyle.CAUTION -> FilledTonalButton(onClick = onClick, modifier = buttonModifier,
            enabled = enabled, shape = ActionButtonTokens.shape,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer),
            content = content)
        AppButtonStyle.CAUTION_OUTLINED -> OutlinedButton(onClick = onClick,
            modifier = buttonModifier, enabled = enabled, shape = ActionButtonTokens.shape,
            border = BorderStroke(ActionButtonTokens.cautionOutlineWidth, MaterialTheme.colorScheme.error.copy(
                alpha = ActionButtonTokens.cautionOutlineAlpha)),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(
                    alpha = ActionButtonTokens.cautionContainerAlpha),
                contentColor = MaterialTheme.colorScheme.error),
            content = content)
    }
}
