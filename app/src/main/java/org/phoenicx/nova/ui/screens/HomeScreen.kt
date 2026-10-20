package org.phoenicx.nova.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import coil.compose.AsyncImage
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.phoenicx.nova.NovaApp
import org.phoenicx.nova.data.Item
import org.phoenicx.nova.player.Launcher
import org.phoenicx.nova.ui.Navigator
import org.phoenicx.nova.ui.Screen
import org.phoenicx.nova.ui.components.BackdropLayer
import org.phoenicx.nova.ui.components.CenterMessage
import org.phoenicx.nova.ui.components.EdgeScroll
import org.phoenicx.nova.ui.components.LandscapeCard
import org.phoenicx.nova.ui.components.MetaRow
import org.phoenicx.nova.ui.components.NovaButton
import org.phoenicx.nova.ui.components.NovaLoader
import org.phoenicx.nova.ui.components.PosterCard
import org.phoenicx.nova.ui.components.SectionTitle
import org.phoenicx.nova.ui.components.TopNav
import org.phoenicx.nova.ui.components.api
import org.phoenicx.nova.ui.theme.UiScale
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


private const val HERO_INTERVAL_MS = 10000

@Composable
fun HomeScreen(nav: Navigator, state: HomeState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fromNav = remember { nav.focusNavOnEnter }

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
        Column(Modifier.fillMaxSize()) {
            TopNav(nav, Screen.Home)
            if (state.error != null) {
                CenterMessage("Can't reach the server", state.error) {
                    NovaButton("Try again", primary = true, onClick = { scope.launch { state.load(true); nav.libraries.value = state.libsCache } })
                }
            } else NovaLoader()
        }
        return
    }

    val heroRequester = remember { FocusRequester() }
    val cardRequester = remember { FocusRequester() }
    val featured = state.featured
    val heroItem = featured.getOrNull(state.heroIndex)
    // true while focus is on the banner buttons or the top bar
    var heroArea by remember { mutableStateOf(state.heroMode) }

    fun goToHero() {
        scope.launch {
            state.listState.scrollToItem(0)
            delay(30)
            runCatching { heroRequester.requestFocus() }
        }
    }

    // Auto-rotate the banner, only while you're up in the banner area
    val slideProgress = remember { Animatable(0f) }
    var interactions by remember { mutableIntStateOf(0) }
    LaunchedEffect(state.heroIndex, heroArea, featured.size, interactions) {
        slideProgress.snapTo(0f)
        if (!heroArea || featured.size < 2 || !NovaApp.instance.prefs.heroAutoRotate) return@LaunchedEffect
        slideProgress.animateTo(1f, tween(HERO_INTERVAL_MS, easing = LinearEasing))
        state.heroIndex = (state.heroIndex + 1) % featured.size
    }

    BackHandler(enabled = !heroArea) {
        heroArea = true
        state.heroMode = true
        goToHero()
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val heroH: Dp = (maxHeight - 88.dp - 128.dp).coerceAtLeast(300.dp)
        BackdropLayer(
            heroItem?.let { api.backdrop(it) },
            kenBurns = false,
            dim = if (heroArea) 0f else 0.5f,
        )

        Column(Modifier.fillMaxSize()) {
            // Top bar stays put, always reachable with UP
            Box(Modifier.onFocusChanged { if (it.hasFocus) { heroArea = true; state.heroMode = true } }) {
                TopNav(nav, Screen.Home)
            }

            EdgeScroll(startMargin = 46.dp, endMargin = 24.dp) {
                LazyColumn(
                    state = state.listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .onPreviewKeyEvent { e ->
                            // From the first row, UP always lands cleanly on the banner buttons
                            if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionUp && !heroArea && state.focusedRow == 0) {
                                heroArea = true
                                state.heroMode = true
                                goToHero()
                                true
                            } else false
                        },
                    contentPadding = PaddingValues(bottom = 120.dp),
                ) {
                    item(key = "hero") {
                        if (heroItem != null) {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .height(heroH)
                                    .onFocusChanged { if (it.hasFocus) { heroArea = true; state.heroMode = true } }
                                    .padding(horizontal = 56.dp),
                                verticalArrangement = Arrangement.Bottom,
                            ) {
                                HeroInfo(heroItem)
                                Spacer(Modifier.height(18.dp))
                                Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
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
                        } else {
                            Spacer(Modifier.height(24.dp))
                        }
                    }
                    itemsIndexed(state.rows, key = { _, r -> r.key }) { rowIndex, row ->
                        ContentRow(
                            row = row,
                            rowIndex = rowIndex,
                            state = state,
                            cardRequester = cardRequester,
                            onRowFocused = { heroArea = false; state.heroMode = false },
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

    // Restore focus when coming back to Home (the top bar handles it when opened from the top bar)
    LaunchedEffect(Unit) {
        if (fromNav) return@LaunchedEffect
        delay(80)
        if (state.heroMode || state.lastFocusKey == null) {
            runCatching { heroRequester.requestFocus() }
        } else if (runCatching { cardRequester.requestFocus() }.isFailure) {
            heroArea = true; state.heroMode = true; goToHero()
        }
    }
}

@Composable
private fun ContentRow(
    row: HomeRow,
    rowIndex: Int,
    state: HomeState,
    cardRequester: FocusRequester,
    onRowFocused: () -> Unit,
    onClick: (Item) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .onFocusChanged { if (it.hasFocus) { state.focusedRow = rowIndex; onRowFocused() } }
    ) {
        SectionTitle(row.title, Modifier.padding(start = 56.dp))
        EdgeScroll(56.dp, 56.dp) {
            LazyRow(
                state = state.rowState(row.key),
                contentPadding = PaddingValues(horizontal = 56.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(if (row.landscape) 18.dp else 16.dp),
            ) {
                itemsIndexed(row.items, key = { _, it -> it.id }) { _, item ->
                    val key = "${row.key}/${item.id}"
                    val req = if (state.lastFocusKey == key) cardRequester else null
                    val onFocused = {
                        state.focusedItem = item
                        state.lastFocusKey = key
                    }
                    if (row.landscape) {
                        LandscapeCard(item, onClick = { onClick(item) }, width = UiScale.landscapeWidth, focusRequester = req, onFocused = onFocused)
                    } else {
                        PosterCard(item, onClick = { onClick(item) }, width = UiScale.posterWidth, showTitle = true, focusRequester = req, onFocused = onFocused)
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroInfo(item: Item) {
    AnimatedContent(
        targetState = item,
        transitionSpec = { fadeIn(tween(450)) togetherWith fadeOut(tween(200)) },
        label = "hero",
    ) { it ->
        Column(Modifier.widthIn(max = 620.dp)) {
            Text(
                if (it.isSeries) "✦  FEATURED SERIES" else "✦  FEATURED FILM",
                style = NovaType.Caption, color = Nova.Cyan,
            )
            Spacer(Modifier.height(10.dp))
            TitleOrLogo(it, maxLogoH = 96.dp, maxLogoW = 420.dp, big = true)
            Spacer(Modifier.height(14.dp))
            MetaRow(it)
            Spacer(Modifier.height(10.dp))
            Text(
                listOfNotNull(
                    it.genres.take(3).joinToString(" • ").takeIf { g -> g.isNotEmpty() },
                ).joinToString() ,
                style = NovaType.Label, color = Nova.Text,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                it.overview, style = NovaType.Body, color = Nova.TextDim,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
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
