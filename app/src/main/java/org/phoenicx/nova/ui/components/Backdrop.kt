package org.phoenicx.nova.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import org.phoenicx.nova.ui.theme.Nova

/** Full-bleed cinematic backdrop with slow Ken Burns drift and layered scrims. */
@Composable
fun BackdropLayer(url: String?, kenBurns: Boolean, dim: Float, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "kb")
    val drift by t.animateFloat(
        1.0f, 1.09f,
        infiniteRepeatable(tween(22_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "drift",
    )
    val dimA by animateFloatAsState(dim, tween(500), label = "dim")

    Box(modifier.fillMaxSize()) {
        Crossfade(targetState = url, animationSpec = tween(800), label = "backdrop") { u ->
            if (u != null) {
                AsyncImage(
                    model = u,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val s = if (kenBurns) drift else 1.04f
                            scaleX = s; scaleY = s
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.7f, 0.3f)
                        },
                )
            }
        }
        // left scrim for text legibility
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to Nova.Bg.copy(alpha = 0.94f),
                    0.38f to Nova.Bg.copy(alpha = 0.62f),
                    0.72f to Color.Transparent,
                )
            )
        )
        // bottom fade into page
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Nova.Bg.copy(alpha = 0.55f),
                    0.18f to Color.Transparent,
                    0.55f to Color.Transparent,
                    0.86f to Nova.Bg.copy(alpha = 0.92f),
                    1f to Nova.Bg,
                )
            )
        )
        // aurora tint
        Box(
            Modifier.fillMaxSize().background(
                Brush.linearGradient(listOf(Color(0x1A6CE5F5), Color.Transparent, Color(0x1FA58BFF)))
            )
        )
        Box(Modifier.fillMaxSize().background(Nova.BgDeep.copy(alpha = dimA)))
    }
}
