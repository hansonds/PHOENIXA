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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.phoenicx.nova.ui.theme.Nova

/**
 * A remote-friendly focusable surface: scales up, glows and gets an aurora
 * gradient border when focused. DPAD-center / Enter triggers onClick.
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
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) focusedScale else 1f, tween(130, easing = FastOutSlowInEasing), label = "scale")
    LaunchedEffect(focused) { if (focused) onFocused?.invoke() }

    Box(
        modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(
                if (focused && glow) Modifier.shadow(22.dp, shape, ambientColor = Nova.Cyan, spotColor = Nova.Violet)
                else Modifier
            )
            .clip(shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .then(if (focused && borderWidth > 0.dp) Modifier.border(borderWidth, Nova.AccentBrush, shape) else Modifier)
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
