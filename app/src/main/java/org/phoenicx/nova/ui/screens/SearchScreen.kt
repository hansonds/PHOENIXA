package org.phoenicx.nova.ui.screens

import org.phoenicx.nova.ui.components.wrapRowFocus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.phoenicx.nova.data.Item
import org.phoenicx.nova.ui.Navigator
import org.phoenicx.nova.ui.Screen
import org.phoenicx.nova.ui.components.CenterMessage
import org.phoenicx.nova.ui.components.NovaLoader
import org.phoenicx.nova.ui.components.NovaTextField
import org.phoenicx.nova.ui.components.PosterCard
import org.phoenicx.nova.ui.components.TopNav
import org.phoenicx.nova.ui.components.api
import org.phoenicx.nova.ui.theme.Lay
import org.phoenicx.nova.ui.theme.UiScale

@Composable
fun SearchScreen(nav: Navigator) {
    var query by remember { mutableStateOf(nav.searchQuery) }
    var results by remember { mutableStateOf(nav.searchResults) }
    var searched by remember { mutableStateOf(nav.searchQuery) }
    var loading by remember { mutableStateOf(false) }
    val fieldRequester = remember { FocusRequester() }
    val gf = org.phoenicx.nova.ui.components.rememberGridFocus(nav, "search", firstOnOpen = false)
    val grid = androidx.compose.foundation.lazy.grid.rememberLazyGridState()

    val fromNav = remember { nav.focusNavOnEnter }
    // Opened fresh: the search box. Back from a title: that result (see GridFocusEffect below).
    LaunchedEffect(Unit) { if (!fromNav && gf.target < 0) { delay(120); runCatching { fieldRequester.requestFocus() } } }
    LaunchedEffect(query) {
        nav.searchQuery = query
        if (query == searched && results.isNotEmpty()) return@LaunchedEffect   // already showing these
        if (query.trim().length < 2) { results = emptyList(); nav.searchResults = emptyList(); return@LaunchedEffect }
        delay(450)
        loading = true
        results = runCatching { api.search(query.trim()) }.getOrDefault(emptyList())
        nav.searchResults = results
        searched = query
        loading = false
    }

    Column(Modifier.fillMaxSize()) {
        TopNav(nav, Screen.Search)
        NovaTextField(
            query, { query = it }, label = "",
            placeholder = "Search movies, shows, episodes…",
            icon = Icons.Outlined.Search,
            imeAction = ImeAction.Search,
            focusRequester = fieldRequester,
            modifier = Modifier.padding(horizontal = Lay.side).then(if (Lay.phone) Modifier.fillMaxWidth() else Modifier.width(620.dp)),
        )
        when {
            loading && results.isEmpty() -> org.phoenicx.nova.ui.components.SkeletonGrid()
            query.trim().length >= 2 && results.isEmpty() && !loading -> CenterMessage("No results", "Try a different title")
            results.isEmpty() -> CenterMessage("Find something to watch", "Start typing a title, actor or genre")
            else -> LazyVerticalGrid(
                state = grid,
                columns = GridCells.Adaptive(UiScale.posterWidth.dp),
                contentPadding = PaddingValues(start = Lay.side, end = Lay.side, top = 24.dp, bottom = 60.dp),
                horizontalArrangement = Arrangement.spacedBy(if (Lay.phone) 12.dp else 20.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
                modifier = Modifier.fillMaxSize().wrapRowFocus(),
            ) {
                items(results.size, key = { results[it].id }) { i ->
                    val item = results[i]
                    PosterCard(item, onClick = { nav.push(Screen.Detail(item.id)) }, width = UiScale.posterWidth, showTitle = true,
                        focusRequester = gf.req(i), onFocused = { gf.focused(i) })
                }
            }
        }
        org.phoenicx.nova.ui.components.GridFocusEffect(gf, results.isNotEmpty()) { grid.scrollToItem(it.coerceAtMost(results.size - 1)) }
    }
}
