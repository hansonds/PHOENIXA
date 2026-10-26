package org.phoenicx.nova.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.SurroundSound
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.phoenicx.nova.App
import org.phoenicx.nova.Navigator
import org.phoenicx.nova.Screen
import org.phoenicx.nova.data.Item
import org.phoenicx.nova.data.Lang
import org.phoenicx.nova.data.MediaSourceInfo
import org.phoenicx.nova.data.PlaySelection
import org.phoenicx.nova.player.Player
import org.phoenicx.nova.ui.Chip
import org.phoenicx.nova.ui.Loader
import org.phoenicx.nova.ui.MediaRow
import org.phoenicx.nova.ui.Message
import org.phoenicx.nova.ui.NetImage
import org.phoenicx.nova.ui.Nova
import org.phoenicx.nova.ui.NovaButton
import org.phoenicx.nova.ui.NovaType
import org.phoenicx.nova.ui.PersonCard
import org.phoenicx.nova.ui.PosterCard
import org.phoenicx.nova.ui.ProgressBar
import org.phoenicx.nova.ui.SectionTitle
import org.phoenicx.nova.ui.UiScale
import org.phoenicx.nova.ui.clickableHand
import org.phoenicx.nova.ui.formatRuntime
import org.phoenicx.nova.ui.rememberHover
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(nav: Navigator, id: String) {
    val api = App.api
    var detail by remember(id) { mutableStateOf<Item?>(null) }
    var error by remember(id) { mutableStateOf<String?>(null) }
    var seasons by remember(id) { mutableStateOf<List<Item>>(emptyList()) }
    var seasonId by remember(id) { mutableStateOf<String?>(null) }
    var episodes by remember(id) { mutableStateOf<List<Item>>(emptyList()) }
    var children by remember(id) { mutableStateOf<List<Item>>(emptyList()) }
    var similar by remember(id) { mutableStateOf<List<Item>>(emptyList()) }
    var nextUp by remember(id) { mutableStateOf<Item?>(null) }

    LaunchedEffect(id, App.refreshTick) {
        try {
            coroutineScope {
                val it = api.item(id)
                detail = it
                val simD = async { runCatching { api.similar(id) }.getOrDefault(emptyList()) }
                when {
                    it.isSeries -> {
                        seasons = runCatching { api.seasons(id) }.getOrDefault(emptyList())
                        nextUp = runCatching { api.nextUpForSeries(id) }.getOrNull()
                        if (seasonId == null) seasonId = nextUp?.seasonId ?: seasons.firstOrNull()?.id
                    }
                    it.isFolderLike || it.isCollection -> children = runCatching { api.children(id) }.getOrDefault(emptyList())
                }
                similar = if (it.isCollection) emptyList() else simD.await()
            }
        } catch (e: Exception) {
            error = e.message ?: "Couldn't load this title"
        }
    }
    LaunchedEffect(seasonId, App.refreshTick) {
        val sid = seasonId ?: return@LaunchedEffect
        episodes = runCatching { api.episodes(id, sid) }.getOrDefault(emptyList())
    }

    val it = detail
    if (it == null) {
        Column(Modifier.fillMaxSize().padding(top = 120.dp)) {
            if (error != null) Message(error!!) else Loader()
        }
        return
    }

    Box(Modifier.fillMaxSize()) {
        // Backdrop stays still while the page scrolls
        AsyncImage(
            model = api.backdrop(it, 1920), contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
        )
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Nova.Bg.copy(alpha = 0.96f), 0.5f to Nova.Bg.copy(alpha = 0.7f), 1f to Nova.Bg.copy(alpha = 0.25f))))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, 0.55f to Nova.Bg.copy(alpha = 0.85f), 0.8f to Nova.Bg)))

        LazyColumn(Modifier.fillMaxSize()) {
            item { Header(it, nav, nextUp) }

            if (it.isSeries && seasons.isNotEmpty()) {
                item {
                    Column(Modifier.padding(start = 64.dp, end = 64.dp, top = 32.dp)) {
                        SectionTitle("Episodes")
                        Spacer(Modifier.height(14.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            seasons.forEach { s -> Chip(s.name, s.id == seasonId) { seasonId = s.id } }
                        }
                    }
                }
                items(episodes) { ep -> EpisodeRow(ep) { Player.play(ep) } }
            }

            if (children.isNotEmpty()) {
                item {
                    Column(Modifier.padding(start = 64.dp, end = 64.dp, top = 32.dp)) {
                        SectionTitle(if (it.type == "BoxSet") "In this collection" else "Items")
                        Spacer(Modifier.height(16.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                            children.forEach { c ->
                                PosterCard(c, UiScale.posterWidth.dp) {
                                    if (c.isEpisode) Player.play(c) else nav.go(Screen.Detail(c.id))
                                }
                            }
                        }
                    }
                }
            }

            val cast = it.people.filter { p -> p.type == "Actor" || p.type == "Director" || p.type == "Writer" }.take(30)
            if (cast.isNotEmpty()) item {
                MediaRow("Cast & Crew", cast, 120.dp, sidePadding = 64.dp) { p ->
                    PersonCard(p) { nav.go(Screen.Person(p.id, p.name)) }
                }
            }
            if (similar.isNotEmpty()) item {
                MediaRow("More Like This", similar, UiScale.posterWidth.dp, sidePadding = 64.dp) { s ->
                    PosterCard(s, UiScale.posterWidth.dp) { nav.go(Screen.Detail(s.id)) }
                }
            }
            item { Spacer(Modifier.height(64.dp)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Header(item: Item, nav: Navigator, nextUp: Item?) {
    val scope = rememberCoroutineScope()
    var fav by remember(item.id) { mutableStateOf(item.isFavorite) }
    var watched by remember(item.id) { mutableStateOf(item.played) }

    // Version / audio / subtitle choices (movies and episodes)
    val sources = item.mediaSources
    var source by remember(item.id) { mutableStateOf<MediaSourceInfo?>(sources.firstOrNull()) }
    val src = source
    var audioIdx by remember(item.id, src?.id) {
        mutableStateOf(src?.let { Lang.pickAudio(it.streams, it.defaultAudioIndex) })
    }
    var subIdx by remember(item.id, src?.id) {
        mutableStateOf(src?.let { Lang.pickSubtitle(it.streams, it.defaultSubtitleIndex) } ?: -1)
    }
    fun selection(): PlaySelection? = if (src == null) null else PlaySelection(src.id, audioIdx, subIdx)

    Column(Modifier.padding(start = 64.dp, end = 64.dp, top = 130.dp).widthIn(max = 900.dp)) {
        if (item.isEpisode && item.seriesName != null) {
            Text(
                listOfNotNull(item.seriesName, item.episodeLabel).joinToString(" · "),
                style = NovaType.Section, color = Nova.Cyan,
                modifier = Modifier.clickableHand { item.seriesId?.let { nav.go(Screen.Detail(it)) } },
            )
            Spacer(Modifier.height(8.dp))
        }
        TitleOrLogo(item, 130.dp, 560.dp)
        Spacer(Modifier.height(16.dp))
        MetaLine(item)
        if (item.genres.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(item.genres.joinToString(" • "), style = NovaType.Button, color = Nova.Text)
        }
        endsAt(item)?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = NovaType.Label, color = Nova.TextFaint)
        }
        item.taglines.firstOrNull()?.let {
            Spacer(Modifier.height(14.dp))
            Text(it, style = NovaType.Section.copy(fontStyle = FontStyle.Italic), color = Nova.Text)
        }
        if (item.overview.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(item.overview, style = NovaType.Body, color = Nova.TextDim, maxLines = 6, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(24.dp))

        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when {
                item.isSeries -> {
                    val label = nextUp?.let { n -> "Play ${n.episodeLabel ?: n.name}" } ?: "Play"
                    NovaButton(label, Icons.Filled.PlayArrow, primary = true) { Player.play(nextUp ?: item) }
                }
                item.isCollection || item.isFolderLike -> {}
                else -> {
                    NovaButton(if (item.progress > 0f) "Resume" else "Play", Icons.Filled.PlayArrow, primary = true) {
                        Player.play(item, selection())
                    }
                    if (item.progress > 0f) NovaButton("From start", Icons.Outlined.Replay) { Player.play(item, selection(), fromStart = true) }
                }
            }
            NovaButton(null, if (fav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder) {
                fav = !fav
                scope.launch { runCatching { App.api.setFavorite(item.id, fav) } }
            }
            if (!item.isCollection) NovaButton(if (watched) "Watched" else "Mark watched", if (watched) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle) {
                watched = !watched
                scope.launch { runCatching { App.api.setPlayed(item.id, watched) }; App.refreshTick++ }
            }
        }

        if (src != null && !item.isSeries) {
            Spacer(Modifier.height(16.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (sources.size > 1) {
                    Picker(Icons.Outlined.VideoFile, "Version", src.label, sources.map { it.label to it.detail }, sources.indexOf(src)) { i ->
                        source = sources[i]
                    }
                }
                val audio = src.audioStreams
                if (audio.isNotEmpty()) {
                    val labels = audio.map { Lang.label(it) }
                    val cur = audio.indexOfFirst { it.index == audioIdx }.coerceAtLeast(0)
                    Picker(Icons.Outlined.SurroundSound, "Audio", labels[cur].first, labels, cur) { i ->
                        audioIdx = audio[i].index
                        Lang.rememberAudio(audio[i].language)
                    }
                }
                val subs = src.subtitleStreams
                if (subs.isNotEmpty()) {
                    val labels = listOf("Off" to "") + subs.map { Lang.label(it) }
                    val cur = if (subIdx < 0) 0 else (subs.indexOfFirst { it.index == subIdx } + 1).coerceAtLeast(0)
                    Picker(Icons.Outlined.ClosedCaption, "Subtitles", labels[cur].first, labels, cur) { i ->
                        if (i == 0) { subIdx = -1; Lang.rememberSubtitle(null, off = true) }
                        else { subIdx = subs[i - 1].index; Lang.rememberSubtitle(subs[i - 1].language, off = false) }
                    }
                }
            }
        }

        val directors = item.people.filter { it.type == "Director" }.map { it.name }
        if (directors.isNotEmpty() || item.studios.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            if (directors.isNotEmpty()) InfoLine("Director", directors.joinToString(", "))
            if (item.studios.isNotEmpty()) InfoLine("Studio", item.studios.take(3).joinToString(", "))
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.padding(vertical = 2.dp)) {
        Text("$label  ", style = NovaType.Label, color = Nova.TextFaint)
        Text(value, style = NovaType.Label, color = Nova.TextDim)
    }
}

/** A button that opens a small menu (version, audio, subtitles) */
@Composable
private fun Picker(icon: ImageVector, title: String, current: String, options: List<Pair<String, String>>, selected: Int, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val (src, hovered) = rememberHover()
    val shape = RoundedCornerShape(14.dp)
    Box {
        Row(
            Modifier
                .clip(shape)
                .background(if (hovered) Color(0x26FFFFFF) else Color(0x14FFFFFF))
                .border(1.dp, Nova.Stroke, shape)
                .hoverable(src)
                .clickableHand { open = true }
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = Nova.Cyan, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Column {
                Text(title.uppercase(), style = NovaType.Caption, color = Nova.TextFaint)
                Text(current, style = NovaType.Label, color = Nova.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 260.dp))
            }
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Outlined.ExpandMore, null, tint = Nova.TextDim, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.background(Nova.SurfaceHi)) {
            options.forEachIndexed { i, (label, detail) ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(label, style = NovaType.Button, color = if (i == selected) Nova.Cyan else Nova.Text)
                            if (detail.isNotBlank()) Text(detail, style = NovaType.Label, color = Nova.TextFaint)
                        }
                    },
                    onClick = { open = false; onPick(i) },
                )
            }
        }
    }
}

@Composable
private fun EpisodeRow(ep: Item, onClick: () -> Unit) {
    val (src, hovered) = rememberHover()
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .padding(horizontal = 52.dp, vertical = 4.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(if (hovered) Color(0x1AFFFFFF) else Color.Transparent)
            .hoverable(src)
            .clickableHand(onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(240.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).border(1.dp, if (hovered) Nova.Cyan else Nova.Stroke, RoundedCornerShape(10.dp))) {
            NetImage(App.api.landscape(ep, 480), Modifier.fillMaxSize())
            if (hovered) {
                Box(Modifier.fillMaxSize().background(Color(0x66000000)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.PlayArrow, "Play", tint = Color.White, modifier = Modifier.size(44.dp))
                }
            }
            if (ep.progress > 0f && !ep.played) ProgressBar(ep.progress, Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(8.dp))
        }
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${ep.indexNumber?.let { "$it. " } ?: ""}${ep.name}",
                    style = NovaType.Section, color = Nova.Text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (ep.played) {
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Filled.CheckCircle, "Watched", tint = Nova.Cyan, modifier = Modifier.size(18.dp))
                }
            }
            Text(formatRuntime(ep.runtimeMinutes), style = NovaType.Label, color = Nova.TextFaint)
            if (ep.overview.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(ep.overview, style = NovaType.Body, color = Nova.TextDim, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun endsAt(item: Item): String? {
    if (item.isSeries || item.isCollection || item.runTimeTicks <= 0) return null
    val remainingMs = (item.runTimeTicks - item.playbackPositionTicks).coerceAtLeast(0) / 10_000
    val end = Date(System.currentTimeMillis() + remainingMs)
    val left = if (item.playbackPositionTicks > 0) " · ${formatRuntime((remainingMs / 60_000).toInt())} left" else ""
    return "Ends at " + SimpleDateFormat("h:mm a", Locale.getDefault()).format(end) + left
}
