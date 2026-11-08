package com.miowser.app.player

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import com.miowser.app.ui.theme.PlayerSpeedControlTokens
import com.miowser.app.ui.theme.UiControlSize
import com.miowser.app.ui.theme.UiSpacing
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/** Shared by both engines. A drag commits on release; discrete edits commit explicitly. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PlayerSpeedControls(speed: Float, onSpeedSelected: (Float) -> Unit) {
    val normal = PlayerSpeedControlTokens.normal
    val range = PlayerSpeedControlTokens.minimum..PlayerSpeedControlTokens.maximum
    val slowerSpan = normal - range.start
    val fasterSpan = range.endInclusive - normal
    fun precise(value: Float) = ((value / PlayerSpeedControlTokens.increment).roundToInt() /
        (1f / PlayerSpeedControlTokens.increment))
        .coerceIn(range)
    fun number(value: Float) = String.format(Locale.ROOT, "%.2f", precise(value))
    // Each half spans a different range so the physical midpoint represents normal speed.
    fun positionFor(rate: Float): Float {
        val offset = precise(rate) - normal
        val span = if (offset < 0f) slowerSpan else fasterSpan
        return offset / span
    }
    fun rateAt(position: Float): Float {
        val span = if (position < 0f) slowerSpan else fasterSpan
        return precise(normal + position * span)
    }
    val slider = rememberSliderState(value = positionFor(speed), trackRange = -1f..1f)
    val selectedRate = rateAt(slider.value)
    val interactionSource = remember { MutableInteractionSource() }
    val dragging by interactionSource.collectIsDraggedAsState()
    var pendingSpeed by remember { mutableStateOf<Float?>(null) }
    var inputFocused by remember { mutableStateOf(false) }
    var speedText by remember { mutableStateOf(number(speed)) }
    fun parseRate(text: String) = text.trim().replace(',', '.').toFloatOrNull()?.takeIf {
        it.isFinite() && it in range
    }
    val typedRate = parseRate(speedText)
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val arrowDirection = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    // Keep newer edits visible through intermediate asynchronous WebView acknowledgements.
    LaunchedEffect(speed, pendingSpeed, dragging) {
        if (!dragging) {
            val pending = pendingSpeed
            if (pending != null && abs(speed - pending) >= PlayerSpeedControlTokens.acknowledgementTolerance)
                delay(PlayerSpeedControlTokens.feedbackTimeoutMs)
            slider.value = positionFor(speed)
            pendingSpeed = null
        }
    }
    LaunchedEffect(selectedRate, inputFocused) {
        if (!inputFocused) speedText = number(selectedRate)
    }
    fun select(value: Float) {
        val rate = precise(value)
        slider.value = positionFor(rate)
        speedText = number(rate)
        pendingSpeed = rate
        onSpeedSelected(rate)
    }
    fun applyFieldRate(rate: Float) {
        select(rate)
        focus.clearFocus()
        keyboard?.hide()
    }
    fun applyTypedRate() = parseRate(speedText)?.let(::applyFieldRate)
    Column(Modifier.width(PlayerSpeedControlTokens.menuWidth).padding(horizontal = UiSpacing.m),
        verticalArrangement = Arrangement.spacedBy(UiSpacing.s)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(UiSpacing.s)) {
            Text("Playback speed", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            OutlinedTextField(
                value = speedText, onValueChange = { speedText = it },
                modifier = Modifier.width(PlayerSpeedControlTokens.fieldWidth)
                    .onFocusChanged { inputFocused = it.isFocused },
                label = { Text("Speed (×)") }, singleLine = true,
                isError = speedText.isNotBlank() && typedRate == null,
                supportingText = if (speedText.isNotBlank() && typedRate == null) ({
                    Text("${number(PlayerSpeedControlTokens.minimum)}–${number(PlayerSpeedControlTokens.maximum)}")
                }) else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { applyTypedRate() }),
                trailingIcon = {
                    Row {
                        IconButton(onClick = { applyFieldRate(normal) }) {
                            Icon(Icons.Rounded.Close, "Reset playback speed to 1×")
                        }
                        IconButton(onClick = { applyTypedRate() }, enabled = typedRate != null) {
                            Icon(Icons.Rounded.Check, "Apply playback speed")
                        }
                    }
                }
            )
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SpeedAdjustmentButton(Icons.Rounded.Remove, "Decrease speed by ${PlayerSpeedControlTokens.increment}x",
                enabled = selectedRate > range.start) {
                select(rateAt(slider.value) - PlayerSpeedControlTokens.increment)
            }
            Slider(state = slider, modifier = Modifier.weight(1f)
                .semantics {
                    contentDescription = "Playback speed"
                    stateDescription = "${number(selectedRate)}×"
                    progressBarRangeInfo = ProgressBarRangeInfo(selectedRate, range, PlayerSpeedControlTokens.sliderSteps)
                    setProgress { rate ->
                        if (!rate.isFinite()) return@setProgress false
                        select(rate)
                        true
                    }
                }
                .onPreviewKeyEvent { event ->
                    val direction = when (event.key) {
                        Key.DirectionLeft, Key.NumPadDirectionLeft -> -arrowDirection
                        Key.DirectionRight, Key.NumPadDirectionRight -> arrowDirection
                        else -> return@onPreviewKeyEvent false
                    }
                    if (event.type == KeyEventType.KeyDown)
                        select(rateAt(slider.value) + direction * PlayerSpeedControlTokens.increment)
                    true
                },
                interactionSource = interactionSource,
                onValueChange = { slider.value = it },
                onValueChangeFinished = { select(rateAt(slider.value)) },
                track = { state -> SliderDefaults.CenteredTrack(
                    sliderState = state, drawStopIndicator = null, drawTick = { _, _ -> }
                ) })
            SpeedAdjustmentButton(Icons.Rounded.Add, "Increase speed by ${PlayerSpeedControlTokens.increment}x",
                enabled = selectedRate < range.endInclusive) {
                select(rateAt(slider.value) + PlayerSpeedControlTokens.increment)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SpeedAdjustmentButton(icon: ImageVector, label: String, enabled: Boolean, onStep: () -> Unit) {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val currentStep by rememberUpdatedState(onStep)
    val longPressTimeout = LocalViewConfiguration.current.longPressTimeoutMillis
    val colors = IconButtonDefaults.iconButtonColors()
    LaunchedEffect(pressed, enabled) {
        if (pressed && enabled) {
            delay(longPressTimeout)
            var repeats = 0
            // combinedClickable supplies the first long-press step and suppresses the release click.
            while (true) {
                delay(when {
                    repeats < PlayerSpeedControlTokens.mediumRepeatAfter -> PlayerSpeedControlTokens.slowRepeatMs
                    repeats < PlayerSpeedControlTokens.fastRepeatAfter -> PlayerSpeedControlTokens.mediumRepeatMs
                    else -> PlayerSpeedControlTokens.fastRepeatMs
                })
                currentStep()
                repeats++
            }
        }
    }
    Box(Modifier.size(UiControlSize.touchTarget).clip(CircleShape)
        .combinedClickable(enabled = enabled, role = Role.Button, interactionSource = interactions,
            indication = ripple(), onClick = onStep, onLongClick = onStep,
            onLongClickLabel = "Hold to repeat"), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = label,
            tint = if (enabled) colors.contentColor else colors.disabledContentColor)
    }
}
