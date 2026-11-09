package org.phoenicx.nova.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.phoenicx.nova.data.TorviaAdd
import org.phoenicx.nova.data.TorviaAdds
import org.phoenicx.nova.data.TorviaApi
import org.phoenicx.nova.data.TorviaDetails
import org.phoenicx.nova.data.TorviaRelease
import org.phoenicx.nova.data.TorviaTitle
import org.phoenicx.nova.ui.Navigator
import org.phoenicx.nova.ui.Screen
import org.phoenicx.nova.ui.components.CenterMessage
import org.phoenicx.nova.ui.components.FocusSurface
import org.phoenicx.nova.ui.components.GlassChip
import org.phoenicx.nova.ui.components.NovaButton
import org.phoenicx.nova.ui.components.NovaLoader
import org.phoenicx.nova.ui.components.NovaTextField
import org.phoenicx.nova.ui.components.SectionTitle
import org.phoenicx.nova.ui.components.TopNav
import org.phoenicx.nova.ui.components.api
import org.phoenicx.nova.ui.theme.Lay
import org.phoenicx.nova.ui.theme.Nova
import org.phoenicx.nova.ui.theme.NovaType
import org.phoenicx.nova.ui.theme.UiScale

private val torvia get() = TorviaApi(api)

/** Keeps the last search while you open a title and come back */
private object TorviaMemory {
    var query = ""
    var lastSearched = ""
    var results: List<TorviaTitle> = emptyList()
    var tab = 0
}

/** "8 of 10 left today" (nothing when there is no limit) */
private fun allowanceText(nav: Navigator): String? {
    val s = nav.torvia.value ?: return null
    val left = s.remainingToday ?: return null
    return if (s.dailyLimit != null) "$left of ${s.dailyLimit} adds left today" else "$left adds left today"
}

// ============================================================================ Search & My adds

@Composable
fun TorviaScreen(nav: Navigator) {
    var tab by rememberSaveable { mutableIntStateOf(TorviaMemory.tab) }
    LaunchedEffect(tab) { TorviaMemory.tab = tab }
    val label = nav.torvia.value?.buttonLabel ?: "Search & Add"

    Column(Modifier.fillMaxSize()) {
        TopNav(nav, Screen.Torvia)
        PageHeader(label, allowanceText(nav)) {
            SortChip("Search", tab == 0) { tab = 0 }
            SortChip("My adds", tab == 1) { tab = 1 }
        }
        Spacer(Modifier.height(8.dp))
        if (tab == 0) TorviaSearchTab(nav) else TorviaAddsTab(nav)
    }
}

@Composable
private fun TorviaSearchTab(nav: Navigator) {
    var query by rememberSaveable { mutableStateOf(TorviaMemory.query) }
    var results by remember { mutableStateOf(TorviaMemory.results) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var runs by remember { mutableIntStateOf(0) }
    val fieldRequester = remember { FocusRequester() }
    val fromNav = remember { nav.focusNavOnEnter }

    LaunchedEffect(Unit) { if (!fromNav && results.isEmpty()) { delay(150); runCatching { fieldRequester.requestFocus() } } }
    LaunchedEffect(query, runs) {
        TorviaMemory.query = query
        val q = query.trim()
        if (q.length < 2) { results = emptyList(); error = null; return@LaunchedEffect }
        if (q == TorviaMemory.lastSearched && results.isNotEmpty() && runs == 0) return@LaunchedEffect
        delay(600)
        loading = true
        error = null
        runCatching { torvia.search(q) }
            .onSuccess { results = it; TorviaMemory.results = it; TorviaMemory.lastSearched = q }
            .onFailure { error = it.message }
        loading = false
    }

    NovaTextField(
        query, { query = it }, label = "",
        placeholder = "Movie or show name, TMDB or IMDb link…",
        icon = Icons.Outlined.Search,
        imeAction = ImeAction.Search,
        focusRequester = fieldRequester,
        onDone = { runs++ },
        modifier = Modifier.padding(horizontal = Lay.side).then(if (Lay.phone) Modifier.fillMaxWidth() else Modifier.width(620.dp)),
    )
    when {
        loading && results.isEmpty() -> NovaLoader()
        error != null -> CenterMessage("Couldn't search", error)
        query.trim().length >= 2 && results.isEmpty() && !loading -> CenterMessage("Nothing found", "Try another name, or paste a TMDB or IMDb link")
        results.isEmpty() -> CenterMessage("Find a movie or show to add", "Search by name, or paste a TMDB or IMDb link")
        else -> LazyVerticalGrid(
            columns = GridCells.Adaptive(UiScale.posterWidth.dp),
            contentPadding = PaddingValues(start = Lay.side, end = Lay.side, top = 24.dp, bottom = 60.dp),
            horizontalArrangement = Arrangement.spacedBy(if (Lay.phone) 12.dp else 20.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(results, key = { "${it.type}-${it.tmdbId}" }) { t ->
                TmdbPosterCard(t.title, t.year, t.poster, if (t.isTv) "Series" else "Movie") {
                    nav.push(Screen.TorviaTitle(t.type, t.tmdbId, t.title))
                }
            }
        }
    }
}

@Composable
private fun TmdbPosterCard(title: String, year: Int?, poster: String?, badge: String?, onClick: () -> Unit) {
    val w = UiScale.posterWidth
    Column(Modifier.width(w.dp)) {
        FocusSurface(onClick = onClick, modifier = Modifier.size(w.dp, (w * 1.5f).dp)) { _ ->
            Box(Modifier.fillMaxSize().background(Nova.Surface))
            if (poster != null) {
                AsyncImage(poster, title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Text(title, style = NovaType.Section, color = Nova.Text, textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(12.dp))
            }
            if (badge != null) {
                Box(
                    Modifier.align(Alignment.TopStart).padding(8.dp)
                        .background(Nova.BgDeep.copy(alpha = 0.75f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) { Text(badge, style = NovaType.Label, color = Nova.Text) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(title, style = NovaType.Label, color = Nova.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(year?.toString() ?: "", style = NovaType.Label, color = Nova.TextFaint, maxLines = 1)
    }
}

@Composable
private fun TorviaAddsTab(nav: Navigator) {
    val scope = rememberCoroutineScope()
    val isAdmin = nav.torvia.value?.isAdmin == true
    var everyone by rememberSaveable { mutableStateOf(false) }
    var userFilter by rememberSaveable { mutableStateOf<String?>(null) }
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
                Row(
                    Modifier.padding(horizontal = Lay.side).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SortChip("Mine", !everyone) { everyone = false; userFilter = null }
                    SortChip("Everyone", everyone && userFilter == null) { everyone = true; userFilter = null }
                    if (everyone) data?.users?.forEach { u ->
                        SortChip("${u.name} (${u.count})", userFilter == u.id) { userFilter = u.id }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            notice?.let {
                Text(it, style = NovaType.Body, color = Nova.Cyan, modifier = Modifier.padding(horizontal = Lay.side, vertical = 6.dp))
            }
            val d = data
            when {
                error != null -> CenterMessage("Couldn't load your adds", error)
                d == null -> NovaLoader()
                d.items.isEmpty() -> CenterMessage("Nothing added yet", "Titles you add from Search appear here")
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = Lay.side, end = Lay.side, top = 6.dp, bottom = 60.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(d.items, key = { it.id }) { a ->
                        AddRow(a, showUser = everyone,
                            onOpen = a.jellyfinItemId?.let { id -> { nav.push(Screen.Detail(id)) } },
                            onRemove = if (a.canRemove) ({ confirm = a }) else null,
                        )
                    }
                }
            }
        }
        confirm?.let { a ->
            ConfirmDialog(
                title = "Remove ${a.title}?",
                body = "This deletes it from TorBox and it leaves Jellyfin. If it was already in TorBox before, or someone else added it too, it stays in TorBox.",
                confirmText = "Remove",
                onConfirm = {
                    confirm = null
                    scope.launch {
                        notice = runCatching { torvia.remove(a.id).message }.getOrElse { it.message ?: "Couldn't remove it." }
                        reload++
                        nav.refreshTorvia()
                    }
                },
                onDismiss = { confirm = null },
            )
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
        Box(Modifier.size(56.dp, 84.dp).background(Nova.Surface, RoundedCornerShape(8.dp))) {
            if (a.poster != null) AsyncImage(a.poster, null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().background(Nova.Surface, RoundedCornerShape(8.dp)))
        }
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
            if (Lay.phone && (onOpen != null || onRemove != null)) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (onOpen != null) NovaButton("Open", Icons.Outlined.PlayArrow, onClick = onOpen)
                    if (onRemove != null) NovaButton("Remove", Icons.Outlined.Delete, onClick = onRemove)
                }
            }
        }
        if (!Lay.phone) {
            Spacer(Modifier.width(12.dp))
            if (onOpen != null) NovaButton("Open", Icons.Outlined.PlayArrow, onClick = onOpen)
            if (onOpen != null && onRemove != null) Spacer(Modifier.width(10.dp))
            if (onRemove != null) NovaButton("Remove", Icons.Outlined.Delete, onClick = onRemove)
        }
    }
}

// ============================================================================ One title: releases & add

private val qualityOrder = listOf("4K", "1080p", "720p", "480p", "?")
private val sorts = listOf("Best", "Smallest", "Largest", "Seeders")

@Composable
fun TorviaTitleScreen(nav: Navigator, type: String, tmdbId: Int, name: String) {
    val scope = rememberCoroutineScope()
    var details by remember { mutableStateOf<TorviaDetails?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    // TV shows: chosen season and episode (0 = whole season)
    var season by rememberSaveable { mutableIntStateOf(-1) }
    var episode by rememberSaveable { mutableIntStateOf(1) }

    var releases by remember { mutableStateOf<List<TorviaRelease>?>(null) }
    var relError by remember { mutableStateOf<String?>(null) }
    var inLibraryId by remember { mutableStateOf<String?>(null) }

    // Filters (applied on the device)
    var quality by rememberSaveable { mutableStateOf("1080p") }
    var tenBit by rememberSaveable { mutableStateOf(false) }
    var hdr by rememberSaveable { mutableStateOf(false) }
    var hideCam by rememberSaveable { mutableStateOf(true) }
    var sort by rememberSaveable { mutableIntStateOf(0) }

    var picked by remember { mutableStateOf<TorviaRelease?>(null) }
    var adding by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var waiting by remember { mutableStateOf(false) }   // waiting for Torvia to sync the new title

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
            // Default 1080p; if there's none, show every quality
            if (quality != "all" && r.releases.none { it.quality == quality }) quality = "all"
        }.onFailure { relError = it.message }
    }
    // After adding: check every 10 s (up to 3 min) until Torvia has put it in Jellyfin
    LaunchedEffect(waiting) {
        if (!waiting) return@LaunchedEffect
        repeat(18) {
            delay(10_000)
            torvia.inLibrary(type, tmdbId)?.let { inLibraryId = it; waiting = false; return@LaunchedEffect }
        }
        waiting = false
    }

    val all = releases.orEmpty()
    val shown = all
        .filter { quality == "all" || it.quality == quality }
        .filter { !tenBit || it.isTenBit }
        .filter { !hdr || it.isHdr }
        .filter { !hideCam || !it.isCam }
        .let { list ->
            when (sort) {
                1 -> list.sortedBy { it.sizeBytes }
                2 -> list.sortedByDescending { it.sizeBytes }
                3 -> list.sortedByDescending { it.seeders }
                else -> list // server order = best
            }
        }

    BackHandler(enabled = picked != null) { picked = null }

    Box(Modifier.fillMaxSize()) {
        val d = details
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 60.dp)) {
            item { TopNav(nav, Screen.Torvia) }
            if (d == null) {
                item { if (error != null) CenterMessage("Couldn't open $name", error) else NovaLoader() }
                return@LazyColumn
            }
            item { TitleHeader(d.title, inLibraryId, waiting, result, nav) }

            if (d.title.isTv && d.seasons.isNotEmpty()) {
                item {
                    ChipRow("Season") {
                        d.seasons.forEach { s -> SortChip(s.name.ifBlank { "Season ${s.season}" }, s.season == season) { season = s.season; episode = 1 } }
                    }
                    val count = d.seasons.firstOrNull { it.season == season }?.episodes ?: 0
                    ChipRow("Episode") {
                        SortChip("Whole season", episode == 0) { episode = 0 }
                        (1..count).forEach { e -> SortChip("E$e", episode == e) { episode = e } }
                    }
                }
            }

            item {
                val qualities = qualityOrder.filter { q -> all.any { it.quality == q } }
                ChipRow("Quality") {
                    SortChip("All", quality == "all") { quality = "all" }
                    qualities.forEach { q -> SortChip("$q (${all.count { it.quality == q }})", quality == q) { quality = q } }
                }
                ChipRow("Filter") {
                    if (all.any { it.isTenBit }) SortChip("10-bit", tenBit) { tenBit = !tenBit }
                    if (all.any { it.isHdr }) SortChip("HDR", hdr) { hdr = !hdr }
                    if (all.any { it.isCam }) SortChip("Hide CAM", hideCam) { hideCam = !hideCam }
                    sorts.forEachIndexed { i, s -> SortChip(s, sort == i) { sort = i } }
                }
                Spacer(Modifier.height(10.dp))
                SectionTitle(
                    when {
                        releases == null -> "Releases"
                        else -> "Releases · ${shown.size} of ${all.size}"
                    },
                    Modifier.padding(horizontal = Lay.side),
                )
                Spacer(Modifier.height(10.dp))
            }
            when {
                relError != null -> item { Text(relError!!, style = NovaType.Body, color = Nova.Rose, modifier = Modifier.padding(horizontal = Lay.side)) }
                releases == null -> item {
                    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(70.dp)) { NovaLoader() }
                        Text("Looking for cached releases… this can take a few seconds", style = NovaType.Label, color = Nova.TextDim)
                    }
                }
                all.isEmpty() -> item { Text("No cached releases found on TorBox for this.", style = NovaType.Body, color = Nova.TextDim, modifier = Modifier.padding(horizontal = Lay.side)) }
                shown.isEmpty() -> item { Text("No releases match these filters.", style = NovaType.Body, color = Nova.TextDim, modifier = Modifier.padding(horizontal = Lay.side)) }
                else -> items(shown, key = { it.hash + it.fileName }) { r ->
                    ReleaseRow(r) { picked = r }
                }
            }
        }

        picked?.let { r ->
            AddDialog(
                release = r,
                tags = nav.torvia.value?.tags.orEmpty(),
                defaultTag = if (type == "tv") nav.torvia.value?.tvTag.orEmpty() else nav.torvia.value?.movieTag.orEmpty(),
                allowance = allowanceText(nav),
                busy = adding,
                onAdd = { tag ->
                    adding = true
                    scope.launch {
                        val res = runCatching { torvia.add(r.hash, tag) }.getOrElse { org.phoenicx.nova.data.TorviaResult(false, it.message ?: "Couldn't add it.") }
                        adding = false
                        picked = null
                        result = res.message
                        if (res.ok) {
                            nav.refreshTorvia()
                            if (inLibraryId == null) waiting = true
                        }
                    }
                },
                onDismiss = { if (!adding) picked = null },
            )
        }
    }
}

@Composable
private fun TitleHeader(t: TorviaTitle, inLibraryId: String?, waiting: Boolean, result: String?, nav: Navigator) {
    Box(Modifier.fillMaxWidth()) {
        if (t.backdrop != null) {
            AsyncImage(t.backdrop, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(if (Lay.phone) 200.dp else 260.dp))
            Box(
                Modifier.fillMaxWidth().height(if (Lay.phone) 200.dp else 260.dp).background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Nova.Bg.copy(alpha = 0.3f), Nova.Bg))
                )
            )
        }
        Row(Modifier.padding(start = Lay.side, end = Lay.side, top = if (t.backdrop != null) 90.dp else 10.dp)) {
            Box(Modifier.size(if (Lay.phone) 100.dp else 130.dp, if (Lay.phone) 150.dp else 195.dp).background(Nova.Surface, RoundedCornerShape(12.dp))) {
                if (t.poster != null) AsyncImage(t.poster, t.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f).padding(top = if (t.backdrop != null) 40.dp else 0.dp)) {
                Text(t.title, style = NovaType.Title, color = Nova.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val meta = listOfNotNull(
                    if (t.isTv) "Series" else "Movie",
                    t.year?.toString(),
                    t.rating?.let { "★ " + String.format("%.1f", it) },
                ).joinToString(" · ")
                Text(meta, style = NovaType.Label, color = Nova.TextDim)
                Spacer(Modifier.height(8.dp))
                if (t.overview.isNotBlank()) Text(t.overview, style = NovaType.Body, color = Nova.TextDim, maxLines = if (Lay.phone) 4 else 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    Column(Modifier.padding(horizontal = Lay.side, vertical = 12.dp)) {
        if (result != null) {
            Text(result, style = NovaType.Body, color = Nova.Cyan)
            Spacer(Modifier.height(8.dp))
        }
        when {
            inLibraryId != null -> NovaButton("Open in library", Icons.Outlined.PlayArrow, primary = true) { nav.push(Screen.Detail(inLibraryId)) }
            waiting -> Text("Waiting for it to appear in Jellyfin…", style = NovaType.Label, color = Nova.TextDim)
        }
    }
}

@Composable
private fun ChipRow(label: String, chips: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp).horizontalScroll(rememberScrollState()).padding(horizontal = Lay.side),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label.uppercase(), style = NovaType.Caption, color = Nova.TextFaint, modifier = Modifier.width(78.dp))
        chips()
    }
}

@Composable
private fun ReleaseRow(r: TorviaRelease, onClick: () -> Unit) {
    FocusSurface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Lay.side, vertical = 5.dp),
        shape = RoundedCornerShape(16.dp),
        focusedScale = 1.01f,
    ) { focused ->
        Row(
            Modifier.fillMaxWidth()
                .background(if (focused) Nova.SurfaceHi else Color(0x12FFFFFF))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    GlassChip(r.quality.ifBlank { "?" }, accent = Nova.Cyan)
                    if (r.label.isNotBlank()) GlassChip(r.label)
                    if (r.isCam) GlassChip("⚠ CAM", accent = Nova.Rose)
                }
                Spacer(Modifier.height(6.dp))
                Text(r.name, style = NovaType.Label, color = Nova.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(r.summary, style = NovaType.Label, color = Nova.TextDim, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (r.playsLabel.isNotBlank()) {
                    Text("Plays ${r.playsLabel}" + if (r.fileName.isNotBlank()) " · ${r.fileName}" else "",
                        style = NovaType.Label, color = Nova.TextFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(r.size, style = NovaType.Section, color = Nova.Text, fontWeight = FontWeight.Bold)
                if (r.packSize.isNotBlank()) Text("pack ${r.packSize}", style = NovaType.Label, color = Nova.TextFaint)
                Text(listOf(r.speed, "${r.seeders} seeds").filter { it.isNotBlank() }.joinToString(" · "), style = NovaType.Label, color = Nova.TextDim)
            }
        }
    }
}

@Composable
private fun AddDialog(
    release: TorviaRelease,
    tags: List<String>,
    defaultTag: String,
    allowance: String?,
    busy: Boolean,
    onAdd: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var tag by remember { mutableStateOf(defaultTag.ifBlank { tags.firstOrNull() ?: "" }) }
    val addReq = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(100); runCatching { addReq.requestFocus() } }
    DialogFrame(onDismiss) {
        Text("Add this release?", style = NovaType.Title, color = Nova.Text)
        Spacer(Modifier.height(10.dp))
        Text(release.name, style = NovaType.Body, color = Nova.Text, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Text(release.summary, style = NovaType.Label, color = Nova.TextDim)
        if (allowance != null) {
            Spacer(Modifier.height(8.dp))
            Text(allowance, style = NovaType.Label, color = Nova.Cyan)
        }
        if (tags.size > 1) {
            Spacer(Modifier.height(14.dp))
            Text("ADD TO", style = NovaType.Caption, color = Nova.TextFaint)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tags.forEach { t -> SortChip(t, t == tag) { tag = t } }
            }
        }
        Spacer(Modifier.height(20.dp))
        if (busy) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp)) { NovaLoader() }
                Spacer(Modifier.width(10.dp))
                Text("Adding to TorBox… this can take up to half a minute", style = NovaType.Label, color = Nova.TextDim)
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NovaButton("Add", Icons.Outlined.Add, primary = true, focusRequester = addReq) { onAdd(tag.ifBlank { null }) }
                NovaButton("Cancel", onClick = onDismiss)
            }
        }
    }
}

@Composable
private fun ConfirmDialog(title: String, body: String, confirmText: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val cancelReq = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(100); runCatching { cancelReq.requestFocus() } }
    BackHandler { onDismiss() }
    DialogFrame(onDismiss) {
        Text(title, style = NovaType.Title, color = Nova.Text)
        Spacer(Modifier.height(10.dp))
        Text(body, style = NovaType.Body, color = Nova.TextDim)
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NovaButton("Cancel", focusRequester = cancelReq, onClick = onDismiss)
            NovaButton(confirmText, Icons.Outlined.Delete, primary = true, onClick = onConfirm)
        }
    }
}

/** A centred panel over a dimmed screen; tap outside (touch) closes it */
@Composable
private fun DialogFrame(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize()
            .background(Nova.BgDeep.copy(alpha = 0.78f))
            .pointerInputTapOutside(onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(20.dp)
                .widthIn(max = 620.dp)
                .fillMaxWidth()
                .background(Nova.Surface, RoundedCornerShape(24.dp))
                .border(1.dp, Nova.Stroke, RoundedCornerShape(24.dp))
                .pointerInputTapOutside {} // taps inside the panel don't close it
                .padding(24.dp)
        ) { content() }
    }
}

private fun Modifier.pointerInputTapOutside(onTap: () -> Unit): Modifier =
    this.pointerInput(onTap) { detectTapGestures { onTap() } }
