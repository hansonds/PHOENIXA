package org.phoenicx.nova.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.phoenicx.nova.App
import org.phoenicx.nova.Navigator
import org.phoenicx.nova.Screen
import org.phoenicx.nova.data.ApiException
import org.phoenicx.nova.data.Item
import org.phoenicx.nova.player.Player
import org.phoenicx.nova.ui.Badge
import org.phoenicx.nova.ui.LandscapeCard
import org.phoenicx.nova.ui.Loader
import org.phoenicx.nova.ui.MediaRow
import org.phoenicx.nova.ui.Message
import org.phoenicx.nova.ui.Nova
import org.phoenicx.nova.ui.NovaButton
import org.phoenicx.nova.ui.NovaType
import org.phoenicx.nova.ui.PosterCard
import org.phoenicx.nova.ui.UiScale
import org.phoenicx.nova.ui.clickableHand
import org.phoenicx.nova.ui.formatRuntime
import org.phoenicx.nova.ui.rememberHover

private data class HomeRow(val title: String, val items: List<Item>, val landscape: Boolean)

@Composable
fun HomeScreen(nav: Navigator) {
    var featured by remember { mutableStateOf<List<Item>>(emptyList()) }
    var rows by remember { mutableStateOf<List<HomeRow>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(App.refreshTick) {
        try {
            coroutineScope {
                val api = App.api
                val libs = runCatching { api.libraries() }.getOrElse { e ->
                    if (e is ApiException && e.code == 401) { App.signOut(); return@coroutineScope }
                    throw e
                }
                App.libraries = libs
                val featD = async { runCatching { api.featured(10) }.getOrDefault(emptyList()) }
                val resumeD = async { runCatching { api.resume() }.getOrDefault(emptyList()) }
                val nextD = async { runCatching { api.nextUp() }.getOrDefault(emptyList()) }
                val favD = async { runCatching { api.favorites() }.getOrDefault(emptyList()) }
                val latestD = libs.filter { it.collectionType == "movies" || it.collectionType == "tvshows" || it.collectionType == null }
                    .map { lib -> lib to async { runCatching { api.latest(lib.id) }.getOrDefault(emptyList()) } }
                if (featured.isEmpty()) featured = featD.await()
                val out = mutableListOf<HomeRow>()
                out += HomeRow("Continue Watching", resumeD.await(), true)
                out += HomeRow("Next Up", nextD.await(), true)
                latestD.forEach { (lib, d) -> out += HomeRow("Recently Added in ${lib.name}", d.await(), false) }
                out += HomeRow("My Favorites", favD.await(), false)
                rows = out.filter { it.items.isNotEmpty() }
                error = null
            }
        } catch (e: Exception) {
            error = e.message ?: "Couldn't reach the server"
        }
        loading = false
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val heroHeight = (maxHeight * 0.70f).coerceIn(440.dp, 760.dp)
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                if (featured.isNotEmpty()) Hero(featured, heroHeight, nav)
                else Spacer(Modifier.height(96.dp))
            }
            if (loading && rows.isEmpty()) item { Loader() }
            error?.let { msg -> item { Message(msg) } }
            items(rows) { row ->
                if (row.landscape) {
                    MediaRow(row.title, row.items, UiScale.landscapeWidth.dp) { item ->
                        LandscapeCard(item, UiScale.landscapeWidth.dp) { open(nav, item, playIfEpisode = true) }
                    }
                } else {
                    MediaRow(row.title, row.items, UiScale.posterWidth.dp) { item ->
                        PosterCard(item, UiScale.posterWidth.dp) { open(nav, item) }
                    }
                }
            }
            item { Spacer(Modifier.height(48.dp)) }
        }
    }
}

/** Opens the detail page, or plays straight away for Continue Watching / Next Up episodes */
fun open(nav: Navigator, item: Item, playIfEpisode: Boolean = false) {
    if (playIfEpisode && (item.isEpisode || item.progress > 0f)) Player.play(item)
    else nav.go(Screen.Detail(item.id))
}

@Composable
private fun Hero(items: List<Item>, height: Dp, nav: Navigator) {
    var index by remember { mutableIntStateOf(0) }
    val (src, hovered) = rememberHover()
    // Rotates every 9 s; clicking an arrow or dot restarts the timer
    LaunchedEffect(index, items.size) {
        if (!App.prefs.heroAutoRotate || items.size < 2) return@LaunchedEffect
        delay(9000)
        index = (index + 1) % items.size
    }
    val item = items[index.coerceIn(0, items.lastIndex)]
    Box(Modifier.fillMaxWidth().height(height).hoverable(src)) {
        AnimatedContent(item, transitionSpec = { fadeIn(tween(700)) togetherWith fadeOut(tween(700)) }, label = "hero") { cur ->
            Box(Modifier.fillMaxSize()) {
                AsyncImage(
                    model = App.api.backdrop(cur, 1920), contentDescription = null,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                )
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Nova.Bg.copy(alpha = 0.95f), 0.45f to Nova.Bg.copy(alpha = 0.55f), 0.75f to Color.Transparent)))
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Nova.Bg.copy(alpha = 0.5f), 0.25f to Color.Transparent, 0.7f to Color.Transparent, 1f to Nova.Bg)))
                HeroInfo(cur, nav, Modifier.align(Alignment.BottomStart).padding(start = 64.dp, bottom = 56.dp))
            }
        }
        if (items.size > 1) {
            Row(Modifier.align(Alignment.BottomEnd).padding(end = 64.dp, bottom = 64.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items.indices.forEach { i ->
                    Box(
                        Modifier
                            .height(6.dp)
                            .width(if (i == index) 28.dp else 10.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (i == index) Nova.Cyan else Color(0x55FFFFFF))
                            .clickableHand { index = i }
                    )
                }
            }
            if (hovered) {
                HeroArrow("‹", Modifier.align(Alignment.CenterStart).padding(start = 14.dp)) { index = (index - 1 + items.size) % items.size }
                HeroArrow("›", Modifier.align(Alignment.CenterEnd).padding(end = 14.dp)) { index = (index + 1) % items.size }
            }
        }
    }
}

@Composable
private fun HeroArrow(symbol: String, modifier: Modifier, onClick: () -> Unit) {
    val (src, hovered) = rememberHover()
    Box(
        modifier.size(52.dp).clip(CircleShape)
            .background(if (hovered) Nova.Focus else Color(0x55000000))
            .hoverable(src).clickableHand(onClick),
        contentAlignment = Alignment.Center,
    ) { Text(symbol, style = NovaType.Display.copy(fontSize = 34.sp, lineHeight = 34.sp), color = if (hovered) Nova.OnFocus else Color.White) }
}

@Composable
private fun HeroInfo(item: Item, nav: Navigator, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    var fav by remember(item.id) { mutableStateOf(item.isFavorite) }
    Column(modifier.widthIn(max = 640.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("✦  FEATURED " + if (item.isSeries) "SERIES" else "FILM", style = NovaType.Caption, color = Nova.Cyan)
        }
        Spacer(Modifier.height(14.dp))
        TitleOrLogo(item, 120.dp, 520.dp)
        Spacer(Modifier.height(16.dp))
        MetaLine(item)
        if (item.genres.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(item.genres.take(3).joinToString(" • "), style = NovaType.Button, color = Nova.Text)
        }
        if (item.overview.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(item.overview, style = NovaType.Body, color = Nova.TextDim, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            NovaButton(if (item.progress > 0f) "Resume" else "Play", Icons.Filled.PlayArrow, primary = true) { Player.play(item) }
            NovaButton("More Info", Icons.Outlined.Info) { nav.go(Screen.Detail(item.id)) }
            NovaButton(null, if (fav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder) {
                fav = !fav
                scope.launch { runCatching { App.api.setFavorite(item.id, fav) } }
            }
        }
    }
}

/** Title logo image if the server has one, otherwise big text */
@Composable
fun TitleOrLogo(item: Item, maxHeight: Dp, maxWidth: Dp) {
    val logo = App.api.logo(item, 800)
    if (logo != null) {
        AsyncImage(
            model = logo, contentDescription = item.name, contentScale = ContentScale.Fit,
            alignment = Alignment.CenterStart,
            modifier = Modifier.heightIn(max = maxHeight).widthIn(max = maxWidth),
        )
    } else {
        Text(item.name, style = NovaType.Display, color = Nova.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** ★ 7.5 · 🍅 92% · 2021 · 2h 12m · PG-13 · HD · 5.1 */
@Composable
fun MetaLine(item: Item) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        item.communityRating?.let {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Star, null, tint = Nova.Gold, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(String.format("%.1f", it), style = NovaType.Button, color = Nova.Text)
            }
        }
        item.criticRating?.let { Text("🍅 $it%", style = NovaType.Button, color = Nova.Text) }
        item.year?.let { Text("$it", style = NovaType.Button, color = Nova.TextDim) }
        formatRuntime(item.runtimeMinutes).takeIf { it.isNotEmpty() && !item.isSeries }?.let { Text(it, style = NovaType.Button, color = Nova.TextDim) }
        item.officialRating?.let { Badge(it) }
        item.qualityBadges.take(4).forEach { Badge(it, accent = true) }
    }
}
