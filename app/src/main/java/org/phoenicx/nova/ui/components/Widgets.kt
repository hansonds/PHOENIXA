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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.drawBehind
import kotlinx.coroutines.launch
import org.phoenicx.nova.ui.theme.Lay
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
            focused -> Brush.horizontalGradient(listOf(Nova.Focus, Nova.Focus))
            primary -> Brush.horizontalGradient(listOf(Color(0x40FFFFFF), Color(0x26FFFFFF)))
            else -> Brush.horizontalGradient(listOf(Color(0x24FFFFFF), Color(0x14FFFFFF)))
        }
        val fg = if (focused) Nova.OnFocus else Nova.Text
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
        Text(text, style = NovaType.Label, color = accent ?: Nova.Text, maxLines = 1, softWrap = false)
    }
}

@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
fun MetaRow(item: Item, modifier: Modifier = Modifier, showEpisode: Boolean = true) {
    // Phones: tags that don't fit move to a second line instead of being squeezed
    androidx.compose.foundation.layout.FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(if (org.phoenicx.nova.ui.theme.Lay.phone) 10.dp else 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item.communityRating?.let {
            Row(Modifier.align(Alignment.CenterVertically), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Star, null, tint = Nova.Gold, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(String.format("%.1f", it), style = NovaType.Label, color = Nova.Text)
            }
        }
        item.criticRating?.let {
            Text("🍅 $it%", modifier = Modifier.align(Alignment.CenterVertically), style = NovaType.Label, color = Nova.Text)
        }
        item.year?.let { Text("$it", modifier = Modifier.align(Alignment.CenterVertically), style = NovaType.Label, color = Nova.TextDim) }
        if (showEpisode) item.episodeLabel?.let { Text(it, modifier = Modifier.align(Alignment.CenterVertically), style = NovaType.Label, color = Nova.Cyan) }
        when {
            item.isSeries && item.childCount != null ->
                Text("${item.childCount} Season${if (item.childCount == 1) "" else "s"}", modifier = Modifier.align(Alignment.CenterVertically), style = NovaType.Label, color = Nova.TextDim)
            item.runtimeMinutes > 0 -> Text(formatRuntime(item.runtimeMinutes), modifier = Modifier.align(Alignment.CenterVertically), style = NovaType.Label, color = Nova.TextDim)
        }
        item.officialRating?.let { GlassChip(it, Modifier.align(Alignment.CenterVertically)) }
        item.qualityBadges.forEach { GlassChip(it, Modifier.align(Alignment.CenterVertically), accent = Nova.Cyan) }
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

/** Grey placeholder with a soft light sweeping across it, shown until the picture arrives */
@Composable
fun ShimmerBox(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "shimmer")
    val x by t.animateFloat(-1f, 2f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "x")
    Box(
        modifier.drawBehind {
            drawRect(Nova.SurfaceHi.copy(alpha = 0.55f))
            val w = size.width
            drawRect(
                Brush.linearGradient(
                    listOf(Color.Transparent, Color.White.copy(alpha = 0.07f), Color.Transparent),
                    start = androidx.compose.ui.geometry.Offset(w * x - w * 0.6f, 0f),
                    end = androidx.compose.ui.geometry.Offset(w * x, size.height),
                )
            )
        }
    )
}

/** Library / search page while loading: a grid of empty poster shapes */
@Composable
fun SkeletonGrid(modifier: Modifier = Modifier) {
    val w = org.phoenicx.nova.ui.theme.UiScale.posterWidth
    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
        columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(w.dp),
        contentPadding = PaddingValues(start = Lay.side, end = Lay.side, top = 18.dp, bottom = 60.dp),
        horizontalArrangement = Arrangement.spacedBy(if (Lay.phone) 12.dp else 20.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
        userScrollEnabled = false,
        modifier = modifier.fillMaxSize(),
    ) {
        items(18) { SkeletonPoster(w) }
    }
}

@Composable
fun SkeletonPoster(width: Int, landscape: Boolean = false) {
    Column(Modifier.width(width.dp)) {
        ShimmerBox(Modifier.size(width.dp, if (landscape) (width * 0.5625f).dp else (width * 1.5f).dp).clip(RoundedCornerShape(14.dp)))
        if (!landscape) {
            Spacer(Modifier.height(10.dp))
            ShimmerBox(Modifier.width((width * 0.8f).dp).height(10.dp).clip(RoundedCornerShape(5.dp)))
            Spacer(Modifier.height(6.dp))
            ShimmerBox(Modifier.width((width * 0.4f).dp).height(8.dp).clip(RoundedCornerShape(4.dp)))
        }
    }
}

/** Home while loading: a banner block and a few rows of empty cards */
@Composable
fun SkeletonHome() {
    Column(Modifier.fillMaxSize().padding(top = 10.dp)) {
        Column(Modifier.padding(horizontal = Lay.side)) {
            ShimmerBox(Modifier.fillMaxWidth().height(if (Lay.phone) 220.dp else 280.dp).clip(RoundedCornerShape(22.dp)))
            Spacer(Modifier.height(22.dp))
        }
        repeat(2) { r ->
            Column(Modifier.padding(start = Lay.side)) {
                ShimmerBox(Modifier.width(160.dp).height(14.dp).clip(RoundedCornerShape(7.dp)))
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    val w = if (r == 0) org.phoenicx.nova.ui.theme.UiScale.landscapeWidth else org.phoenicx.nova.ui.theme.UiScale.posterWidth
                    repeat(8) { SkeletonPoster(w, landscape = r == 0) }
                }
                Spacer(Modifier.height(26.dp))
            }
        }
    }
}

/** Detail page while loading */
@Composable
fun SkeletonDetail() {
    Column(Modifier.fillMaxSize().padding(horizontal = Lay.side, vertical = 30.dp), verticalArrangement = Arrangement.Bottom) {
        ShimmerBox(Modifier.width(320.dp).height(54.dp).clip(RoundedCornerShape(12.dp)))
        Spacer(Modifier.height(16.dp))
        ShimmerBox(Modifier.width(240.dp).height(14.dp).clip(RoundedCornerShape(7.dp)))
        Spacer(Modifier.height(18.dp))
        repeat(3) {
            ShimmerBox(Modifier.fillMaxWidth(if (it == 2) 0.5f else 0.85f).height(12.dp).clip(RoundedCornerShape(6.dp)))
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ShimmerBox(Modifier.width(150.dp).height(48.dp).clip(RoundedCornerShape(24.dp)))
            ShimmerBox(Modifier.width(48.dp).height(48.dp).clip(CircleShape))
            ShimmerBox(Modifier.width(48.dp).height(48.dp).clip(CircleShape))
        }
        Spacer(Modifier.height(60.dp))
    }
}

/** Phones and tablets: pull the page down to reload it. TVs: just the page. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun TouchRefresh(onRefresh: suspend () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    if (org.phoenicx.nova.NovaApp.instance.isTv) { Box(modifier) { content() }; return }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    val state = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()
    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            refreshing = true
            scope.launch { runCatching { onRefresh() }; refreshing = false }
        },
        state = state,
        modifier = modifier,
        indicator = {
            androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator(
                state = state, isRefreshing = refreshing,
                containerColor = Nova.SurfaceHi, color = Nova.Cyan,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        },
    ) { content() }
}

@Composable
fun PosterCard(
    item: Item,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Int = org.phoenicx.nova.ui.theme.UiScale.posterWidth,
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
            tile = true,
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
                        .background(Nova.Bg.copy(alpha = 0.60f), CircleShape))
            } else if ((item.unplayedCount ?: 0) > 0) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(8.dp)
                        .background(Nova.AccentBrush, RoundedCornerShape(10.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) { Text("${item.unplayedCount}", style = NovaType.Label, color = Nova.OnFocus, fontWeight = FontWeight.Bold) }
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
    width: Int = org.phoenicx.nova.ui.theme.UiScale.landscapeWidth,
    subtitle: String? = null,
    focusRequester: FocusRequester? = null,
    onFocused: (() -> Unit)? = null,
) {
    FocusSurface(
        onClick = onClick,
        modifier = modifier.size(width.dp, (width * 9f / 16f).dp),
        focusRequester = focusRequester,
        onFocused = onFocused,
            tile = true,
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
                Brush.verticalGradient(0.35f to Color.Transparent, 1f to Nova.BgDeep.copy(alpha = 0.90f))
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
fun PersonCard(person: Person, focusRequester: FocusRequester? = null, onClick: () -> Unit = {}) {
    Column(Modifier.width(120.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        FocusSurface(onClick = onClick, modifier = Modifier.size(96.dp), shape = CircleShape, focusedScale = 1.1f, focusRequester = focusRequester) {
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
