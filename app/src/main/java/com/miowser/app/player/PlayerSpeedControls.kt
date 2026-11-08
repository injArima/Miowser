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
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
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
    fun precise(value: Float) = ((value / PlayerSpeedControlTokens.increment).roundToInt() /
        (1f / PlayerSpeedControlTokens.increment))
        .coerceIn(PlayerSpeedControlTokens.minimum, PlayerSpeedControlTokens.maximum)
    fun number(value: Float) = String.format(Locale.ROOT, "%.2f", precise(value))
    val slider = rememberSliderState(value = precise(speed), steps = PlayerSpeedControlTokens.sliderSteps,
        trackRange = PlayerSpeedControlTokens.minimum..PlayerSpeedControlTokens.maximum)
    val interactionSource = remember { MutableInteractionSource() }
    val dragging by interactionSource.collectIsDraggedAsState()
    var pendingSpeed by remember { mutableStateOf<Float?>(null) }
    var inputFocused by remember { mutableStateOf(false) }
    var speedText by remember { mutableStateOf(number(speed)) }
    fun parseRate(text: String) = text.trim().replace(',', '.').toFloatOrNull()?.takeIf {
        it.isFinite() && it in PlayerSpeedControlTokens.minimum..PlayerSpeedControlTokens.maximum
    }
    val typedRate = parseRate(speedText)
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    // Keep newer edits visible through intermediate asynchronous WebView acknowledgements.
    LaunchedEffect(speed, pendingSpeed, dragging) {
        if (!dragging) {
            val pending = pendingSpeed
            if (pending != null && abs(speed - pending) >= PlayerSpeedControlTokens.acknowledgementTolerance)
                delay(PlayerSpeedControlTokens.feedbackTimeoutMs)
            slider.value = precise(speed)
            pendingSpeed = null
        }
    }
    LaunchedEffect(slider.value, inputFocused) {
        if (!inputFocused) speedText = number(slider.value)
    }
    fun select(value: Float) {
        val rate = precise(value)
        slider.value = rate
        speedText = number(rate)
        pendingSpeed = rate
        onSpeedSelected(rate)
    }
    fun applyTypedRate() {
        parseRate(speedText)?.let {
            select(it)
            focus.clearFocus()
            keyboard?.hide()
        }
    }
    Column(Modifier.width(PlayerSpeedControlTokens.menuWidth).padding(horizontal = UiSpacing.m),
        verticalArrangement = Arrangement.spacedBy(UiSpacing.s)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(UiSpacing.s)) {
            Text("Playback speed", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            OutlinedTextField(
                value = speedText, onValueChange = { speedText = it },
                modifier = Modifier.width(PlayerSpeedControlTokens.fieldWidth)
                    .onFocusChanged { inputFocused = it.isFocused },
                label = { Text("Speed") }, suffix = { Text("×") }, singleLine = true,
                isError = speedText.isNotBlank() && typedRate == null,
                supportingText = if (speedText.isNotBlank() && typedRate == null) ({
                    Text("${number(PlayerSpeedControlTokens.minimum)}–${number(PlayerSpeedControlTokens.maximum)}")
                }) else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { applyTypedRate() }),
                trailingIcon = {
                    IconButton(onClick = { applyTypedRate() }, enabled = typedRate != null) {
                        Icon(Icons.Rounded.Check, "Apply playback speed")
                    }
                }
            )
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SpeedAdjustmentButton(Icons.Rounded.Remove, "Decrease speed by ${PlayerSpeedControlTokens.increment}x",
                enabled = slider.value > PlayerSpeedControlTokens.minimum) {
                select(slider.value - PlayerSpeedControlTokens.increment)
            }
            Slider(state = slider, modifier = Modifier.weight(1f),
                interactionSource = interactionSource,
                onValueChange = { slider.value = precise(it) },
                onValueChangeFinished = { select(slider.value) },
                track = { state -> SliderDefaults.CenteredTrack(
                    sliderState = state, drawStopIndicator = null, drawTick = { _, _ -> }
                ) })
            SpeedAdjustmentButton(Icons.Rounded.Add, "Increase speed by ${PlayerSpeedControlTokens.increment}x",
                enabled = slider.value < PlayerSpeedControlTokens.maximum) {
                select(slider.value + PlayerSpeedControlTokens.increment)
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
