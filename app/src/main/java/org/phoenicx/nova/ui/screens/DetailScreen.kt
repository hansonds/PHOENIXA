package org.phoenicx.nova.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Tv
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.phoenicx.nova.data.Item
import org.phoenicx.nova.player.Launcher
import org.phoenicx.nova.ui.Navigator
import org.phoenicx.nova.ui.Screen
import org.phoenicx.nova.ui.components.BackdropLayer
import org.phoenicx.nova.ui.components.CenterMessage
import org.phoenicx.nova.ui.components.FocusSurface
import org.phoenicx.nova.ui.components.OptionSheet
import org.phoenicx.nova.ui.components.SheetOption
import org.phoenicx.nova.data.PlaySelection
import org.phoenicx.nova.data.Lang
import org.phoenicx.nova.ui.components.GlassChip
import org.phoenicx.nova.ui.components.LandscapeCard
import org.phoenicx.nova.ui.components.MetaRow
import org.phoenicx.nova.ui.components.NovaButton
import org.phoenicx.nova.ui.components.NovaLoader
import org.phoenicx.nova.ui.components.PersonCard
import org.phoenicx.nova.ui.components.EdgeScroll
import androidx.compose.foundation.lazy.rememberLazyListState
import org.phoenicx.nova.ui.components.PosterCard
import org.phoenicx.nova.ui.components.ProgressBar
import org.phoenicx.nova.ui.components.SectionTitle
import org.phoenicx.nova.ui.components.api
import org.phoenicx.nova.ui.components.formatRuntime
import org.phoenicx.nova.ui.theme.Nova
import org.phoenicx.nova.ui.theme.NovaType
import org.phoenicx.nova.ui.theme.UiScale
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DetailScreen(nav: Navigator, itemId: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var item by remember { mutableStateOf<Item?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var seasons by remember { mutableStateOf<List<Item>>(emptyList()) }
    var selectedSeason by remember { mutableStateOf<Item?>(null) }
    var episodes by remember { mutableStateOf<List<Item>>(emptyList()) }
    var similar by remember { mutableStateOf<List<Item>>(emptyList()) }
    var children by remember { mutableStateOf<List<Item>>(emptyList()) }
    var nextUp by remember { mutableStateOf<Item?>(null) }
    val playRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    // Version / audio / subtitle choices for movies & episodes
    var srcIdx by remember { mutableStateOf(0) }
    var audioSel by remember { mutableStateOf<Int?>(null) }
    var subSel by remember { mutableStateOf<Int?>(null) }
    var sheet by remember { mutableStateOf<String?>(null) }
    var lastSheet by remember { mutableStateOf<String?>(null) }
    val chipRequesters = remember { mapOf("version" to FocusRequester(), "audio" to FocusRequester(), "subs" to FocusRequester()) }
    LaunchedEffect(sheet) {
        if (sheet == null && lastSheet != null) {
            delay(60); runCatching { lastSheet?.let { k -> chipRequesters[k]?.requestFocus() } }
        }
    }
    var focusedOnce by remember { mutableStateOf(false) }

    suspend fun load() = coroutineScope {
        try {
            val it = api.item(itemId)
            item = it
            val simD = async { runCatching { api.similar(itemId) }.getOrDefault(emptyList()) }
            if (it.isSeries) {
                val s = runCatching { api.seasons(it.id) }.getOrDefault(emptyList())
                seasons = s
                nextUp = runCatching { api.nextUpForSeries(it.id) }.getOrNull()
                if (selectedSeason == null) {
                    selectedSeason = s.firstOrNull { se -> se.id == nextUp?.seasonId }
                        ?: s.firstOrNull { se -> (se.unplayedCount ?: 0) > 0 } ?: s.firstOrNull()
                }
            }
            if (it.isCollection) children = runCatching { api.children(it.id) }.getOrDefault(emptyList())
            similar = if (it.isCollection) emptyList() else simD.await()
        } catch (e: Exception) {
            error = e.message ?: "Couldn't load"
        }
        Unit
    }

    LaunchedEffect(itemId) { load() }
    LifecycleResumeEffect(itemId) {
        // refresh progress after returning from the player
        if (item != null) scope.launch { load() }
        onPauseOrDispose { }
    }
    LaunchedEffect(selectedSeason?.id) {
        val s = selectedSeason ?: return@LaunchedEffect
        episodes = runCatching { api.episodes(item!!.id, s.id) }.getOrDefault(emptyList())
    }
    LaunchedEffect(item != null) {
        if (item != null && !focusedOnce) {
            delay(80); runCatching { playRequester.requestFocus() }; focusedOnce = true
        }
    }

    val it = item
    if (it == null) {
        if (error != null) CenterMessage("Something went wrong", error) else NovaLoader()
        return
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val headerH = maxHeight - 96.dp
        BackdropLayer(api.backdrop(it) ?: api.landscape(it, 1280), kenBurns = true, dim = 0f)

        val density = LocalDensity.current
        EdgeScroll(70.dp, 40.dp) {
            LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 80.dp)) {
                item("header") {
                    Box(Modifier.fillMaxWidth().height(headerH)) {
                        Column(
                            Modifier.align(Alignment.BottomStart).padding(start = 56.dp, end = 56.dp, bottom = 24.dp).widthIn(max = 720.dp)
                        ) {
                            if (it.isEpisode && it.seriesName != null) {
                                Text(it.seriesName.uppercase(), style = NovaType.Caption, color = Nova.Cyan)
                                Spacer(Modifier.height(10.dp))
                            }
                            TitleOrLogo(it, maxLogoH = 130.dp, maxLogoW = 480.dp, big = true)
                            if (it.isEpisode) {
                                Spacer(Modifier.height(8.dp))
                                Text(listOfNotNull(it.episodeLabel, it.name).joinToString("  ·  "), style = NovaType.Section, color = Nova.Text)
                            }
                            Spacer(Modifier.height(16.dp))
                            MetaRow(it, showEpisode = false)
                            if (it.genres.isNotEmpty()) {
                                Spacer(Modifier.height(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { it.genres.take(5).forEach { g -> GlassChip(g) } }
                            }
                            it.taglines.firstOrNull()?.let { tag ->
                                Spacer(Modifier.height(14.dp))
                                Text("“$tag”", style = NovaType.Body.copy(fontSize = 16.sp), color = Nova.Text)
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(it.overview, style = NovaType.Body, color = Nova.TextDim, maxLines = 4, overflow = TextOverflow.Ellipsis)

                            val credits = buildList {
                                it.people.filter { p -> p.type == "Director" }.take(2).map { p -> p.name }.takeIf { l -> l.isNotEmpty() }
                                    ?.let { l -> add("Directed by ${l.joinToString(", ")}") }
                                it.studios.firstOrNull()?.let { s -> add(s) }
                                if (it.runtimeMinutes > 0 && !it.isSeries) {
                                    val remainingMs = (it.runTimeTicks - it.playbackPositionTicks) / 10_000
                                    val ends = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(System.currentTimeMillis() + remainingMs))
                                    add("Ends at $ends")
                                }
                            }
                            if (credits.isNotEmpty()) {
                                Spacer(Modifier.height(12.dp))
                                Text(credits.joinToString("   ·   "), style = NovaType.Label, color = Nova.TextFaint)
                            }

                            Spacer(Modifier.height(24.dp))
                            ActionRow(it, nextUp, playRequester,
                                onFocusRow = { scope.launch { delay(170); listState.animateScrollToItem(0) } }, onPlay = { fromStart ->
                                    val src = it.mediaSources.getOrNull(srcIdx)
                                    val sel = if (!it.isSeries && src != null) PlaySelection(
                                        mediaSourceId = src.id,
                                        audioIndex = audioSel ?: Lang.pickAudio(src.streams, src.defaultAudioIndex),
                                        subtitleIndex = subSel ?: Lang.pickSubtitle(src.streams, src.defaultSubtitleIndex),
                                    ) else null
                                    val target = if (it.isCollection) (children.firstOrNull { c -> !c.played } ?: children.firstOrNull()) else (nextUp ?: it)
                                    if (target != null) Launcher.play(context, scope, target, fromStart, if (it.isCollection) null else sel)
                                },
                                onGoSeries = { sid -> nav.push(Screen.Detail(sid)) },
                                onChanged = { changed -> item = changed })
                            val src = it.mediaSources.getOrNull(srcIdx)
                            if (!it.isSeries && src != null) {
                                Spacer(Modifier.height(16.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    if (it.mediaSources.size > 1) {
                                        SelectorChip("VERSION", src.label, chipRequesters.getValue("version")) {
                                            lastSheet = "version"; sheet = "version"
                                        }
                                    }
                                    val aIdx = audioSel ?: Lang.pickAudio(src.streams, src.defaultAudioIndex)
                                    val aStream = src.audioStreams.firstOrNull { a -> a.index == aIdx } ?: src.audioStreams.firstOrNull()
                                    if (src.audioStreams.isNotEmpty()) {
                                        SelectorChip("AUDIO", aStream?.let { a -> Lang.label(a).let { (m, d) -> if (d.isEmpty()) m else "$m · ${d.substringBefore(" · Default")}" } } ?: "Default", chipRequesters.getValue("audio")) {
                                            lastSheet = "audio"; sheet = "audio"
                                        }
                                    }
                                    val sIdx = subSel ?: Lang.pickSubtitle(src.streams, src.defaultSubtitleIndex)
                                    val sStream = src.subtitleStreams.firstOrNull { x -> x.index == sIdx }
                                    SelectorChip("SUBTITLES", sStream?.let { x -> Lang.label(x).first } ?: "Off", chipRequesters.getValue("subs")) {
                                        lastSheet = "subs"; sheet = "subs"
                                    }
                                }
                            }
                            if (it.progress > 0f && !it.played && !it.isSeries) {
                                Spacer(Modifier.height(14.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    ProgressBar(it.progress, Modifier.width(240.dp))
                                    Spacer(Modifier.width(12.dp))
                                    val left = ((it.runTimeTicks - it.playbackPositionTicks) / 600_000_000L).toInt()
                                    Text("${formatRuntime(left)} left", style = NovaType.Label, color = Nova.TextDim)
                                }
                            }
                        }
                    }
                }

                if (it.isCollection && children.isNotEmpty()) {
                    item("children") {
                        Column(Modifier.padding(top = 8.dp)) {
                            SectionTitle("In this collection · ${children.size}", Modifier.padding(start = 56.dp))
                            EdgeScroll(56.dp, 56.dp) {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 56.dp, vertical = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                                ) {
                                    items(children, key = { c -> c.id }) { c ->
                                        PosterCard(c, onClick = { nav.push(Screen.Detail(c.id)) }, width = UiScale.posterWidth, showTitle = true)
                                    }
                                }
                            }
                        }
                    }
                }

                if (it.isSeries && seasons.isNotEmpty()) {
                    item("seasons") {
                        Column {
                            SectionTitle("Episodes", Modifier.padding(start = 56.dp, top = 8.dp))
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 56.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                items(seasons, key = { s -> s.id }) { s ->
                                    SeasonTab(s.name, s.id == selectedSeason?.id) { selectedSeason = s }
                                }
                            }
                            EdgeScroll(56.dp, 56.dp) {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 56.dp, vertical = 14.dp),
                                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                                ) {
                                    items(episodes, key = { e -> e.id }) { e ->
                                        Column(Modifier.width((UiScale.landscapeWidth * 1.15f).toInt().dp)) {
                                            LandscapeCard(
                                                e, width = (UiScale.landscapeWidth * 1.15f).toInt(),
                                                subtitle = listOfNotNull(
                                                    e.indexNumber?.let { n -> "Episode $n" },
                                                    e.runtimeMinutes.takeIf { m -> m > 0 }?.let(::formatRuntime),
                                                ).joinToString(" · "),
                                                onClick = { Launcher.play(context, scope, e) },
                                            )
                                            Spacer(Modifier.height(10.dp))
                                            Text(e.name, style = NovaType.Label, color = Nova.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(e.overview, style = NovaType.Label.copy(fontSize = 12.sp), color = Nova.TextFaint,
                                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                val cast = it.people.filter { p -> p.type == "Actor" || p.type == "GuestStar" }
                if (cast.isNotEmpty()) {
                    item("cast") {
                        Column(Modifier.padding(top = 12.dp)) {
                            SectionTitle("Cast & Crew", Modifier.padding(start = 56.dp))
                            EdgeScroll(56.dp, 56.dp) {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 56.dp, vertical = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                ) {
                                    items(cast.take(30), key = { p -> p.id + p.role }) { p -> PersonCard(p) { nav.push(Screen.Person(p.id, p.name)) } }
                                }
                            }
                        }
                    }
                }

                if (similar.isNotEmpty()) {
                    item("similar") {
                        Column(Modifier.padding(top = 12.dp)) {
                            SectionTitle("More Like This", Modifier.padding(start = 56.dp))
                            EdgeScroll(56.dp, 56.dp) {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 56.dp, vertical = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                                ) {
                                    items(similar, key = { s -> s.id }) { s ->
                                        PosterCard(s, onClick = { nav.push(Screen.Detail(s.id)) }, width = UiScale.posterWidth, showTitle = true)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        val cur = item
        val curSrc = cur?.mediaSources?.getOrNull(srcIdx)
        if (sheet != null && cur != null && curSrc != null) {
            when (sheet) {
                "version" -> OptionSheet(
                    "Version",
                    cur.mediaSources.mapIndexed { i, m -> SheetOption(m.label, m.detail, i == srcIdx) },
                    onSelect = { i -> srcIdx = i; audioSel = null; subSel = null; sheet = null },
                    onDismiss = { sheet = null },
                )
                "audio" -> {
                    val list = curSrc.audioStreams
                    val current = audioSel ?: Lang.pickAudio(curSrc.streams, curSrc.defaultAudioIndex)
                    OptionSheet(
                        "Audio",
                        list.map { a -> Lang.label(a).let { (m, d) -> SheetOption(m, d, a.index == current) } },
                        onSelect = { i -> audioSel = list[i].index; Lang.rememberAudio(list[i].language); sheet = null },
                        onDismiss = { sheet = null },
                    )
                }
                "subs" -> {
                    val list = curSrc.subtitleStreams
                    val current = subSel ?: Lang.pickSubtitle(curSrc.streams, curSrc.defaultSubtitleIndex)
                    OptionSheet(
                        "Subtitles",
                        listOf(SheetOption("Off", "", current == -1)) +
                            list.map { x -> Lang.label(x).let { (m, d) -> SheetOption(m, d, x.index == current) } },
                        onSelect = { i ->
                            subSel = if (i == 0) -1 else list[i - 1].index
                            Lang.rememberSubtitle(if (i == 0) null else list[i - 1].language, off = i == 0)
                            sheet = null
                        },
                        onDismiss = { sheet = null },
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectorChip(caption: String, value: String, requester: FocusRequester, onClick: () -> Unit) {
    FocusSurface(onClick = onClick, shape = RoundedCornerShape(14.dp), focusedScale = 1.04f, borderWidth = 0.dp, glow = false, focusRequester = requester) { focused ->
        Column(
            Modifier
                .widthIn(min = 150.dp, max = 300.dp)
                .background(if (focused) Nova.Focus else Color(0x1FFFFFFF))
                .border(1.dp, if (focused) Color.Transparent else Nova.Stroke, RoundedCornerShape(14.dp))
                .padding(horizontal = 16.dp, vertical = 9.dp)
        ) {
            Text(caption, style = NovaType.Caption.copy(fontSize = 10.sp), color = if (focused) Color(0xFF4A5068) else Nova.TextFaint)
            Text(
                "$value  ▾", style = NovaType.Label.copy(fontSize = 14.sp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (focused) Nova.OnFocus else Nova.Text,
            )
        }
    }
}

@Composable
private fun ActionRow(
    item: Item,
    nextUp: Item?,
    playRequester: FocusRequester,
    onPlay: (fromStart: Boolean) -> Unit,
    onGoSeries: (String) -> Unit,
    onChanged: (Item) -> Unit,
    onFocusRow: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    Row(Modifier.onFocusChanged { f -> if (f.hasFocus) onFocusRow() }, horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        val label = when {
            item.isSeries && nextUp != null -> {
                val resume = nextUp.playbackPositionTicks > 0
                (if (resume) "Resume " else "Play ") + (nextUp.episodeLabel ?: "")
            }
            item.isSeries -> "Play"
            item.playbackPositionTicks > 0 && !item.played -> "Resume"
            else -> "Play"
        }
        NovaButton(label.trim(), Icons.Filled.PlayArrow, primary = true, focusRequester = playRequester) { onPlay(false) }
        if (!item.isSeries && item.playbackPositionTicks > 0 && !item.played) {
            NovaButton("From Start", Icons.Outlined.Replay) { onPlay(true) }
        }
        if (item.isEpisode && item.seriesId != null) {
            NovaButton("Go to Series", Icons.Outlined.Tv) { onGoSeries(item.seriesId) }
        }
        NovaButton("", if (item.played) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle) {
            val v = !item.played
            onChanged(item.copy(played = v, playbackPositionTicks = if (v) 0 else item.playbackPositionTicks, playedPercentage = null))
            scope.launch { runCatching { api.setPlayed(item.id, v) } }
        }
        NovaButton("", if (item.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder) {
            val v = !item.isFavorite
            onChanged(item.copy(isFavorite = v))
            scope.launch { runCatching { api.setFavorite(item.id, v) } }
        }
    }
}

@Composable
private fun SeasonTab(name: String, selected: Boolean, onClick: () -> Unit) {
    FocusSurface(onClick = onClick, shape = RoundedCornerShape(20.dp), focusedScale = 1.05f, borderWidth = 0.dp, glow = false,
        onFocused = onClick) { focused ->
        val bg = when {
            focused -> Brush.horizontalGradient(listOf(Nova.Focus, Nova.Focus))
            selected -> Brush.horizontalGradient(listOf(Nova.Cyan.copy(alpha = 0.33f), Nova.Violet.copy(alpha = 0.33f)))
            else -> Brush.horizontalGradient(listOf(Color(0x1FFFFFFF), Color(0x14FFFFFF)))
        }
        Box(
            Modifier.background(bg).border(1.dp, if (selected && !focused) Nova.Cyan.copy(alpha = 0.5f) else Nova.Stroke, RoundedCornerShape(20.dp))
                .padding(horizontal = 18.dp, vertical = 9.dp)
        ) {
            Text(name, style = NovaType.Button, color = if (focused) Nova.OnFocus else Nova.Text)
        }
    }
}
