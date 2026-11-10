package org.phoenicx.nova.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.phoenicx.nova.App
import org.phoenicx.nova.Navigator
import org.phoenicx.nova.Screen
import org.phoenicx.nova.data.ReleaseFilter
import org.phoenicx.nova.data.TorviaAdd
import org.phoenicx.nova.data.TorviaAdds
import org.phoenicx.nova.data.TorviaApi
import org.phoenicx.nova.data.TorviaDetails
import org.phoenicx.nova.data.TorviaRelease
import org.phoenicx.nova.data.TorviaResult
import org.phoenicx.nova.data.TorviaTitle
import org.phoenicx.nova.ui.Chip
import org.phoenicx.nova.ui.Loader
import org.phoenicx.nova.ui.Message
import org.phoenicx.nova.ui.NetImage
import org.phoenicx.nova.ui.Nova
import org.phoenicx.nova.ui.NovaButton
import org.phoenicx.nova.ui.NovaType
import org.phoenicx.nova.ui.SectionTitle
import org.phoenicx.nova.ui.TopBarHeight
import org.phoenicx.nova.ui.UiScale
import org.phoenicx.nova.ui.clickableHand
import org.phoenicx.nova.ui.rememberHover

private val torvia get() = TorviaApi(App.api)

/** Keeps the last search and tab while you open a title and come back */
private object TorviaMemory {
    var query = ""
    var lastSearched = ""
    var results: List<TorviaTitle> = emptyList()
    var tab = 0
}

private fun allowanceText(): String? {
    val s = App.torvia ?: return null
    val left = s.remainingToday ?: return null
    return if (s.dailyLimit != null) "$left of ${s.dailyLimit} adds left today" else "$left adds left today"
}

// ============================================================================ Search & My adds

@Composable
fun TorviaScreen(nav: Navigator) {
    var tab by remember { mutableIntStateOf(TorviaMemory.tab) }
    LaunchedEffect(tab) { TorviaMemory.tab = tab }
    Column(Modifier.fillMaxSize().padding(top = TopBarHeight + 8.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(App.torvia?.buttonLabel ?: "Search & Add", style = NovaType.Title.copy(fontSize = 30.sp), color = Nova.Text)
                allowanceText()?.let { Text(it, style = NovaType.Button, color = Nova.TextFaint) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Search", tab == 0) { tab = 0 }
                Chip("My adds", tab == 1) { tab = 1 }
            }
        }
        Spacer(Modifier.height(14.dp))
        if (tab == 0) SearchTab(nav) else AddsTab(nav)
    }
}

@Composable
private fun SearchTab(nav: Navigator) {
    var query by remember { mutableStateOf(TorviaMemory.query) }
    var results by remember { mutableStateOf(TorviaMemory.results) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(query) {
        TorviaMemory.query = query
        val q = query.trim()
        if (q.length < 2) { results = emptyList(); error = null; return@LaunchedEffect }
        if (q == TorviaMemory.lastSearched && results.isNotEmpty()) return@LaunchedEffect
        delay(600)
        loading = true
        error = null
        runCatching { torvia.search(q) }
            .onSuccess { results = it; TorviaMemory.results = it; TorviaMemory.lastSearched = q }
            .onFailure { error = it.message }
        loading = false
    }

    val cardW = UiScale.posterWidth
    LazyVerticalGrid(
        columns = GridCells.Adaptive((cardW + 8).dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 48.dp, end = 48.dp, bottom = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Movie or show name, TMDB or IMDb link…", color = Nova.TextFaint) },
                leadingIcon = { Icon(Icons.Outlined.Search, null, tint = Nova.TextDim) },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Nova.Cyan, unfocusedBorderColor = Nova.Stroke, cursorColor = Nova.Cyan,
                    focusedTextColor = Nova.Text, unfocusedTextColor = Nova.Text,
                    focusedContainerColor = Color(0x14FFFFFF), unfocusedContainerColor = Color(0x0AFFFFFF),
                ),
                modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
            )
        }
        when {
            loading && results.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) { Loader() }
            error != null -> item(span = { GridItemSpan(maxLineSpan) }) { Message("Couldn't search: $error") }
            query.trim().length >= 2 && results.isEmpty() && !loading ->
                item(span = { GridItemSpan(maxLineSpan) }) { Message("Nothing found. Try another name, or paste a TMDB or IMDb link.") }
            results.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) { Message("Search by name, or paste a TMDB or IMDb link.") }
            else -> items(results) { t ->
                Box(contentAlignment = Alignment.TopCenter) {
                    TmdbPoster(t, cardW) { nav.go(Screen.TorviaTitle(t.type, t.tmdbId, t.title)) }
                }
            }
        }
    }
}

@Composable
private fun TmdbPoster(t: TorviaTitle, width: Int, onClick: () -> Unit) {
    val (src, hovered) = rememberHover()
    val shape = RoundedCornerShape(12.dp)
    Column(Modifier.width(width.dp).hoverable(src).clickableHand(onClick)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(shape)
                .border(if (hovered) 2.dp else 1.dp, if (hovered) Nova.Cyan else Nova.Stroke, shape)
        ) {
            NetImage(t.poster, Modifier.fillMaxSize())
            Box(
                Modifier.align(Alignment.TopStart).padding(8.dp)
                    .background(Nova.BgDeep.copy(alpha = 0.75f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) { Text(if (t.isTv) "Series" else "Movie", style = NovaType.Label, color = Nova.Text) }
        }
        Spacer(Modifier.height(8.dp))
        Text(t.title, style = NovaType.Button, color = Nova.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(t.year?.toString() ?: " ", style = NovaType.Label, color = Nova.TextFaint, maxLines = 1)
    }
}

@Composable
private fun AddsTab(nav: Navigator) {
    val scope = rememberCoroutineScope()
    val isAdmin = App.torvia?.isAdmin == true
    var everyone by remember { mutableStateOf(false) }
    var userFilter by remember { mutableStateOf<String?>(null) }
    var data by remember { mutableStateOf<TorviaAdds?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var confirm by remember { mutableStateOf<TorviaAdd?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(everyone, userFilter, reload) {
        error = null
        runCatching { torvia.adds(everyone, if (everyone) userFilter else null) }
            .onSuccess { data = it }
            .onFailure { error = it.message }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (isAdmin) {
                Row(Modifier.padding(horizontal = 48.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Mine", !everyone) { everyone = false; userFilter = null }
                    Chip("Everyone", everyone && userFilter == null) { everyone = true; userFilter = null }
                    if (everyone) data?.users?.forEach { u -> Chip("${u.name} (${u.count})", userFilter == u.id) { userFilter = u.id } }
                }
                Spacer(Modifier.height(12.dp))
            }
            notice?.let { Text(it, style = NovaType.Body, color = Nova.Cyan, modifier = Modifier.padding(horizontal = 48.dp, vertical = 6.dp)) }
            val d = data
            when {
                error != null -> Message("Couldn't load your adds: $error")
                d == null -> Loader()
                d.items.isEmpty() -> Message("Nothing added yet. Titles you add from Search appear here.")
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 6.dp, bottom = 48.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(d.items) { a ->
                        AddRow(a, everyone,
                            onOpen = a.jellyfinItemId?.let { id -> { nav.go(Screen.Detail(id)) } },
                            onRemove = if (a.canRemove) ({ confirm = a }) else null,
                        )
                    }
                }
            }
        }
        confirm?.let { a ->
            Dialog(onDismiss = { confirm = null }) {
                Text("Remove ${a.title}?", style = NovaType.Title, color = Nova.Text)
                Spacer(Modifier.height(10.dp))
                Text(
                    "This deletes it from TorBox and it leaves Jellyfin. If it was already in TorBox before, or someone else added it too, it stays in TorBox.",
                    style = NovaType.Body, color = Nova.TextDim,
                )
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NovaButton("Cancel") { confirm = null }
                    NovaButton("Remove", Icons.Outlined.Delete, primary = true) {
                        confirm = null
                        scope.launch {
                            notice = runCatching { torvia.remove(a.id).message }.getOrElse { it.message ?: "Couldn't remove it." }
                            reload++
                            App.refreshTorvia()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddRow(a: TorviaAdd, showUser: Boolean, onOpen: (() -> Unit)?, onRemove: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth()
            .background(Color(0x12FFFFFF), RoundedCornerShape(16.dp))
            .border(1.dp, Nova.Stroke, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NetImage(a.poster, Modifier.size(56.dp, 84.dp).clip(RoundedCornerShape(8.dp)))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(listOfNotNull(a.title, a.year?.let { "($it)" }).joinToString(" "), style = NovaType.Section, color = Nova.Text,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            val line = listOf(a.label, a.quality, a.size).filter { it.isNotBlank() }.joinToString(" · ")
            if (line.isNotBlank()) Text(line, style = NovaType.Label, color = Nova.TextDim, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val meta = listOfNotNull(
                if (showUser && a.userName.isNotBlank()) "by ${a.userName}" else null,
                a.whenText.take(10).takeIf { it.isNotBlank() },
                if (a.jellyfinItemId == null) "Not in Jellyfin yet" else "In Jellyfin",
            ).joinToString(" · ")
            Text(meta, style = NovaType.Label, color = Nova.TextFaint, maxLines = 1)
        }
        Spacer(Modifier.width(12.dp))
        if (onOpen != null) NovaButton("Open", Icons.Outlined.PlayArrow, onClick = onOpen)
        if (onOpen != null && onRemove != null) Spacer(Modifier.width(10.dp))
        if (onRemove != null) NovaButton("Remove", Icons.Outlined.Delete, onClick = onRemove)
    }
}

// ============================================================================ One title: releases & add


@Composable
fun TorviaTitleScreen(nav: Navigator, type: String, tmdbId: Int, name: String) {
    val scope = rememberCoroutineScope()
    var details by remember(type, tmdbId) { mutableStateOf<TorviaDetails?>(null) }
    var error by remember(type, tmdbId) { mutableStateOf<String?>(null) }
    var season by remember(type, tmdbId) { mutableIntStateOf(-1) }
    var episode by remember(type, tmdbId) { mutableIntStateOf(1) }
    var releases by remember(type, tmdbId) { mutableStateOf<List<TorviaRelease>?>(null) }
    var relError by remember(type, tmdbId) { mutableStateOf<String?>(null) }
    var inLibraryId by remember(type, tmdbId) { mutableStateOf<String?>(null) }

    var f by remember { mutableStateOf(ReleaseFilter()) }
    var note by remember { mutableStateOf<String?>(null) }

    var picked by remember { mutableStateOf<TorviaRelease?>(null) }
    var adding by remember { mutableStateOf(false) }
    var result by remember(type, tmdbId) { mutableStateOf<String?>(null) }
    var waiting by remember(type, tmdbId) { mutableStateOf(false) }

    LaunchedEffect(type, tmdbId) {
        runCatching { torvia.details(type, tmdbId) }
            .onSuccess { d ->
                details = d
                inLibraryId = d.jellyfinItemId
                if (d.title.isTv && season < 0) season = d.seasons.firstOrNull()?.season ?: 1
            }
            .onFailure { error = it.message }
    }
    LaunchedEffect(details, season, episode) {
        val d = details ?: return@LaunchedEffect
        if (d.title.isTv && season < 0) return@LaunchedEffect
        releases = null; relError = null
        runCatching {
            if (d.title.isTv) torvia.releases(type, tmdbId, season, if (episode > 0) episode else null, wholeSeason = episode == 0)
            else torvia.releases(type, tmdbId)
        }.onSuccess { r ->
            releases = r.releases
            if (r.jellyfinItemId != null) inLibraryId = r.jellyfinItemId
            ReleaseFilter.start(r.releases).let { (start, n) -> f = start; note = n }
        }.onFailure { relError = it.message }
    }
    LaunchedEffect(waiting) {
        if (!waiting) return@LaunchedEffect
        repeat(18) {
            delay(10_000)
            torvia.inLibrary(type, tmdbId)?.let { inLibraryId = it; waiting = false; return@LaunchedEffect }
        }
        waiting = false
    }

    val all = releases.orEmpty()
    val shown = f.apply(all)
    fun set(n: ReleaseFilter) { f = n; note = null }

    Box(Modifier.fillMaxSize()) {
        val d = details
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = TopBarHeight, bottom = 48.dp)) {
            if (d == null) {
                item { if (error != null) Message("Couldn't open $name: $error") else Loader() }
                return@LazyColumn
            }
            item { Header(d.title, inLibraryId, waiting, result, nav) }
            if (d.title.isTv && d.seasons.isNotEmpty()) {
                item {
                    ChipLine("Season") {
                        d.seasons.forEach { s -> Chip(s.name.ifBlank { "Season ${s.season}" }, s.season == season) { season = s.season; episode = 1 } }
                    }
                    val count = d.seasons.firstOrNull { it.season == season }?.episodes ?: 0
                    ChipLine("Episode") {
                        Chip("Whole season", episode == 0) { episode = 0 }
                        (1..count).forEach { e -> Chip("E$e", episode == e) { episode = e } }
                    }
                }
            }
            if (all.isNotEmpty()) item { FilterPanel(all, f, note, ::set) }
            item {
                Spacer(Modifier.height(10.dp))
                SectionTitle(if (releases == null) "Releases" else "Releases · ${shown.size} of ${all.size}", Modifier.padding(horizontal = 48.dp))
                Spacer(Modifier.height(10.dp))
            }
            when {
                relError != null -> item { Text(relError!!, style = NovaType.Body, color = Nova.Rose, modifier = Modifier.padding(horizontal = 48.dp)) }
                releases == null -> item {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Loader()
                        Text("Looking for cached releases… this can take a few seconds", style = NovaType.Label, color = Nova.TextDim)
                    }
                }
                all.isEmpty() -> item { Text("No cached releases found on TorBox for this.", style = NovaType.Body, color = Nova.TextDim, modifier = Modifier.padding(horizontal = 48.dp)) }
                shown.isEmpty() -> item { Text("No releases match these filters.", style = NovaType.Body, color = Nova.TextDim, modifier = Modifier.padding(horizontal = 48.dp)) }
                else -> items(shown) { r -> ReleaseRow(r, best = f.sort == "best" && !r.isCam && r === shown.first()) { picked = r } }
            }
        }

        picked?.let { r ->
            val st = App.torvia
            val tags = st?.tags.orEmpty()
            var tag by remember(r.hash) { mutableStateOf((if (type == "tv") st?.tvTag else st?.movieTag).orEmpty().ifBlank { tags.firstOrNull() ?: "" }) }
            Dialog(onDismiss = { if (!adding) picked = null }) {
                Text("Add this release?", style = NovaType.Title, color = Nova.Text)
                Spacer(Modifier.height(10.dp))
                Text(r.name, style = NovaType.Body, color = Nova.Text, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text(r.summary, style = NovaType.Label, color = Nova.TextDim)
                allowanceText()?.let { Spacer(Modifier.height(8.dp)); Text(it, style = NovaType.Label, color = Nova.Cyan) }
                if (tags.size > 1) {
                    Spacer(Modifier.height(14.dp))
                    Text("ADD TO", style = NovaType.Caption, color = Nova.TextFaint)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { tags.forEach { t -> Chip(t, t == tag) { tag = t } } }
                }
                Spacer(Modifier.height(20.dp))
                if (adding) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(60.dp)) { Loader() }
                        Text("Adding to TorBox… this can take up to half a minute", style = NovaType.Label, color = Nova.TextDim)
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        NovaButton("Add", Icons.Outlined.Add, primary = true) {
                            adding = true
                            scope.launch {
                                val res = runCatching { torvia.add(r.hash, tag.ifBlank { null }) }
                                    .getOrElse { TorviaResult(false, it.message ?: "Couldn't add it.") }
                                adding = false
                                picked = null
                                result = res.message
                                if (res.ok) {
                                    App.refreshTorvia()
                                    if (inLibraryId == null) waiting = true
                                }
                            }
                        }
                        NovaButton("Cancel") { picked = null }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(t: TorviaTitle, inLibraryId: String?, waiting: Boolean, result: String?, nav: Navigator) {
    Box(Modifier.fillMaxWidth()) {
        if (t.backdrop != null) {
            AsyncImage(t.backdrop, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(300.dp))
            Box(Modifier.fillMaxWidth().height(300.dp).background(Brush.verticalGradient(listOf(Nova.Bg.copy(alpha = 0.3f), Nova.Bg))))
        }
        Row(Modifier.padding(start = 48.dp, end = 48.dp, top = if (t.backdrop != null) 110.dp else 16.dp)) {
            NetImage(t.poster, Modifier.size(150.dp, 225.dp).clip(RoundedCornerShape(12.dp)))
            Spacer(Modifier.width(24.dp))
            Column(Modifier.weight(1f).padding(top = if (t.backdrop != null) 60.dp else 0.dp)) {
                Text(t.title, style = NovaType.Display.copy(fontSize = 34.sp, lineHeight = 38.sp), color = Nova.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(if (t.isTv) "Series" else "Movie", t.year?.toString(), t.rating?.let { "★ " + String.format("%.1f", it) }).joinToString(" · "),
                    style = NovaType.Button, color = Nova.TextDim,
                )
                Spacer(Modifier.height(8.dp))
                if (t.overview.isNotBlank()) Text(t.overview, style = NovaType.Body, color = Nova.TextDim, maxLines = 4, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 900.dp))
            }
        }
    }
    Column(Modifier.padding(horizontal = 48.dp, vertical = 12.dp)) {
        if (result != null) { Text(result, style = NovaType.Body, color = Nova.Cyan); Spacer(Modifier.height(8.dp)) }
        when {
            inLibraryId != null -> NovaButton("Open in library", Icons.Outlined.PlayArrow, primary = true) { nav.go(Screen.Detail(inLibraryId)) }
            waiting -> Text("Waiting for it to appear in Jellyfin…", style = NovaType.Label, color = Nova.TextDim)
        }
    }
}

/** The same filter box as the Torvia web page: Quality, Type, Video, Language, Sort and Reset */
@Composable
private fun FilterPanel(all: List<TorviaRelease>, f: ReleaseFilter, note: String?, set: (ReleaseFilter) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 48.dp, vertical = 6.dp)
            .background(Color(0x0FFFFFFF), RoundedCornerShape(14.dp))
            .border(1.dp, Nova.Stroke, RoundedCornerShape(14.dp))
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        FilterLine("Quality") {
            FilterChip("All", all.size, f.quality == "all") { set(f.copy(quality = "all")) }
            ReleaseFilter.qualities.filter { q -> all.any { it.quality == q } }.forEach { q ->
                FilterChip(q, all.count { it.quality == q }, f.quality == q) { set(f.copy(quality = q)) }
            }
        }
        val eps = all.count { !it.isSeasonPack && it.kind.isNotBlank() }
        val packs = all.count { it.isSeasonPack }
        if (eps > 0 && packs > 0) FilterLine("Type") {
            FilterChip("All", all.size, f.kind == "all") { set(f.copy(kind = "all")) }
            FilterChip("This episode only", eps, f.kind == "episode") { set(f.copy(kind = "episode")) }
            FilterChip("Packs", packs, f.kind == "pack") { set(f.copy(kind = "pack")) }
        }
        val nTen = all.count { it.isTenBit }
        val nHdr = all.count { it.isHdr }
        val anyCam = all.any { it.isCam }
        if (nTen > 0 || nHdr > 0 || anyCam) FilterLine("Video") {
            if (nTen > 0) FilterChip("10-bit", nTen, f.tenBit) { set(f.copy(tenBit = !f.tenBit)) }
            if (nHdr > 0) FilterChip("HDR / DV", nHdr, f.hdr) { set(f.copy(hdr = !f.hdr)) }
            if (anyCam) FilterChip("Hide CAM/TS", null, f.hideCam) { set(f.copy(hideCam = !f.hideCam)) }
        }
        val langs = ReleaseFilter.languages(all)
        if (langs.isNotEmpty()) FilterLine("Language") {
            langs.forEach { (l, n) ->
                FilterChip(l, n, l in f.langs) { set(f.copy(langs = if (l in f.langs) f.langs - l else f.langs + l)) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                FilterLine("Sort") {
                    ReleaseFilter.sorts.forEach { (k, label) -> FilterChip(label, null, f.sort == k) { set(f.copy(sort = k)) } }
                }
            }
            FilterChip("Reset", null, false, outlined = true) { set(ReleaseFilter()) }
        }
        if (note != null) Text(note, style = NovaType.Caption, color = Nova.TextFaint, modifier = Modifier.padding(top = 4.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterLine(label: String, chips: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = NovaType.Label, color = Nova.TextDim, modifier = Modifier.width(120.dp).padding(top = 8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { chips() }
    }
}

/** A filter pill with a small count after the name, like "1080p 4" on the web page */
@Composable
private fun FilterChip(text: String, count: Int?, selected: Boolean, outlined: Boolean = false, onClick: () -> Unit) {
    val (src, hovered) = rememberHover()
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier.clip(shape)
            .background(
                when {
                    selected -> Nova.Cyan.copy(alpha = 0.22f)
                    hovered -> Color(0x26FFFFFF)
                    outlined -> Color.Transparent
                    else -> Color(0x12FFFFFF)
                }, shape
            )
            .border(1.dp, if (selected) Nova.Cyan.copy(alpha = 0.8f) else Nova.Stroke, shape)
            .hoverable(src)
            .clickableHand(onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = NovaType.Label, color = if (selected) Nova.Cyan else Nova.Text, fontWeight = FontWeight.SemiBold, maxLines = 1)
        if (count != null) {
            Spacer(Modifier.width(6.dp))
            Text(count.toString(), style = NovaType.Caption, color = Nova.TextFaint, maxLines = 1)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipLine(label: String, chips: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label.uppercase(), style = NovaType.Caption, color = Nova.TextFaint, modifier = Modifier.width(80.dp))
        chips()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReleaseRow(r: TorviaRelease, best: Boolean, onClick: () -> Unit) {
    val (src, hovered) = rememberHover()
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 48.dp, vertical = 5.dp)
            .clip(shape)
            .background(if (hovered) Nova.SurfaceHi else Color(0x12FFFFFF))
            .border(1.dp, if (hovered) Nova.Cyan else Nova.Stroke, shape)
            .hoverable(src)
            .clickableHand(onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (best) Tag("Best", Nova.Cyan)
                if (r.label.isNotBlank()) Tag(r.label, Nova.TextDim)
                if (r.isCam) Tag("⚠ CAM/TS", Nova.Rose) else if (r.source.isNotBlank()) Tag(r.source, Nova.TextDim)
                if (r.quality.isNotBlank() && r.quality != "?") Tag(r.quality, Nova.Cyan)
                listOf(
                    r.codec, r.hdr, if (r.bitDepth > 0) "${r.bitDepth}-bit" else "",
                    r.audio, r.languages.joinToString(" / "), r.subtitles, r.container,
                ).filter { it.isNotBlank() }.forEach { Tag(it, Nova.TextDim) }
            }
            Spacer(Modifier.height(6.dp))
            Text(r.name, style = NovaType.Label, color = Nova.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (r.source.isBlank() && r.codec.isBlank()) Text(r.summary, style = NovaType.Label, color = Nova.TextDim, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (r.playsLabel.isNotBlank()) Text(
                "Plays ${r.playsLabel}" + if (r.fileName.isNotBlank()) " · ${r.fileName}" else "",
                style = NovaType.Label, color = Nova.TextFaint, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(if (r.isSeasonPack && r.playsLabel.isNotBlank()) "Episode ${r.size}" else r.size, style = NovaType.Section, color = Nova.Text, fontWeight = FontWeight.Bold)
            if (r.packSize.isNotBlank()) Text("pack ${r.packSize}", style = NovaType.Label, color = Nova.TextFaint)
            Text(listOf(r.speed, "${r.seeders} seeds").filter { it.isNotBlank() }.joinToString(" · "), style = NovaType.Label, color = Nova.TextDim)
        }
    }
}

@Composable
private fun Tag(text: String, color: Color) {
    Box(Modifier.border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(8.dp)).padding(horizontal = 9.dp, vertical = 3.dp)) {
        Text(text, style = NovaType.Label, color = color, maxLines = 1)
    }
}

/** A centred panel over a dimmed window; clicking outside closes it */
@Composable
private fun Dialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Nova.BgDeep.copy(alpha = 0.78f))
            .pointerInput(onDismiss) { detectTapGestures { onDismiss() } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.widthIn(max = 640.dp).fillMaxWidth(0.9f)
                .background(Nova.Surface, RoundedCornerShape(24.dp))
                .border(1.dp, Nova.Stroke, RoundedCornerShape(24.dp))
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(28.dp)
        ) { content() }
    }
}
