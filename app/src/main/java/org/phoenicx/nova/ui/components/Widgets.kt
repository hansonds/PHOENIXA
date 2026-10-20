package org.phoenicx.nova.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import org.phoenicx.nova.NovaApp
import org.phoenicx.nova.data.Item
import org.phoenicx.nova.data.Person
import org.phoenicx.nova.ui.theme.Nova
import org.phoenicx.nova.ui.theme.NovaType

val api: org.phoenicx.nova.data.JellyfinApi get() = NovaApp.instance.api

@Composable
fun NovaButton(
    text: String,
    icon: ImageVector? = null,
    primary: Boolean = false,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onFocused: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    FocusSurface(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(24.dp),
        focusedScale = 1.06f,
        borderWidth = 0.dp,
        focusRequester = focusRequester,
        onFocused = onFocused,
    ) { focused ->
        val bg: Brush = when {
            focused -> Brush.horizontalGradient(listOf(Color(0xFFF4F7FF), Color(0xFFE4E9FF)))
            primary -> Brush.horizontalGradient(listOf(Color(0x40FFFFFF), Color(0x26FFFFFF)))
            else -> Brush.horizontalGradient(listOf(Color(0x24FFFFFF), Color(0x14FFFFFF)))
        }
        val fg = if (focused) Color(0xFF12152A) else Nova.Text
        Row(
            Modifier
                .fillMaxHeight()
                .background(bg)
                .border(1.dp, if (focused) Color.Transparent else Nova.Stroke, RoundedCornerShape(24.dp))
                .padding(horizontal = if (text.isEmpty()) 12.dp else 22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) Icon(icon, null, tint = fg, modifier = Modifier.size(22.dp))
            if (icon != null && text.isNotEmpty()) Spacer(Modifier.width(10.dp))
            if (text.isNotEmpty()) Text(text, style = NovaType.Button, color = fg)
        }
    }
}

@Composable
fun GlassChip(text: String, modifier: Modifier = Modifier, accent: Color? = null) {
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (accent != null) accent.copy(alpha = 0.16f) else Color(0x1FFFFFFF))
            .border(1.dp, accent?.copy(alpha = 0.45f) ?: Nova.Stroke, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text, style = NovaType.Label, color = accent ?: Nova.Text)
    }
}

@Composable
fun MetaRow(item: Item, modifier: Modifier = Modifier, showEpisode: Boolean = true) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        item.communityRating?.let {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Star, null, tint = Nova.Gold, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(String.format("%.1f", it), style = NovaType.Label, color = Nova.Text)
            }
        }
        item.criticRating?.let {
            Text("🍅 $it%", style = NovaType.Label, color = Nova.Text)
        }
        item.year?.let { Text("$it", style = NovaType.Label, color = Nova.TextDim) }
        if (showEpisode) item.episodeLabel?.let { Text(it, style = NovaType.Label, color = Nova.Cyan) }
        when {
            item.isSeries && item.childCount != null ->
                Text("${item.childCount} Season${if (item.childCount == 1) "" else "s"}", style = NovaType.Label, color = Nova.TextDim)
            item.runtimeMinutes > 0 -> Text(formatRuntime(item.runtimeMinutes), style = NovaType.Label, color = Nova.TextDim)
        }
        item.officialRating?.let { GlassChip(it) }
        item.qualityBadges.forEach { GlassChip(it, accent = Nova.Cyan) }
    }
}

fun formatRuntime(min: Int): String = if (min >= 60) "${min / 60}h ${min % 60}m" else "${min}m"

fun formatTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, sec) else String.format("%d:%02d", m, sec)
}

@Composable
fun ProgressBar(progress: Float, modifier: Modifier = Modifier, height: Int = 4) {
    Box(
        modifier
            .height(height.dp)
            .clip(RoundedCornerShape(height.dp))
            .background(Color(0x40FFFFFF))
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .clip(RoundedCornerShape(height.dp))
                .background(Nova.AccentBrushH)
        )
    }
}

@Composable
fun ShimmerBox(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "shimmer")
    val a by t.animateFloat(0.35f, 0.7f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse), label = "a")
    Box(modifier.alpha(a).background(Nova.SurfaceHi))
}

@Composable
fun PosterCard(
    item: Item,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Int = 150,
    showTitle: Boolean = false,
    focusRequester: FocusRequester? = null,
    onFocused: (() -> Unit)? = null,
) {
    Column(modifier.width(width.dp)) {
        FocusSurface(
            onClick = onClick,
            modifier = Modifier.size(width.dp, (width * 1.5f).dp),
            focusRequester = focusRequester,
            onFocused = onFocused,
        ) { focused ->
            ShimmerBox(Modifier.fillMaxSize())
            AsyncImage(
                model = api.poster(item, 360),
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (api.poster(item) == null) {
                Text(item.name, style = NovaType.Section, color = Nova.Text, textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(12.dp))
            }
            if (item.played) {
                Icon(Icons.Filled.CheckCircle, null, tint = Nova.Cyan,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(22.dp)
                        .background(Color(0x99121522), CircleShape))
            } else if ((item.unplayedCount ?: 0) > 0) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(8.dp)
                        .background(Nova.AccentBrush, RoundedCornerShape(10.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) { Text("${item.unplayedCount}", style = NovaType.Label, color = Color(0xFF12152A), fontWeight = FontWeight.Bold) }
            }
            if (item.progress > 0f && !item.played) {
                ProgressBar(item.progress, Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(8.dp))
            }
        }
        if (showTitle) {
            Spacer(Modifier.height(8.dp))
            Text(item.name, style = NovaType.Label, color = Nova.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.year?.toString() ?: "", style = NovaType.Label.copy(fontSize = 12.sp), color = Nova.TextFaint, maxLines = 1)
        }
    }
}

@Composable
fun LandscapeCard(
    item: Item,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Int = 268,
    subtitle: String? = null,
    focusRequester: FocusRequester? = null,
    onFocused: (() -> Unit)? = null,
) {
    FocusSurface(
        onClick = onClick,
        modifier = modifier.size(width.dp, (width * 9f / 16f).dp),
        focusRequester = focusRequester,
        onFocused = onFocused,
    ) { focused ->
        ShimmerBox(Modifier.fillMaxSize())
        AsyncImage(
            model = api.landscape(item, 640),
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color(0xE6101320))
            )
        )
        Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
            Text(
                if (item.isEpisode) item.seriesName ?: item.name else item.name,
                style = NovaType.Label.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                color = Nova.Text, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            val sub = subtitle ?: if (item.isEpisode) listOfNotNull(item.episodeLabel, item.name).joinToString(" · ") else item.year?.toString()
            if (!sub.isNullOrEmpty()) {
                Text(sub, style = NovaType.Label.copy(fontSize = 12.sp), color = Nova.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (item.progress > 0f && !item.played) {
                Spacer(Modifier.height(8.dp))
                ProgressBar(item.progress, Modifier.fillMaxWidth(), height = 3)
            }
        }
    }
}

@Composable
fun PersonCard(person: Person, onClick: () -> Unit = {}) {
    Column(Modifier.width(120.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        FocusSurface(onClick = onClick, modifier = Modifier.size(96.dp), shape = CircleShape, focusedScale = 1.1f) {
            Box(Modifier.fillMaxSize().background(Nova.SurfaceHi), contentAlignment = Alignment.Center) {
                Text(person.name.take(1), style = NovaType.Title, color = Nova.TextDim)
            }
            AsyncImage(
                model = api.personImage(person),
                contentDescription = person.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(person.name, style = NovaType.Label, color = Nova.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        Text(person.role.ifEmpty { person.type }, style = NovaType.Label.copy(fontSize = 11.sp), color = Nova.TextFaint,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 4.dp, height = 18.dp).clip(RoundedCornerShape(2.dp)).background(Nova.AccentBrush))
        Spacer(Modifier.width(10.dp))
        Text(text, style = NovaType.Section, color = Nova.Text)
    }
}

@Composable
fun CenterMessage(title: String, subtitle: String? = null, action: (@Composable () -> Unit)? = null) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = NovaType.Title, color = Nova.Text)
        if (subtitle != null) {
            Spacer(Modifier.height(8.dp))
            Text(subtitle, style = NovaType.Body, color = Nova.TextDim, textAlign = TextAlign.Center)
        }
        if (action != null) {
            Spacer(Modifier.height(24.dp))
            action()
        }
    }
}

@Composable
fun NovaLoader(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "loader")
    val rot by t.animateFloat(0f, 360f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "r")
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.size(56.dp)) {
            drawArc(
                brush = Brush.sweepGradient(listOf(Color.Transparent, Nova.Cyan, Nova.Violet)),
                startAngle = rot, sweepAngle = 300f, useCenter = false,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round),
            )
        }
    }
}
