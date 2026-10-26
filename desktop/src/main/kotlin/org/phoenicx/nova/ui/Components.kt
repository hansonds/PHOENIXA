package org.phoenicx.nova.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import org.phoenicx.nova.App
import org.phoenicx.nova.data.Item
import org.phoenicx.nova.data.Person

/** Clickable with a hand cursor */
fun Modifier.clickableHand(onClick: () -> Unit): Modifier =
    this.pointerHoverIcon(PointerIcon.Hand).clickable(onClick = onClick)

@Composable
fun rememberHover(): Pair<MutableInteractionSource, Boolean> {
    val src = remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    return src to hovered
}

/** Rounded button. Primary = accent gradient, otherwise glass; brightens on hover. */
@Composable
fun NovaButton(
    text: String?,
    icon: ImageVector? = null,
    primary: Boolean = false,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val (src, hovered) = rememberHover()
    val shape = RoundedCornerShape(26.dp)
    val bg: Modifier = when {
        primary -> Modifier.background(Nova.AccentBrushH, shape)
        hovered -> Modifier.background(Nova.Focus, shape)
        else -> Modifier.background(Color(0x1FFFFFFF), shape)
    }
    val fg = if (primary || hovered) Nova.OnFocus else Nova.Text
    Row(
        modifier
            .scale(if (hovered && enabled) 1.03f else 1f)
            .clip(shape)
            .then(bg)
            .border(1.dp, if (primary || hovered) Color.Transparent else Nova.Stroke, shape)
            .hoverable(src)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = if (text == null) 12.dp else 22.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) Icon(icon, text, tint = fg, modifier = Modifier.size(20.dp))
        if (text != null) Text(text, style = NovaType.Button, color = fg, maxLines = 1)
    }
}

/** Small selectable pill (sort options, filters, seasons) */
@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    val (src, hovered) = rememberHover()
    val shape = RoundedCornerShape(20.dp)
    Box(
        Modifier
            .clip(shape)
            .background(
                when {
                    selected -> Nova.Cyan.copy(alpha = 0.22f)
                    hovered -> Color(0x26FFFFFF)
                    else -> Color(0x12FFFFFF)
                }, shape
            )
            .border(1.dp, if (selected) Nova.Cyan.copy(alpha = 0.7f) else Nova.Stroke, shape)
            .hoverable(src)
            .clickableHand(onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(text, style = NovaType.Label, color = if (selected) Nova.Text else Nova.TextDim, maxLines = 1)
    }
}

@Composable
fun Loader(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Nova.Cyan, strokeWidth = 3.dp, modifier = Modifier.size(36.dp))
    }
}

@Composable
fun Message(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
        Text(text, style = NovaType.Body, color = Nova.TextDim)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(4.dp).height(20.dp).background(Nova.AccentBrush, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(10.dp))
        Text(text, style = NovaType.Section, color = Nova.Text)
    }
}

@Composable
fun ProgressBar(progress: Float, modifier: Modifier = Modifier) {
    Box(modifier.height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x40FFFFFF))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0f, 1f)).background(Nova.AccentBrushH))
    }
}

/** Image with a dark placeholder behind it */
@Composable
fun NetImage(url: String?, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop, fallback: ImageVector = Icons.Outlined.Movie) {
    Box(modifier.background(Nova.Surface), contentAlignment = Alignment.Center) {
        Icon(fallback, null, tint = Nova.TextFaint, modifier = Modifier.size(36.dp))
        if (url != null) AsyncImage(model = url, contentDescription = null, contentScale = contentScale, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun cardFrame(hovered: Boolean): Modifier {
    val s by animateFloatAsState(if (hovered) 1.045f else 1f, tween(160))
    return Modifier.scale(s)
}

/** Portrait poster with title and year */
@Composable
fun PosterCard(item: Item, width: Dp, onClick: () -> Unit) {
    val (src, hovered) = rememberHover()
    val shape = RoundedCornerShape(12.dp)
    Column(Modifier.width(width).hoverable(src).clickableHand(onClick)) {
        Box(
            cardFrame(hovered)
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(shape)
                .border(if (hovered) 2.dp else 1.dp, if (hovered) Nova.Cyan else Nova.Stroke, shape)
        ) {
            NetImage(App.api.poster(item, 400), Modifier.fillMaxSize())
            if (item.played) {
                Icon(Icons.Filled.CheckCircle, "Watched", tint = Nova.Cyan, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(22.dp))
            } else if ((item.unplayedCount ?: 0) > 0 && item.isSeries) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(8.dp).background(Nova.Cyan, RoundedCornerShape(10.dp)).padding(horizontal = 7.dp, vertical = 2.dp)
                ) { Text("${item.unplayedCount}", style = NovaType.Label, color = Nova.OnFocus, fontWeight = FontWeight.Bold) }
            }
            if (item.progress > 0f && !item.played) {
                ProgressBar(item.progress, Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(8.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(item.name, style = NovaType.Button, color = if (hovered) Nova.Text else Nova.Text.copy(alpha = 0.92f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            listOfNotNull(item.year?.toString(), item.officialRating).joinToString(" · ").ifEmpty { " " },
            style = NovaType.Label, color = Nova.TextFaint, maxLines = 1,
        )
    }
}

/** 16:9 still with title on top (Continue Watching, episodes, Next Up) */
@Composable
fun LandscapeCard(item: Item, width: Dp, onClick: () -> Unit) {
    val (src, hovered) = rememberHover()
    val shape = RoundedCornerShape(12.dp)
    Box(
        cardFrame(hovered)
            .width(width)
            .aspectRatio(16f / 9f)
            .clip(shape)
            .border(if (hovered) 2.dp else 1.dp, if (hovered) Nova.Cyan else Nova.Stroke, shape)
            .hoverable(src)
            .clickableHand(onClick)
    ) {
        NetImage(App.api.landscape(item, 640), Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color(0xE6000000))))
        Column(Modifier.align(Alignment.BottomStart).padding(12.dp).fillMaxWidth()) {
            val top = if (item.isEpisode) listOfNotNull(item.seriesName, item.episodeLabel).joinToString(" · ") else null
            if (top != null) Text(top, style = NovaType.Label, color = Nova.Cyan, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.name, style = NovaType.Button, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (item.progress > 0f && !item.played) {
                Spacer(Modifier.height(6.dp))
                ProgressBar(item.progress, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
fun PersonCard(p: Person, onClick: () -> Unit) {
    val (src, hovered) = rememberHover()
    Column(Modifier.width(120.dp).hoverable(src).clickableHand(onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            cardFrame(hovered).size(104.dp).clip(CircleShape)
                .border(2.dp, if (hovered) Nova.Cyan else Nova.Stroke, CircleShape)
        ) {
            NetImage(App.api.personImage(p, 240), Modifier.fillMaxSize(), fallback = Icons.Outlined.Person)
        }
        Spacer(Modifier.height(8.dp))
        Text(p.name, style = NovaType.Label, color = Nova.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(p.role.ifBlank { p.type }, style = NovaType.Label, color = Nova.TextFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A titled horizontal row with arrow buttons that appear when you hover over it */
@Composable
fun <T> MediaRow(
    title: String,
    items: List<T>,
    itemWidth: Dp,
    sidePadding: Dp = 48.dp,
    content: @Composable (T) -> Unit,
) {
    if (items.isEmpty()) return
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val (src, hovered) = rememberHover()
    Column(Modifier.fillMaxWidth().padding(top = 26.dp)) {
        SectionTitle(title, Modifier.padding(start = sidePadding, bottom = 12.dp))
        BoxWithConstraints(Modifier.fillMaxWidth().hoverable(src)) {
            val page = constraints.maxWidth * 0.8f
            LazyRow(
                state = state,
                contentPadding = PaddingValues(horizontal = sidePadding, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                items(items) { content(it) }
            }
            if (hovered && state.canScrollBackward) {
                RowArrow(Icons.Outlined.ChevronLeft, Modifier.align(Alignment.CenterStart).padding(start = 8.dp)) {
                    scope.launch { state.animateScrollBy(-page) }
                }
            }
            if (hovered && state.canScrollForward) {
                RowArrow(Icons.Outlined.ChevronRight, Modifier.align(Alignment.CenterEnd).padding(end = 8.dp)) {
                    scope.launch { state.animateScrollBy(page) }
                }
            }
        }
    }
}

@Composable
private fun RowArrow(icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    val (src, hovered) = rememberHover()
    Box(
        modifier.size(44.dp).clip(CircleShape)
            .background(if (hovered) Nova.Focus else Nova.Bg.copy(alpha = 0.85f))
            .border(1.dp, Nova.Stroke, CircleShape)
            .hoverable(src)
            .clickableHand(onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = if (hovered) Nova.OnFocus else Nova.Text, modifier = Modifier.size(28.dp)) }
}

/** Little outlined tag, e.g. "4K", "HDR10", "5.1" */
@Composable
fun Badge(text: String, accent: Boolean = false) {
    val c = if (accent) Nova.Cyan else Nova.TextDim
    Box(Modifier.border(1.dp, c.copy(alpha = 0.6f), RoundedCornerShape(8.dp)).padding(horizontal = 9.dp, vertical = 3.dp)) {
        Text(text, style = NovaType.Label, color = c)
    }
}

fun formatRuntime(minutes: Int): String = when {
    minutes <= 0 -> ""
    minutes < 60 -> "${minutes}m"
    else -> "${minutes / 60}h ${minutes % 60}m"
}
