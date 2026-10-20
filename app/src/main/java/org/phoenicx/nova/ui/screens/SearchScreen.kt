package org.phoenicx.nova.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
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

@Composable
fun SearchScreen(nav: Navigator) {
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Item>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    val fieldRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { delay(120); runCatching { fieldRequester.requestFocus() } }
    LaunchedEffect(query) {
        if (query.trim().length < 2) { results = emptyList(); return@LaunchedEffect }
        delay(450)
        loading = true
        results = runCatching { api.search(query.trim()) }.getOrDefault(emptyList())
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
            modifier = Modifier.padding(horizontal = 56.dp).width(620.dp),
        )
        when {
            loading && results.isEmpty() -> NovaLoader()
            query.trim().length >= 2 && results.isEmpty() && !loading -> CenterMessage("No results", "Try a different title")
            results.isEmpty() -> CenterMessage("Find something to watch", "Start typing a title, actor or genre")
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(152.dp),
                contentPadding = PaddingValues(start = 56.dp, end = 56.dp, top = 24.dp, bottom = 60.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(results, key = { it.id }) { item ->
                    PosterCard(item, onClick = { nav.push(Screen.Detail(item.id)) }, width = 152, showTitle = true)
                }
            }
        }
    }
}
