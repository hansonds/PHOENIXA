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
    val alpha by animateFloatAsState(if (state.visible) 1f else 0f, tween(if (state.visible) 120 else 200), label = "glassAlpha")
    val glare = remember { Animatable(-1f) }

    // The card grows a little when highlighted: make the glass match that size
    val s = state.scale
    val tl = target.left - target.width * (s - 1) / 2
    val tt = target.top - target.height * (s - 1) / 2
    val tw = target.width * s
    val th = target.height * s
    val latest = androidx.compose.runtime.rememberUpdatedState(Rect(tl, tt, tl + tw, tt + th))

    // The glass is drawn at "the card + a gap that shrinks to zero". When the page scrolls the card moves and
    // the glass moves with it straight away (no lag); only the jump from the old card to the new one is animated.
    val dx = remember { Animatable(0f) }
    val dy = remember { Animatable(0f) }
    val dw = remember { Animatable(0f) }
    val dh = remember { Animatable(0f) }
    val drawn = remember { arrayOfNulls<Rect>(1) }

    LaunchedEffect(state.moves) {
        val prev = drawn[0]
        val now = latest.value
        if (prev == null || !state.visible) {
            dx.snapTo(0f); dy.snapTo(0f); dw.snapTo(0f); dh.snapTo(0f)
        } else coroutineScope {
            dx.snapTo(prev.left - now.left); dy.snapTo(prev.top - now.top)
            dw.snapTo(prev.width - now.width); dh.snapTo(prev.height - now.height)
            // A quick glide you can still see (about a quarter of a second), settling with a tiny bounce
            val hop = spring<Float>(dampingRatio = 0.74f, stiffness = 260f)
            launch { dx.animateTo(0f, hop) }
            launch { dy.animateTo(0f, hop) }
            launch { dw.animateTo(0f, hop) }
            launch { dh.animateTo(0f, hop) }
        }
        glare.snapTo(-0.6f)
        glare.animateTo(1.6f, tween(650, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(state.visible) { if (!state.visible) drawn[0] = null }

    val gx = tl + dx.value
    val gy = tt + dy.value
    val gw = (tw + dw.value).coerceAtLeast(1f)
    val gh = (th + dh.value).coerceAtLeast(1f)
    if (state.visible) drawn[0] = Rect(gx, gy, gx + gw, gy + gh)

    if (alpha <= 0.01f) return
    Box(
        Modifier
            .offset { IntOffset(gx.roundToInt(), gy.roundToInt()) }
            .size(with(d) { gw.toDp() }, with(d) { gh.toDp() })
            .graphicsLayer { this.alpha = alpha }
            .clip(state.shape)
            .drawWithContent {
                // Frosted pane: clearly brighter at the top, fading down
                drawRect(
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.22f),
                        0.35f to Color.White.copy(alpha = 0.06f),
                        1f to Color.Transparent,
                    )
                )
                val ww = size.width
                val sx = glare.value * ww
                drawRect(
                    Brush.linearGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.30f), Color.Transparent),
                        start = Offset(sx - ww * 0.35f, 0f),
                        end = Offset(sx + ww * 0.05f, size.height),
                    )
                )
            }
            .border(
                3.dp,
                Brush.verticalGradient(listOf(Color.White, Color.White.copy(alpha = 0.85f), Nova.Cyan.copy(alpha = 0.9f))),
                state.shape,
            )
    )
}
