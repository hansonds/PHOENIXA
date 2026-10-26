package org.phoenicx.nova.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.phoenicx.nova.App
import org.phoenicx.nova.Navigator
import org.phoenicx.nova.Screen
import org.phoenicx.nova.data.Item
import org.phoenicx.nova.player.Player
import org.phoenicx.nova.ui.Chip
import org.phoenicx.nova.ui.Loader
import org.phoenicx.nova.ui.Message
import org.phoenicx.nova.ui.NetImage
import org.phoenicx.nova.ui.Nova
import org.phoenicx.nova.ui.NovaType
import org.phoenicx.nova.ui.PosterCard
import org.phoenicx.nova.ui.TopBarHeight
import org.phoenicx.nova.ui.UiScale
import org.phoenicx.nova.ui.clickableHand

/** A page with a header on top and a grid of posters below */
@Composable
fun PosterGridPage(
    nav: Navigator,
    items: List<Item>,
    loading: Boolean,
    emptyText: String?,
    header: @Composable () -> Unit,
) {
    val cardW = UiScale.posterWidth
    LazyVerticalGrid(
        columns = GridCells.Adaptive((cardW + 8).dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = TopBarHeight + 12.dp, bottom = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { header() }
        items(items) { item ->
            Box(contentAlignment = Alignment.TopCenter) {
                PosterCard(item, cardW.dp) {
                    if (item.isEpisode) Player.play(item) else nav.go(Screen.Detail(item.id))
                }
            }
        }
        if (loading) item(span = { GridItemSpan(maxLineSpan) }) { Loader() }
        else if (items.isEmpty() && emptyText != null) item(span = { GridItemSpan(maxLineSpan) }) { Message(emptyText) }
    }
}

@Composable
private fun PageTitle(text: String, sub: String? = null) {
    Column(Modifier.padding(bottom = 6.dp)) {
        Text(text, style = NovaType.Title.copy(fontSize = 34.sp), color = Nova.Text)
        if (sub != null) Text(sub, style = NovaType.Button, color = Nova.TextFaint)
    }
}

// ---------------------------------------------------------------- Search

private var lastQuery = ""

@Composable
fun SearchScreen(nav: Navigator) {
    var query by remember { mutableStateOf(lastQuery) }
    var results by remember { mutableStateOf<List<Item>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) { delay(100); runCatching { focus.requestFocus() } }
    LaunchedEffect(query) {
        lastQuery = query
        val q = query.trim()
        if (q.length < 2) { results = emptyList(); loading = false; return@LaunchedEffect }
        loading = true
        delay(350)
        try {
            results = App.api.search(q)
            error = null
        } catch (e: Exception) {
            error = e.message
        }
        loading = false
    }

    PosterGridPage(nav, results, loading, if (query.trim().length >= 2) (error ?: "Nothing found for “${query.trim()}”") else null) {
        Column {
            PageTitle("Search")
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Movies, shows, episodes…", color = Nova.TextFaint) },
                leadingIcon = { Icon(Icons.Outlined.Search, null, tint = Nova.TextDim) },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Nova.Cyan,
                    unfocusedBorderColor = Nova.Stroke,
                    cursorColor = Nova.Cyan,
                    focusedTextColor = Nova.Text,
                    unfocusedTextColor = Nova.Text,
                    focusedContainerColor = Color(0x14FFFFFF),
                    unfocusedContainerColor = Color(0x0AFFFFFF),
                ),
                modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().focusRequester(focus),
            )
        }
    }
}

// ---------------------------------------------------------------- Favorites

@Composable
fun FavoritesScreen(nav: Navigator) {
    val filters = listOf("All" to "Movie,Series,Episode", "Movies" to "Movie", "Shows" to "Series", "Episodes" to "Episode")
    var filter by remember { mutableIntStateOf(0) }
    var items by remember { mutableStateOf<List<Item>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(filter, App.refreshTick) {
        loading = true
        try {
            items = App.api.favorites(filters[filter].second, 300)
            error = null
        } catch (e: Exception) {
            error = e.message
        }
        loading = false
    }
    PosterGridPage(nav, items, loading, error ?: "No favorites yet. Press the heart on any title to add it here.") {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { PageTitle("My Favorites", if (!loading) "${items.size} titles" else null) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                filters.forEachIndexed { i, (label, _) -> Chip(label, i == filter) { filter = i } }
            }
        }
    }
}

// ---------------------------------------------------------------- Actor page

@Composable
fun PersonScreen(nav: Navigator, id: String, name: String) {
    var person by remember(id) { mutableStateOf<Item?>(null) }
    var films by remember(id) { mutableStateOf<List<Item>>(emptyList()) }
    var loading by remember(id) { mutableStateOf(true) }
    var expanded by remember(id) { mutableStateOf(false) }
    var bioDone by remember(id) { mutableStateOf(false) }
    LaunchedEffect(id) {
        films = runCatching { App.api.filmography(id) }.getOrDefault(emptyList())
        loading = false
        person = runCatching { App.api.personWithBio(id, name) }.getOrNull()
        bioDone = true
    }
    val p = person
    PosterGridPage(nav, films, loading, "Nothing from $name on your server") {
        Row(Modifier.fillMaxWidth().padding(bottom = 18.dp)) {
            Box(
                Modifier.width(200.dp).height(300.dp).clip(RoundedCornerShape(18.dp)).border(1.dp, Nova.Stroke, RoundedCornerShape(18.dp))
            ) {
                NetImage(App.api.image(id, "Primary", null, 500), Modifier.fillMaxSize(), fallback = Icons.Outlined.Person)
            }
            Spacer(Modifier.width(32.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = NovaType.Display, color = Nova.Text)
                val born = p?.premiereDate?.take(10)
                val place = p?.locations?.firstOrNull()
                if (born != null || place != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(listOfNotNull(born?.let { "Born $it" }, place).joinToString(" · "), style = NovaType.Button, color = Nova.TextDim)
                }
                Spacer(Modifier.height(14.dp))
                val bio = p?.overview?.takeIf { it.isNotBlank() }
                when {
                    !bioDone -> Text("Loading biography…", style = NovaType.Body, color = Nova.TextFaint)
                    bio == null -> Text("No biography available.", style = NovaType.Body, color = Nova.TextFaint)
                    else -> {
                        Text(
                            bio, style = NovaType.Body, color = Nova.TextDim,
                            maxLines = if (expanded) Int.MAX_VALUE else 5, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 900.dp),
                        )
                        if (bio.length > 380) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                if (expanded) "Show less" else "Read more", style = NovaType.Button, color = Nova.Cyan,
                                modifier = Modifier.clickableHand { expanded = !expanded },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
                Text("ON YOUR SERVER", style = NovaType.Caption, color = Nova.TextFaint)
            }
        }
    }
}
