package org.phoenicx.nova.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.phoenicx.nova.ui.theme.Nova
import kotlin.math.roundToInt

/**
 * TV: one pane of "glass" that glides from the highlighted card to the next one, like Apple TV.
 * Focusable things report where they are; the layer on top of the screen draws the glass there.
 */
class GlassState {
    var rect by mutableStateOf<Rect?>(null); private set
    var shape by mutableStateOf<Shape>(RoundedCornerShape(14))
        private set
    var scale by mutableStateOf(1f); private set
    var visible by mutableStateOf(false); private set
    var moves by mutableStateOf(0); private set
    private var owner: Any? = null

    fun claim(token: Any, shape: Shape, scale: Float) {
        if (owner !== token) moves++
        owner = token; this.shape = shape; this.scale = scale; visible = true
    }

    fun update(token: Any, r: Rect) { if (owner === token) rect = r }

    fun release(token: Any) {
        if (owner === token) { owner = null; visible = false }
    }
}

val LocalGlass = staticCompositionLocalOf<GlassState?> { null }

@Composable
fun BoxScope.GlassLayer(state: GlassState) {
    val target = state.rect ?: return
    val d = LocalDensity.current
    val x = remember { Animatable(target.left) }
    val y = remember { Animatable(target.top) }
    val w = remember { Animatable(target.width) }
    val h = remember { Animatable(target.height) }
    var shown by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(if (state.visible) 1f else 0f, tween(if (state.visible) 160 else 220), label = "glassAlpha")
    val glare = remember { Animatable(-1f) }

    // The card grows a little when highlighted: make the glass match that size
    val s = state.scale
    val tl = target.left - target.width * (s - 1) / 2
    val tt = target.top - target.height * (s - 1) / 2
    val tw = target.width * s
    val th = target.height * s

    LaunchedEffect(tl, tt, tw, th, state.visible) {
        if (!state.visible) { shown = false; return@LaunchedEffect }
        if (!shown) {
            // Appearing (not moving from somewhere): start right where it belongs
            x.snapTo(tl); y.snapTo(tt); w.snapTo(tw); h.snapTo(th); shown = true
        } else coroutineScope {
            val sp = spring<Float>(dampingRatio = 0.82f, stiffness = 520f)
            launch { x.animateTo(tl, sp) }
            launch { y.animateTo(tt, sp) }
            launch { w.animateTo(tw, sp) }
            launch { h.animateTo(th, sp) }
        }
    }
    // A light glare sweeps across each time the glass lands on something new
    LaunchedEffect(state.moves) { glare.snapTo(-0.6f); glare.animateTo(1.6f, tween(800, easing = FastOutSlowInEasing)) }

    if (alpha <= 0.01f) return
    Box(
        Modifier
            .offset { IntOffset(x.value.roundToInt(), y.value.roundToInt()) }
            .size(with(d) { w.value.toDp() }, with(d) { h.value.toDp() })
            .graphicsLayer { this.alpha = alpha }
            .clip(state.shape)
            .drawWithContent {
                // Frosted pane: brighter at the top, fading down
                drawRect(
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.20f),
                        0.45f to Color.White.copy(alpha = 0.06f),
                        1f to Color.White.copy(alpha = 0.03f),
                    )
                )
                val ww = size.width
                val gx = glare.value * ww
                drawRect(
                    Brush.linearGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.18f), Color.Transparent),
                        start = Offset(gx - ww * 0.35f, 0f),
                        end = Offset(gx + ww * 0.05f, size.height),
                    )
                )
            }
            .border(
                2.dp,
                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.9f), Nova.Cyan.copy(alpha = 0.6f), Color.White.copy(alpha = 0.3f))),
                state.shape,
            )
    )
}

