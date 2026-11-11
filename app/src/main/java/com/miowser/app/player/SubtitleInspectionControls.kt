package com.miowser.app.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.miowser.app.R
import com.miowser.app.ui.AppText as Text
import com.miowser.app.ui.theme.LocalMotionPolicy
import com.miowser.app.ui.theme.MotionTokens
import com.miowser.app.ui.theme.PlayerUiColors
import com.miowser.app.ui.theme.SubtitlePanelTokens
import com.miowser.app.ui.theme.UiControlSize
import com.miowser.app.ui.theme.UiIconSize
import com.miowser.app.ui.theme.UiSpacing

/** Timing editors stay out of the transcript until requested; applying closes the editor. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlayerSubtitleTimingControls(
    delayMs: Long,
    onDelayChange: (Long) -> Unit,
    anchorMs: Long?,
    pressedCueStartUs: Long?,
    canAdjust: Boolean,
    showFollow: Boolean,
    onBeginAdjust: () -> Unit,
    onCancelAdjust: () -> Unit,
    onFollow: () -> Unit,
    sourceKey: String?
) {
    var editing by remember(sourceKey) { mutableStateOf(false) }
    var draft by remember(sourceKey) { mutableStateOf(delayMs.toString()) }
    var lastAnchor by remember(sourceKey) { mutableLongStateOf(anchorMs ?: 0L) }
    val displayedAnchor = anchorMs ?: lastAnchor
    val focusManager = LocalFocusManager.current
    val motion = LocalMotionPolicy.current
    val accent = subtitleOverlayAccent()
    val buttonColors = ButtonDefaults.filledTonalButtonColors(
        containerColor = accent.copy(alpha = SubtitlePanelTokens.controlContainerAlpha).compositeOver(PlayerUiColors.panel),
        contentColor = accent, disabledContainerColor = PlayerUiColors.panel,
        disabledContentColor = PlayerUiColors.disabledControl)
    val iconColors = IconButtonDefaults.filledTonalIconButtonColors(
        containerColor = accent.copy(alpha = SubtitlePanelTokens.controlContainerAlpha).compositeOver(PlayerUiColors.panel),
        contentColor = accent, disabledContainerColor = PlayerUiColors.panel,
        disabledContentColor = PlayerUiColors.disabledControl)
    fun closeEditor() {
        editing = false
        focusManager.clearFocus()
    }
    fun applyDelay(value: Long) {
        onDelayChange(value)
        closeEditor()
    }
    LaunchedEffect(delayMs) { if (!editing) draft = delayMs.toString() }
    LaunchedEffect(canAdjust) { if (!canAdjust) closeEditor() }
    LaunchedEffect(anchorMs) { anchorMs?.let { lastAnchor = it } }
    BackHandler(enabled = editing || anchorMs != null) {
        if (anchorMs != null) onCancelAdjust() else closeEditor()
    }
    Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.xxs)) {
        if (anchorMs == null) {
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(UiSpacing.xs),
                verticalArrangement = Arrangement.spacedBy(UiSpacing.xxs)) {
                FilledTonalIconButton(onClick = {
                    if (editing) closeEditor() else { draft = delayMs.toString(); editing = true }
                }, enabled = canAdjust, colors = iconColors) {
                    Icon(painterResource(R.drawable.ic_subtitles_gear_symbol_rounded), "Subtitle delay",
                        modifier = Modifier.size(UiIconSize.standard))
                }
                FilledTonalButton(onClick = { closeEditor(); onBeginAdjust() }, enabled = canAdjust,
                    colors = buttonColors, contentPadding = PaddingValues(horizontal = UiSpacing.s, vertical = UiSpacing.xs)) {
                    Icon(painterResource(R.drawable.ic_view_object_track_symbol_rounded), null,
                        modifier = Modifier.size(UiIconSize.small))
                    Spacer(Modifier.width(UiSpacing.xs))
                    Text("Assisted delay", style = MaterialTheme.typography.labelLarge)
                }
                if (delayMs != 0L && !editing) Text(subtitleDelayLabel(delayMs),
                    modifier = Modifier.align(Alignment.CenterVertically),
                    style = MaterialTheme.typography.labelMedium, color = accent)
                if (showFollow && canAdjust) FilledTonalIconButton(onClick = { closeEditor(); onFollow() }, colors = iconColors) {
                    Icon(Icons.Rounded.Refresh, "Follow playback")
                }
            }
        }
        AnimatedVisibility(visible = editing && anchorMs == null,
            enter = expandVertically(motion.spec(MotionTokens.controlsEnter), expandFrom = Alignment.Top) +
                fadeIn(motion.spec(MotionTokens.controlsEnter)),
            exit = shrinkVertically(motion.spec(MotionTokens.controlsExit), shrinkTowards = Alignment.Top) +
                fadeOut(motion.spec(MotionTokens.controlsExit))) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val inlineActions = maxWidth >= SubtitlePanelTokens.delayEditorInlineWidth
                val actions: @Composable RowScope.() -> Unit = {
                    if (delayMs != 0L || parseSubtitleDelay(draft)?.let { it != 0L } == true) {
                        FilledTonalIconButton(onClick = { applyDelay(0L) }, colors = iconColors) {
                            Icon(Icons.Rounded.Refresh, "Reset subtitle delay")
                        }
                    }
                    FilledTonalIconButton(onClick = { parseSubtitleDelay(draft)?.let(::applyDelay) },
                        enabled = parseSubtitleDelay(draft) != null, colors = iconColors) {
                        Icon(Icons.Rounded.Check, "Apply subtitle delay")
                    }
                }
                Column {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(UiSpacing.xxs)) {
                        FilledTonalIconButton(onClick = {
                            draft = ((parseSubtitleDelay(draft) ?: delayMs) - 50L).coerceAtLeast(-MAX_SUBTITLE_DELAY_MS).toString()
                        }, colors = iconColors) { Icon(Icons.Rounded.Remove, "Decrease subtitle delay by 50 milliseconds") }
                        OutlinedTextField(value = draft, onValueChange = {
                            if (it.length <= 8 && it.matches(SUBTITLE_DELAY_INPUT)) draft = it
                        }, singleLine = true, modifier = Modifier.weight(1f)
                            .semantics { contentDescription = "Subtitle delay in milliseconds" },
                            label = { Text("Delay") }, suffix = { Text("ms") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { parseSubtitleDelay(draft)?.let(::applyDelay) }),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = PlayerUiColors.content, unfocusedTextColor = PlayerUiColors.content,
                                cursorColor = accent, focusedBorderColor = accent,
                                unfocusedBorderColor = PlayerUiColors.disabledControl,
                                focusedLabelColor = accent, unfocusedLabelColor = PlayerUiColors.disabledControl,
                                focusedSuffixColor = PlayerUiColors.content, unfocusedSuffixColor = PlayerUiColors.content))
                        FilledTonalIconButton(onClick = {
                            draft = ((parseSubtitleDelay(draft) ?: delayMs) + 50L).coerceAtMost(MAX_SUBTITLE_DELAY_MS).toString()
                        }, colors = iconColors) { Icon(Icons.Rounded.Add, "Increase subtitle delay by 50 milliseconds") }
                        if (inlineActions) actions()
                    }
                    if (!inlineActions) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, content = actions)
                }
            }
        }
        AnimatedVisibility(visible = anchorMs != null,
            enter = expandVertically(motion.spec(MotionTokens.controlsEnter), expandFrom = Alignment.Top) +
                fadeIn(motion.spec(MotionTokens.controlsEnter)),
            exit = shrinkVertically(motion.spec(MotionTokens.controlsExit), shrinkTowards = Alignment.Top) +
                fadeOut(motion.spec(MotionTokens.controlsExit))) {
            // Keep the frozen time visible while the completed tool collapses.
            Surface(color = PlayerUiColors.panel, contentColor = PlayerUiColors.content, shape = MaterialTheme.shapes.medium) {
                Row(Modifier.fillMaxWidth().padding(UiSpacing.s), verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(UiSpacing.xs)) {
                    Icon(painterResource(R.drawable.ic_view_object_track_symbol_rounded), null,
                        tint = accent, modifier = Modifier.size(UiIconSize.standard))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(UiSpacing.xxs)) {
                        Text("Assisted delay · ${formatPlayerTime(displayedAnchor)}", style = MaterialTheme.typography.labelLarge, color = accent)
                        Text("Tap the line spoken at this moment to apply. Hold a line to preview its delay.",
                            style = MaterialTheme.typography.bodySmall)
                        Text(if (pressedCueStartUs != null) "Apply: ${subtitleDelayLabel(subtitleDelayForCue(displayedAnchor, pressedCueStartUs))}"
                            else "Current delay: ${subtitleDelayLabel(delayMs)}", style = MaterialTheme.typography.labelMedium, color = accent)
                    }
                    IconButton(onClick = onCancelAdjust, modifier = Modifier.size(UiControlSize.touchTarget)) {
                        Icon(Icons.Rounded.Close, "Cancel assisted delay", tint = PlayerUiColors.content)
                    }
                }
            }
        }
    }
}

private val SUBTITLE_DELAY_INPUT = Regex("-?\\d*")
