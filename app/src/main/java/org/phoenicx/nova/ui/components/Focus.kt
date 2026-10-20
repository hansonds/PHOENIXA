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
    focusedScale: Float = 1.07f,
    borderWidth: Dp = 2.5.dp,
    glow: Boolean = true,
    focusRequester: FocusRequester? = null,
    onFocused: (() -> Unit)? = null,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) focusedScale else 1f, tween(200, easing = FastOutSlowInEasing), label = "scale")
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

/** Keeps the focused child at a fixed fraction of the viewport (Netflix-style pivot scrolling). */
@OptIn(ExperimentalFoundationApi::class)
fun pivotSpec(fraction: Float, startOffsetPx: Float = 0f, onlyWhenHidden: Boolean = false) = object : BringIntoViewSpec {
    override val scrollAnimationSpec: AnimationSpec<Float> = tween(320, easing = FastOutSlowInEasing)
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        if (onlyWhenHidden && offset >= 0f && offset + size <= containerSize) return 0f
        val pivot = containerSize * fraction + startOffsetPx
        return offset - pivot
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PivotScroll(fraction: Float, startOffsetPx: Float = 0f, onlyWhenHidden: Boolean = false, content: @Composable () -> Unit) {
    val spec = remember(fraction, startOffsetPx, onlyWhenHidden) { pivotSpec(fraction, startOffsetPx, onlyWhenHidden) }
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec) { content() }
}
