package com.miowser.app.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import com.miowser.app.ui.theme.LocalMotionPolicy
import com.miowser.app.ui.theme.MotionTokens

internal val LocalPlayerControlsActive = staticCompositionLocalOf { true }

/** Modal content must be a sibling of the input gate, never its descendant. */
@Composable
internal fun PlayerInteractionLayers(
    blockPlayerInput: Boolean,
    modifier: Modifier = Modifier,
    playerContent: @Composable BoxScope.() -> Unit,
    overlayContent: @Composable BoxScope.() -> Unit
) {
    Box(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().playerInputGate(blockPlayerInput), content = playerContent)
        overlayContent()
    }
}

@Composable
internal fun PlayerOverlayHost(state: PlayerOverlayState, onClosed: (PlayerOverlay) -> Unit,
    content: @Composable (PlayerOverlay, Modifier, FocusRequester) -> Unit) {
    val panel = state.rendered ?: return
    val motion = LocalMotionPolicy.current
    val travel = with(LocalDensity.current) { MotionTokens.compactTravel.roundToPx() }
    val closed = rememberUpdatedState(onClosed)
    key(panel) {
        val visibility = remember { MutableTransitionState(false) }
        val initialFocus = remember { FocusRequester() }
        visibility.targetState = state.target == panel
        val exitRevision = state.revision
        val enter = if (panel == PlayerOverlay.PLAYLIST) MotionTokens.playlistEnter else MotionTokens.subtitleEnter
        val exit = if (panel == PlayerOverlay.PLAYLIST) MotionTokens.playlistExit else MotionTokens.subtitleExit
        BackHandler { state.dismiss() }
        LaunchedEffect(panel, visibility.isIdle, visibility.currentState, visibility.targetState, exitRevision) {
            if (visibility.isIdle && !visibility.currentState && !visibility.targetState) {
                if (state.finishExit(panel, exitRevision)) closed.value(panel)
            }
        }
        LaunchedEffect(panel, visibility.targetState) {
            if (visibility.targetState) { withFrameNanos { }; initialFocus.requestFocus() }
        }
        AnimatedVisibility(visibleState = visibility,
            enter = fadeIn(motion.spec(enter)), exit = fadeOut(motion.spec(exit)),
            modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().focusGroup().semantics { paneTitle = panel.title }) {
                Box(Modifier.fillMaxSize()
                    .background(Color.Black.copy(alpha = if (panel == PlayerOverlay.PLAYLIST) 0.28f else 0.90f))
                    .semantics { contentDescription = "Close ${panel.title.lowercase()}" }
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null,
                        onClick = { state.dismiss() }))
                val movement = if (motion.reduced) Modifier else Modifier.animateEnterExit(
                    enter = if (panel == PlayerOverlay.PLAYLIST) slideInHorizontally(motion.spec(enter)) { it }
                        else slideInVertically(motion.spec(enter)) { travel },
                    exit = if (panel == PlayerOverlay.PLAYLIST) slideOutHorizontally(motion.spec(exit)) { it }
                        else slideOutVertically(motion.spec(exit)) { travel })
                content(panel, movement.playerInputGate(!visibility.targetState), initialFocus)
            }
        }
    }
}

/** A retained closing modal consumes pointers; hidden descendants cannot receive focus/actions. */
internal fun Modifier.playerFocusGate(blocked: Boolean): Modifier =
    if (!blocked) this else this.focusProperties { canFocus = false }.clearAndSetSemantics { }

internal fun Modifier.playerInputGate(blocked: Boolean): Modifier =
    if (!blocked) this else this.playerFocusGate(true)
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
            }
        }

/** Keep blank-panel taps off the backdrop while allowing nested controls to handle their taps. */
@Composable
internal fun Modifier.playerPanelPointerShield(): Modifier = clickable(
    interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}
)
