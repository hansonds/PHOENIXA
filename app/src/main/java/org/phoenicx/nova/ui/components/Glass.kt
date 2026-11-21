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
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawOutline
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
    /** true right after a page change: disappear at once instead of fading where the old card was */
    var instant by mutableStateOf(false); private set
    /** the new card hasn't been laid out yet: draw nothing rather than a frame at the old card's spot */
    var pending by mutableStateOf(false); private set
    /** this move is from one movie/series/library tile to another: hop across instead of sliding */
    var hop by mutableStateOf(false); private set
    private var lastWasTile = false
    private var lastMoveAt = 0L
    private var owner: Any? = null
    private var waitingForPlace = false
    // The card that was picked when we left a page: it keeps its highlight while the old page fades out,
    // so it must not pull the glass back onto the new page
    private var left: Any? = null

    /** [placed]: whether we already know where the new tile is (a tile that only just scrolled in may not be placed yet) */
    fun claim(token: Any, shape: Shape, scale: Float, placed: Boolean = true, tile: Boolean = false) {
        if (token === left) return
        left = null; instant = false
        val changed = owner !== token
        owner = token; this.shape = shape; this.scale = scale; visible = true
        if (!changed) return
        // Hop only tile → tile. Holding an arrow down (moves come quickly) just glides, so it never looks jittery.
        val now = android.os.SystemClock.uptimeMillis()
        hop = tile && lastWasTile && now - lastMoveAt > 200
        lastWasTile = tile; lastMoveAt = now
        // Start the glide only once we know where to glide to, so it never just jumps
        if (placed) { pending = false; moves++ } else { waitingForPlace = true; pending = true }
    }

    fun update(token: Any, r: Rect) {
        if (owner !== token) return
        rect = r
        if (waitingForPlace) { waitingForPlace = false; pending = false; moves++ }
    }

    /** A new page is opening: hide the glass right away; it comes back on the new page's highlighted card */
    fun leavePage() {
        left = owner; owner = null; waitingForPlace = false; pending = false; lastWasTile = false
        instant = true; visible = false
    }

    /** [gone]: the card itself was removed (a closed menu, a reloaded list): vanish at once, no fading ghost */
    fun release(token: Any, gone: Boolean = false) {
        if (owner === token) { owner = null; waitingForPlace = false; pending = false; instant = gone; visible = false }
    }
}

val LocalGlass = staticCompositionLocalOf<GlassState?> { null }

@Composable
fun BoxScope.GlassLayer(state: GlassState) {
    val target = state.rect ?: return
    val d = LocalDensity.current
    val alpha by animateFloatAsState(if (state.visible) 1f else 0f, tween(if (state.visible) 120 else if (state.instant) 0 else 200), label = "glassAlpha")
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
    // Tile → tile hop: 0..1 along a small arc, then a soft squash when it lands
    val arc = remember { Animatable(1f) }
    val arcHeight = remember { floatArrayOf(0f) }
    val squash = remember { Animatable(0f) }

    LaunchedEffect(state.moves) {
        val prev = drawn[0]
        val now = latest.value
        if (prev == null || !state.visible) {
            dx.snapTo(0f); dy.snapTo(0f); dw.snapTo(0f); dh.snapTo(0f); arc.snapTo(1f); squash.snapTo(0f)
        } else if (state.hop) coroutineScope {
            dx.snapTo(prev.left - now.left); dy.snapTo(prev.top - now.top)
            dw.snapTo(prev.width - now.width); dh.snapTo(prev.height - now.height)
            squash.snapTo(0f)
            // Jump: rises a little and drops onto the new tile, overshoots a touch and springs back
            val dist = kotlin.math.abs(dx.value) + kotlin.math.abs(dy.value) * 0.5f
            arcHeight[0] = with(d) { 10.dp.toPx() } + minOf(with(d) { 16.dp.toPx() }, dist * 0.12f)
            arc.snapTo(0f)
            val bounce = spring<Float>(dampingRatio = 0.6f, stiffness = 300f)
            launch { dx.animateTo(0f, bounce) }
            launch { dy.animateTo(0f, bounce) }
            launch { dw.animateTo(0f, bounce) }
            launch { dh.animateTo(0f, bounce) }
            launch {
                arc.animateTo(1f, tween(260, easing = androidx.compose.animation.core.LinearEasing))
                // Landing: a quick soft squash
                squash.snapTo(1f)
                squash.animateTo(0f, spring(dampingRatio = 0.4f, stiffness = 700f))
            }
        } else coroutineScope {
            arc.snapTo(1f); squash.snapTo(0f)
            dx.snapTo(prev.left - now.left); dy.snapTo(prev.top - now.top)
            dw.snapTo(prev.width - now.width); dh.snapTo(prev.height - now.height)
            // A quick glide you can still see (about a quarter of a second), settling with a tiny bounce
            val hop = spring<Float>(dampingRatio = 0.8f, stiffness = 330f)
            launch { dx.animateTo(0f, hop) }
            launch { dy.animateTo(0f, hop) }
            launch { dw.animateTo(0f, hop) }
            launch { dh.animateTo(0f, hop) }
        }
        glare.snapTo(-0.6f)
        glare.animateTo(1.6f, tween(650, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(state.visible) { if (!state.visible) drawn[0] = null }

    val p = arc.value
    val lift = arcHeight[0] * 4f * p * (1f - p)
    val bx = tl + dx.value
    val by = tt + dy.value - lift
    val bw = (tw + dw.value).coerceAtLeast(1f)
    val bh = (th + dh.value).coerceAtLeast(1f)
    if (state.visible) drawn[0] = Rect(bx, by, bx + bw, by + bh)
    // Squash on landing: a little wider and shorter, resting on the tile's bottom edge
    val sq = squash.value * 0.05f
    val gw = bw * (1f + sq)
    val gh = bh * (1f - sq)
    val gx = bx - (gw - bw) / 2
    val gy = by + (bh - gh)

    if (alpha <= 0.01f || state.pending) return
    // Google TV style: a thin frosted glass frame around the highlighted tile, tinted with the theme colour,
    // plus a light glassy sheen over the tile itself
    val pad = with(d) { 4.dp.toPx() }
    val shape = state.shape
    val tint = Nova.Cyan   // the theme's main colour
    Box(
        Modifier
            .offset { IntOffset((gx - pad).roundToInt(), (gy - pad).roundToInt()) }
            .size(with(d) { (gw + pad * 2).toDp() }, with(d) { (gh + pad * 2).toDp() })
            .graphicsLayer {
                this.alpha = alpha
                compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
            }
            .drawWithContent {
                val inner = androidx.compose.ui.geometry.Size(size.width - pad * 2, size.height - pad * 2)
                // The tile is shown slightly bigger while highlighted, and so are its rounded corners
                val raw = shape.createOutline(
                    androidx.compose.ui.geometry.Size(inner.width / s, inner.height / s), layoutDirection, this,
                )
                val innerOutline: androidx.compose.ui.graphics.Outline
                val outerOutline: androidx.compose.ui.graphics.Outline
                if (raw is androidx.compose.ui.graphics.Outline.Rounded) {
                    // Same curve as the tile, and the outside edge curves a little wider by the frame's width,
                    // so the frame is the same thickness all the way round
                    val r = (raw.roundRect.topLeftCornerRadius.x * s).coerceAtMost(minOf(inner.width, inner.height) / 2)
                    innerOutline = androidx.compose.ui.graphics.Outline.Rounded(
                        androidx.compose.ui.geometry.RoundRect(0f, 0f, inner.width, inner.height, androidx.compose.ui.geometry.CornerRadius(r))
                    )
                    outerOutline = androidx.compose.ui.graphics.Outline.Rounded(
                        androidx.compose.ui.geometry.RoundRect(0f, 0f, size.width, size.height, androidx.compose.ui.geometry.CornerRadius(r + pad))
                    )
                } else {
                    innerOutline = shape.createOutline(inner, layoutDirection, this)
                    outerOutline = shape.createOutline(size, layoutDirection, this)
                }
                // Frame: frosted white with a very light shade of the theme colour
                drawOutline(
                    outerOutline,
                    Brush.verticalGradient(
                        listOf(
                            androidx.compose.ui.graphics.lerp(Color.White, tint, 0.18f).copy(alpha = 0.75f),
                            androidx.compose.ui.graphics.lerp(Color.White, tint, 0.35f).copy(alpha = 0.50f),
                            androidx.compose.ui.graphics.lerp(Color.White, tint, 0.45f).copy(alpha = 0.40f),
                        )
                    ),
                )
                // Thin bright rim on the outside edge
                drawOutline(
                    outerOutline,
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.95f), tint.copy(alpha = 0.6f))),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.2f.dp.toPx()),
                )
                translate(pad, pad) {
                    // Cut the tile out of the frame...
                    drawOutline(innerOutline, Color.Black, blendMode = androidx.compose.ui.graphics.BlendMode.Clear)
                    // ...then lay a light glass sheen over it: brighter at the top, a hint of the theme colour, clear below
                    drawOutline(
                        innerOutline,
                        Brush.verticalGradient(
                            0f to Color.White.copy(alpha = 0.20f),
                            0.30f to tint.copy(alpha = 0.07f),
                            0.6f to Color.Transparent,
                        ),
                    )
                    // A light glare passing across the glass when it lands
                    val ww = inner.width
                    val sx = glare.value * ww
                    drawOutline(
                        innerOutline,
                        Brush.linearGradient(
                            listOf(Color.Transparent, Color.White.copy(alpha = 0.22f), Color.Transparent),
                            start = Offset(sx - ww * 0.35f, 0f),
                            end = Offset(sx + ww * 0.05f, inner.height),
                        ),
                    )
                }
            }
    )
}
