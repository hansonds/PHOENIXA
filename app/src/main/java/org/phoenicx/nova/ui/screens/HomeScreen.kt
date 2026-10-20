package org.phoenicx.nova.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Info
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import coil.compose.AsyncImage
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
import org.phoenicx.nova.ui.components.GlassChip
import org.phoenicx.nova.ui.components.LandscapeCard
import org.phoenicx.nova.ui.components.MetaRow
import org.phoenicx.nova.ui.components.NovaButton
import org.phoenicx.nova.ui.components.NovaLoader
import org.phoenicx.nova.ui.components.PivotScroll
import org.phoenicx.nova.ui.components.PosterCard
import org.phoenicx.nova.ui.components.SectionTitle
import org.phoenicx.nova.ui.components.TopNav
import org.phoenicx.nova.ui.components.api
import org.phoenicx.nova.ui.theme.Nova
import org.phoenicx.nova.ui.theme.NovaType

data class HomeRow(
    val key: String,
    val title: String,
    val items: List<Item>,
    val landscape: Boolean,
    val playOnClick: Boolean,
)

/** Lives for the whole session so Home keeps its place when you come back. */
class HomeState {
    var loaded by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var featured by mutableStateOf<List<Item>>(emptyList())
    var rows by mutableStateOf<List<HomeRow>>(emptyList())
    var heroIndex by mutableIntStateOf(0)
    var heroMode by mutableStateOf(true)
    var focusedItem by mutableStateOf<Item?>(null)
    var focusedRow by mutableIntStateOf(0)
    var lastFocusKey: String? = null
    val listState = LazyListState()
    val rowStates = mutableMapOf<String, LazyListState>()
    var lastRefresh = 0L

    fun rowState(key: String) = rowStates.getOrPut(key) { LazyListState() }

    suspend fun load(full: Boolean) = coroutineScope {
        try {
            error = null
            val resumeD = async { runCatching { api.resume() }.getOrDefault(emptyList()) }
            val nextD = async { runCatching { api.nextUp() }.getOrDefault(emptyList()) }
            if (full || featured.isEmpty()) {
                val featD = async { runCatching { api.featured(12) }.getOrDefault(emptyList()) }
                val libs = api.libraries()
                libsCache = libs
                val video = libs.filter { it.collectionType in setOf("movies", "tvshows", "homevideos", null) }
                val latestD = video.map { lib -> lib to async { runCatching { api.latest(lib.id) }.getOrDefault(emptyList()) } }
                val favD = async { runCatching { api.favorites() }.getOrDefault(emptyList()) }

                val list = mutableListOf<HomeRow>()
                list += HomeRow("resume", "Continue Watching", resumeD.await(), landscape = true, playOnClick = true)
                list += HomeRow("next", "Next Up", nextD.await(), landscape = true, playOnClick = true)
                latestD.forEach { (lib, d) ->
                    list += HomeRow("latest-${lib.id}", "Recently Added · ${lib.name}", d.await(), landscape = false, playOnClick = false)
                }
                list += HomeRow("fav", "My Favorites", favD.await(), landscape = false, playOnClick = false)
                featured = featD.await()
                rows = list.filter { it.items.isNotEmpty() }
                if (heroIndex >= featured.size) heroIndex = 0
            } else {
                val r = resumeD.await()
                val n = nextD.await()
                val updated = rows.toMutableList()
                fun upsert(key: String, title: String, items: List<Item>, pos: Int) {
                    val i = updated.indexOfFirst { it.key == key }
                    if (items.isEmpty()) { if (i >= 0) updated.removeAt(i); return }
                    val row = HomeRow(key, title, items, landscape = true, playOnClick = true)
                    if (i >= 0) updated[i] = row else updated.add(pos.coerceAtMost(updated.size), row)
                }
                upsert("resume", "Continue Watching", r, 0)
                upsert("next", "Next Up", n, if (updated.firstOrNull()?.key == "resume") 1 else 0)
                rows = updated
            }
            loaded = true
            lastRefresh = System.currentTimeMillis()
        } catch (e: Exception) {
            if (!loaded) error = e.message ?: "Couldn't reach your server"
        }
        Unit
    }

    var libsCache: List<org.phoenicx.nova.data.Library> = emptyList()
}

private const val HERO_INTERVAL_MS = 9000

@Composable
fun HomeScreen(nav: Navigator, state: HomeState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (!state.loaded) {
            state.load(full = true)
            nav.libraries.value = state.libsCache
        }
    }
    LifecycleResumeEffect(Unit) {
        if (state.loaded && System.currentTimeMillis() - state.lastRefresh > 4000) {
            scope.launch { state.load(full = false) }
        }
        onPauseOrDispose { }
    }

    if (!state.loaded) {
        if (state.error != null) {
            CenterMessage("Can't reach the server", state.error) {
                NovaButton("Try again", primary = true, onClick = { scope.launch { state.load(true); nav.libraries.value = state.libsCache } })
            }
        } else NovaLoader()
        return
    }

    val heroRequester = remember { FocusRequester() }
    val cardRequester = remember { FocusRequester() }
    var wantHeroFocus by remember { mutableStateOf(state.heroMode) }

    val featured = state.featured
    val heroItem = featured.getOrNull(state.heroIndex)

    // Debounce the ambient backdrop so fast scrolling doesn't thrash images
    var ambient by remember { mutableStateOf(state.focusedItem) }
    LaunchedEffect(state.focusedItem) {
        delay(380)
        ambient = state.focusedItem
    }

    // Auto-rotate hero
    val slideProgress = remember { Animatable(0f) }
    var interactions by remember { mutableIntStateOf(0) }
    LaunchedEffect(state.heroIndex, state.heroMode, featured.size, interactions) {
        slideProgress.snapTo(0f)
        if (!state.heroMode || featured.size < 2 || !org.phoenicx.nova.NovaApp.instance.prefs.heroAutoRotate) return@LaunchedEffect
        slideProgress.animateTo(1f, tween(HERO_INTERVAL_MS, easing = LinearEasing))
        state.heroIndex = (state.heroIndex + 1) % featured.size
    }

    BackHandler(enabled = !state.heroMode) {
        state.heroMode = true
        wantHeroFocus = true
        scope.launch { state.listState.animateScrollToItem(0) }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screenH = maxHeight
        val heroH: Dp = screenH - 142.dp
        val infoH: Dp = 236.dp
        val topH by animateDpAsState(if (state.heroMode) heroH else infoH, tween(420), label = "topH")

        val bgUrl = if (state.heroMode) heroItem?.let { api.backdrop(it) } else ambient?.let { api.backdrop(it) ?: api.landscape(it, 1280) }
        BackdropLayer(bgUrl, kenBurns = state.heroMode, dim = if (state.heroMode) 0f else 0.32f)

        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().height(topH)) {
                if (state.heroMode && heroItem != null) {
                    Column(Modifier.fillMaxSize()) {
                        TopNav(nav, Screen.Home)
                        Spacer(Modifier.weight(1f))
                        HeroInfo(heroItem, state.heroIndex)
                        Spacer(Modifier.height(22.dp))
                        Row(
                            Modifier
                                .padding(start = 56.dp, end = 56.dp, bottom = 18.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            var focusedBtn by remember { mutableIntStateOf(0) }
                            Row(
                                Modifier.onPreviewKeyEvent { e ->
                                    if (e.type != KeyEventType.KeyDown || featured.size < 2) return@onPreviewKeyEvent false
                                    when {
                                        e.key == Key.DirectionLeft && focusedBtn == 0 -> {
                                            state.heroIndex = (state.heroIndex - 1 + featured.size) % featured.size
                                            interactions++; true
                                        }
                                        e.key == Key.DirectionRight && focusedBtn == 2 -> {
                                            state.heroIndex = (state.heroIndex + 1) % featured.size
                                            interactions++; true
                                        }
                                        else -> false
                                    }
                                },
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                val resume = heroItem.playbackPositionTicks > 0 && !heroItem.played
                                NovaButton(
                                    if (resume) "Resume" else "Play", Icons.Filled.PlayArrow, primary = true,
                                    focusRequester = heroRequester, onFocused = { focusedBtn = 0 },
                                ) { Launcher.play(context, scope, heroItem) }
                                NovaButton("More Info", Icons.Outlined.Info, onFocused = { focusedBtn = 1 }) {
                                    nav.push(Screen.Detail(heroItem.id))
                                }
                                var fav by remember(heroItem.id) { mutableStateOf(heroItem.isFavorite) }
                                NovaButton("", if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, onFocused = { focusedBtn = 2 }) {
                                    fav = !fav
                                    scope.launch { runCatching { api.setFavorite(heroItem.id, fav) } }
                                }
                            }
                            Spacer(Modifier.weight(1f))
                            SlideIndicator(featured.size, state.heroIndex, slideProgress.value)
                        }
                    }
                } else if (!state.heroMode) {
                    FocusInfoPanel(state.focusedItem)
                }
            }

            // Content rows
            val density = LocalDensity.current
            val rowPivotPx = with(density) { 50.dp.toPx() }
            PivotScroll(0f, rowPivotPx) {
                LazyColumn(
                    state = state.listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .onFocusChanged { if (it.hasFocus) state.heroMode = false }
                        .onPreviewKeyEvent { e ->
                            if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionUp && state.focusedRow == 0 && !state.heroMode) {
                                state.heroMode = true
                                wantHeroFocus = true
                                true
                            } else false
                        },
                    contentPadding = PaddingValues(bottom = 200.dp),
                ) {
                    itemsIndexed(state.rows, key = { _, r -> r.key }) { rowIndex, row ->
                        ContentRow(
                            row = row,
                            rowIndex = rowIndex,
                            state = state,
                            cardRequester = cardRequester,
                            onClick = { item ->
                                if (row.playOnClick) Launcher.play(context, scope, item)
                                else nav.push(Screen.Detail(item.id))
                            },
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(wantHeroFocus, state.heroMode) {
        if (wantHeroFocus && state.heroMode) {
            delay(30)
            runCatching { heroRequester.requestFocus() }
            wantHeroFocus = false
        }
    }
    // Restore focus when coming back to Home
    LaunchedEffect(Unit) {
        delay(60)
        if (state.heroMode) runCatching { heroRequester.requestFocus() }
        else if (runCatching { cardRequester.requestFocus() }.isFailure) {
            state.heroMode = true; wantHeroFocus = true
        }
    }
}

@Composable
private fun ContentRow(
    row: HomeRow,
    rowIndex: Int,
    state: HomeState,
    cardRequester: FocusRequester,
    onClick: (Item) -> Unit,
) {
    val density = LocalDensity.current
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        SectionTitle(row.title, Modifier.padding(start = 56.dp))
        PivotScroll(0f, with(density) { 56.dp.toPx() }) {
            LazyRow(
                state = state.rowState(row.key),
                contentPadding = PaddingValues(horizontal = 56.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(if (row.landscape) 20.dp else 18.dp),
            ) {
                itemsIndexed(row.items, key = { _, it -> it.id }) { _, item ->
                    val key = "${row.key}/${item.id}"
                    val req = if (state.lastFocusKey == key) cardRequester else null
                    val onFocused = {
                        state.focusedItem = item
                        state.focusedRow = rowIndex
                        state.lastFocusKey = key
                    }
                    if (row.landscape) {
                        LandscapeCard(item, onClick = { onClick(item) }, focusRequester = req, onFocused = onFocused)
                    } else {
                        PosterCard(item, onClick = { onClick(item) }, focusRequester = req, onFocused = onFocused)
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroInfo(item: Item, index: Int) {
    AnimatedContent(
        targetState = item,
        transitionSpec = {
            (fadeIn(tween(600, 150)) + slideInHorizontally(tween(600, 150)) { -40 }) togetherWith fadeOut(tween(250))
        },
        label = "hero",
        modifier = Modifier.padding(horizontal = 56.dp),
    ) { it ->
        Column(Modifier.widthIn(max = 640.dp)) {
            Text(
                if (it.isSeries) "✦  FEATURED SERIES" else "✦  FEATURED FILM",
                style = NovaType.Caption, color = Nova.Cyan,
            )
            Spacer(Modifier.height(12.dp))
            TitleOrLogo(it, maxLogoH = 120.dp, maxLogoW = 460.dp, big = true)
            Spacer(Modifier.height(16.dp))
            MetaRow(it)
            if (it.genres.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    it.genres.take(4).forEach { g -> GlassChip(g) }
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                it.overview, style = NovaType.Body, color = Nova.TextDim,
                maxLines = 3, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun TitleOrLogo(item: Item, maxLogoH: Dp, maxLogoW: Dp, big: Boolean) {
    val logo = api.logo(item)
    var failed by remember(item.id) { mutableStateOf(logo == null) }
    if (!failed) {
        AsyncImage(
            model = logo,
            contentDescription = item.name,
            contentScale = ContentScale.Fit,
            alignment = Alignment.CenterStart,
            onError = { failed = true },
            modifier = Modifier.heightIn(max = maxLogoH).widthIn(max = maxLogoW),
        )
    } else {
        Text(
            if (item.isEpisode) item.seriesName ?: item.name else item.name,
            style = if (big) NovaType.Display else NovaType.Title,
            color = Nova.Text, maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SlideIndicator(count: Int, index: Int, progress: Float) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count.coerceAtMost(12)) { i ->
            val active = i == index
            Box(
                Modifier
                    .size(width = if (active) 42.dp else 10.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0x40FFFFFF))
            ) {
                if (active) {
                    Box(Modifier.fillMaxSize().padding(0.dp)) {
                        Box(
                            Modifier
                                .fillMaxWidth(progress.coerceIn(0.02f, 1f))
                                .height(4.dp)
                                .background(Nova.AccentBrushH)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FocusInfoPanel(item: Item?) {
    if (item == null) return
    AnimatedContent(
        targetState = item,
        transitionSpec = { (fadeIn(tween(300)) + slideInVertically(tween(300)) { 16 }) togetherWith fadeOut(tween(150)) },
        label = "info",
        modifier = Modifier.fillMaxSize().padding(start = 56.dp, top = 40.dp, end = 56.dp),
    ) { it ->
        Column(Modifier.widthIn(max = 680.dp)) {
            TitleOrLogo(it, maxLogoH = 72.dp, maxLogoW = 360.dp, big = false)
            Spacer(Modifier.height(12.dp))
            MetaRow(it)
            if (it.isEpisode) {
                Spacer(Modifier.height(8.dp))
                Text(listOfNotNull(it.episodeLabel, it.name).joinToString("  ·  "), style = NovaType.Label, color = Nova.Text)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                it.overview.ifEmpty { it.genres.joinToString(" • ") },
                style = NovaType.Body, color = Nova.TextDim, maxLines = 3, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
