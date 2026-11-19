package org.phoenicx.nova.ui.components

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.phoenicx.nova.ui.theme.Nova

/**
 * A remote-friendly focusable surface. When focused it lifts up smoothly (a gentle spring) and gets a
 * glassy look: a soft frosted sheen, a thin bright edge and a light glare that sweeps across once,
 * a bit like Apple TV. DPAD-center / Enter triggers onClick.
 * decorate = false: only the lift (for things that draw their own highlight, like the top bar).
 */
@Composable
fun FocusSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(14.dp),
    focusedScale: Float = 1.05f,
    borderWidth: Dp = 2.5.dp,
    glow: Boolean = true,
    focusRequester: FocusRequester? = null,
    onFocused: (() -> Unit)? = null,
    decorate: Boolean = true,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val isFocused by interaction.collectIsFocusedAsState()
    val pressed by interaction.collectIsPressedAsState()
    // TV: highlight follows the remote's focus. Phone: highlight while the finger is down.
    val tv = org.phoenicx.nova.NovaApp.instance.isTv
    val focused = if (tv) isFocused else pressed
    val scale by animateFloatAsState(
        if (focused) focusedScale else 1f,
        androidx.compose.animation.core.spring(dampingRatio = 0.72f, stiffness = 420f), label = "scale",
    )
    // 0..1: how much of the glass effect is showing (fades in and out)
    val glass by animateFloatAsState(if (focused) 1f else 0f, tween(220, easing = FastOutSlowInEasing), label = "glass")
    // The glare that sweeps across once each time it gets focus
    val glare = remember { androidx.compose.animation.core.Animatable(-1f) }
    LaunchedEffect(isFocused) {
        if (isFocused) {
            onFocused?.invoke()
            if (tv && decorate) { glare.snapTo(-0.6f); glare.animateTo(1.6f, tween(750, easing = FastOutSlowInEasing)) }
        }
    }

    Box(
        modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .then(
                if (!decorate) Modifier else Modifier.drawWithContent {
                    drawContent()
                    if (glass > 0f) {
                        // Frosted sheen: brighter at the top, fading down
                        drawRect(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                0f to Color.White.copy(alpha = 0.16f * glass),
                                0.5f to Color.White.copy(alpha = 0.05f * glass),
                                1f to Color.White.copy(alpha = 0.02f * glass),
                            )
                        )
                        // Light glare passing across
                        val w = size.width
                        val x = glare.value * w
                        drawRect(
                            androidx.compose.ui.graphics.Brush.linearGradient(
                                listOf(Color.Transparent, Color.White.copy(alpha = 0.16f * glass), Color.Transparent),
                                start = androidx.compose.ui.geometry.Offset(x - w * 0.35f, 0f),
                                end = androidx.compose.ui.geometry.Offset(x + w * 0.05f, size.height),
                            )
                        )
                    }
                }
            )
            .then(
                // Thin bright glass edge
                if (decorate && borderWidth > 0.dp && glass > 0.01f)
                    Modifier.border(
                        (borderWidth.value.coerceAtMost(2f)).dp,
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = 0.85f * glass), Nova.Cyan.copy(alpha = 0.55f * glass), Color.White.copy(alpha = 0.25f * glass))
                        ),
                        shape,
                    )
                else Modifier
            )
    ) {
        content(focused)
    }
}

/**
 * Calm, remote-friendly scrolling: the list only moves when the focused item would be
 * off-screen, and then just far enough to show it with a margin. Short and snappy.
 */
@OptIn(ExperimentalFoundationApi::class)
fun edgeSpec(startMarginPx: Float, endMarginPx: Float) = object : BringIntoViewSpec {
    override val scrollAnimationSpec: AnimationSpec<Float> = tween(160, easing = FastOutSlowInEasing)
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val start = startMarginPx
        val end = containerSize - endMarginPx
        return when {
            offset < start -> offset - start
            offset + size > end -> (offset + size) - end
            else -> 0f
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EdgeScroll(startMargin: Dp, endMargin: Dp, content: @Composable () -> Unit) {
    val d = androidx.compose.ui.platform.LocalDensity.current
    val sPx = with(d) { startMargin.toPx() }
    val ePx = with(d) { endMargin.toPx() }
    val spec = remember(sPx, ePx) { edgeSpec(sPx, ePx) }
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec) { content() }
}
