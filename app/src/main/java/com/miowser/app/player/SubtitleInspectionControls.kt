package com.miowser.app.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.miowser.app.R
import com.miowser.app.ui.AppText as Text
import com.miowser.app.ui.theme.LocalMotionPolicy
import com.miowser.app.ui.theme.MotionTokens
import com.miowser.app.ui.theme.PlayerUiColors
import com.miowser.app.ui.theme.SubtitlePanelTokens
import com.miowser.app.ui.theme.UiElevation
import com.miowser.app.ui.theme.UiIconSize
import com.miowser.app.ui.theme.UiSpacing
import kotlinx.coroutines.delay

/** Both timing modes edit a draft; only their confirmation button changes playback timing. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlayerSubtitleTimingControls(
    delayMs: Long,
    draft: SubtitleDelayDraft,
    onDraftChange: (SubtitleDelayDraft) -> Unit,
    onDelayChange: (Long) -> Unit,
    onConfirmSubtitleDelay: (Long) -> Unit,
    anchorMs: Long?,
    pressedCueStartUs: Long?,
    canAdjust: Boolean,
    onBeginAdjust: () -> Unit,
    onCancelAdjust: () -> Unit,
    sourceKey: String?
) {
    var manualEditing by remember(sourceKey) { mutableStateOf(false) }
    var helpVisible by remember(sourceKey, anchorMs) { mutableStateOf(false) }
    val assisted = anchorMs != null
    val editing = manualEditing || assisted
    val heldPreview = if (anchorMs != null && pressedCueStartUs != null) subtitleDelayForCue(anchorMs, pressedCueStartUs) else null
    val focusManager = LocalFocusManager.current
    val motion = LocalMotionPolicy.current
    val accent = subtitleOverlayAccent()
    val containerColor = accent.copy(alpha = SubtitlePanelTokens.controlContainerAlpha).compositeOver(PlayerUiColors.panel)
    val buttonColors = ButtonDefaults.filledTonalButtonColors(
        containerColor = containerColor, contentColor = accent,
        disabledContainerColor = PlayerUiColors.panel, disabledContentColor = PlayerUiColors.disabledControl)
    val iconColors = IconButtonDefaults.filledTonalIconButtonColors(
        containerColor = containerColor, contentColor = accent,
        disabledContainerColor = PlayerUiColors.panel, disabledContentColor = PlayerUiColors.disabledControl)
    fun closeEditor() {
        manualEditing = false
        helpVisible = false
        focusManager.clearFocus()
    }
    fun cancel() {
        closeEditor()
        onDraftChange(SubtitleDelayDraft(delayMs.toString()))
        if (assisted) onCancelAdjust()
    }
    fun confirm() {
        val value = draft.valueMs ?: return
        if (assisted) onConfirmSubtitleDelay(value) else onDelayChange(value)
        closeEditor()
    }
    LaunchedEffect(canAdjust) { if (!canAdjust) closeEditor() }
    LaunchedEffect(helpVisible) {
        if (helpVisible) { delay(SubtitlePanelTokens.delayHelpDurationMs); helpVisible = false }
    }
    BackHandler(enabled = editing || helpVisible) {
        if (helpVisible) helpVisible = false else cancel()
    }
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(UiSpacing.xxs),
        verticalArrangement = Arrangement.spacedBy(UiSpacing.xxs), itemVerticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(onClick = {
            if (editing) cancel() else {
                onDraftChange(SubtitleDelayDraft(delayMs.toString()))
                manualEditing = true
            }
        }, enabled = canAdjust, colors = iconColors) {
            Icon(painterResource(R.drawable.ic_subtitles_gear_symbol_rounded), "Subtitle delay",
                modifier = Modifier.size(UiIconSize.standard))
        }
        AnimatedVisibility(visible = editing,
            enter = expandHorizontally(motion.spec(MotionTokens.controlsEnter), expandFrom = Alignment.Start) +
                fadeIn(motion.spec(MotionTokens.controlsEnter)),
            exit = shrinkHorizontally(motion.spec(MotionTokens.controlsExit), shrinkTowards = Alignment.Start) +
                fadeOut(motion.spec(MotionTokens.controlsExit))) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(UiSpacing.xxs)) {
                FilledTonalIconButton(onClick = { onDraftChange(draft.step(-50L)) }, colors = iconColors) {
                    Icon(Icons.Rounded.Remove, "Decrease subtitle delay by 50 milliseconds")
                }
                OutlinedTextField(value = heldPreview?.toString() ?: draft.input, onValueChange = {
                    if (it.length <= 8 && it.matches(SUBTITLE_DELAY_INPUT)) onDraftChange(draft.edit(it))
                }, singleLine = true, readOnly = heldPreview != null,
                    modifier = Modifier.width(SubtitlePanelTokens.delayEditorFieldWidth)
                        .semantics { contentDescription = "Subtitle delay in milliseconds" },
                    label = { Text("ms") }, textStyle = MaterialTheme.typography.bodyMedium,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (assisted) focusManager.clearFocus() else confirm() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = PlayerUiColors.content, unfocusedTextColor = PlayerUiColors.content,
                        cursorColor = accent, focusedBorderColor = accent, unfocusedBorderColor = PlayerUiColors.disabledControl,
                        focusedLabelColor = accent, unfocusedLabelColor = PlayerUiColors.disabledControl))
                FilledTonalIconButton(onClick = { onDraftChange(draft.step(50L)) }, colors = iconColors) {
                    Icon(Icons.Rounded.Add, "Increase subtitle delay by 50 milliseconds")
                }
            }
        }
        if (editing) {
            if (delayMs != 0L || draft.valueMs?.let { it != 0L } == true) {
                FilledTonalIconButton(onClick = {
                    if (assisted) onDraftChange(SubtitleDelayDraft("0")) else { onDelayChange(0L); closeEditor() }
                }, colors = iconColors) { Icon(Icons.Rounded.Refresh, "Reset subtitle delay") }
            }
            if (!assisted) FilledTonalIconButton(onClick = ::confirm, enabled = draft.valueMs != null, colors = iconColors) {
                Icon(Icons.Rounded.Check, "Apply subtitle delay")
            }
        }
        FilledTonalButton(onClick = {
            if (assisted) cancel() else { closeEditor(); onBeginAdjust() }
        }, enabled = canAdjust,
            modifier = Modifier.semantics { selected = assisted },
            colors = if (assisted) ButtonDefaults.filledTonalButtonColors(
                containerColor = accent, contentColor = PlayerUiColors.panel) else buttonColors,
            contentPadding = PaddingValues(horizontal = UiSpacing.s, vertical = UiSpacing.xs)) {
            Icon(painterResource(R.drawable.ic_view_object_track_symbol_rounded), null, modifier = Modifier.size(UiIconSize.small))
            Spacer(Modifier.width(UiSpacing.xs))
            Text("Assisted Delay", style = MaterialTheme.typography.labelLarge)
        }
        if (assisted) Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                FilledTonalIconButton(onClick = { helpVisible = !helpVisible }, colors = iconColors) {
                    Icon(Icons.Rounded.Info, "Assisted Delay help")
                }
                if (helpVisible) {
                    val offset = with(LocalDensity.current) { SubtitlePanelTokens.delayHelpOffset.roundToPx() }
                    Popup(alignment = Alignment.TopEnd, offset = IntOffset(0, offset),
                        onDismissRequest = { helpVisible = false }, properties = PopupProperties(focusable = true)) {
                        Surface(modifier = Modifier.widthIn(max = SubtitlePanelTokens.delayHelpMaxWidth),
                            shape = MaterialTheme.shapes.large, color = PlayerUiColors.panel,
                            contentColor = PlayerUiColors.content, shadowElevation = UiElevation.high) {
                            Text("Playback is paused at ${formatPlayerTime(anchorMs)}. Tap lines to try their delay in the field. " +
                                "Hold a line to preview without saving. Use the tick to apply, or the cross to cancel.",
                                modifier = Modifier.padding(UiSpacing.m), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            FilledTonalIconButton(onClick = ::confirm, enabled = draft.valueMs != null && heldPreview == null, colors = iconColors) {
                Icon(Icons.Rounded.Check, "Apply assisted delay")
            }
            FilledTonalIconButton(onClick = ::cancel, colors = iconColors) {
                Icon(Icons.Rounded.Close, "Cancel assisted delay")
            }
        }
        if (delayMs != 0L && !editing) Text(subtitleDelayLabel(delayMs),
            style = MaterialTheme.typography.labelMedium, color = accent)
    }
}

private val SUBTITLE_DELAY_INPUT = Regex("-?\\d*")
