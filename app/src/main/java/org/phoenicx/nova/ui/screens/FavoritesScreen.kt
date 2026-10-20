package org.phoenicx.nova.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.unit.dp
import org.phoenicx.nova.data.Item
import org.phoenicx.nova.ui.Navigator
import org.phoenicx.nova.ui.Screen
import org.phoenicx.nova.ui.components.CenterMessage
import org.phoenicx.nova.ui.components.NovaLoader
import org.phoenicx.nova.ui.components.PosterCard
import org.phoenicx.nova.ui.components.TopNav
import org.phoenicx.nova.ui.components.api
import org.phoenicx.nova.ui.theme.Nova
import org.phoenicx.nova.ui.theme.NovaType
import org.phoenicx.nova.ui.theme.UiScale

private val filters = listOf(
    "All" to "Movie,Series,Episode",
    "Movies" to "Movie",
    "Shows" to "Series",
    "Episodes" to "Episode",
)

@Composable
fun FavoritesScreen(nav: Navigator) {
    var filter by remember { mutableIntStateOf(0) }
    var items by remember { mutableStateOf<List<Item>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(filter) {
        items = null
        runCatching { api.favorites(filters[filter].second, 300) }
            .onSuccess { items = it; error = null }
            .onFailure { error = it.message; items = emptyList() }
    }

    Column(Modifier.fillMaxSize()) {
        TopNav(nav, Screen.Favorites)
        Row(Modifier.padding(start = 56.dp, end = 56.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Favorites", style = NovaType.Title, color = Nova.Text)
                items?.let { Text("${it.size} titles", style = NovaType.Label, color = Nova.TextFaint) }
            }
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                filters.forEachIndexed { i, f -> SortChip(f.first, i == filter) { filter = i } }
            }
        }
        val list = items
        when {
            list == null -> NovaLoader()
            error != null -> CenterMessage("Couldn't load favorites", error)
            list.isEmpty() -> CenterMessage("No favorites yet", "Press ♥ on any movie or show to save it here")
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(UiScale.posterWidth.dp),
                contentPadding = PaddingValues(start = 56.dp, end = 56.dp, top = 18.dp, bottom = 60.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(list, key = { it.id }) { item ->
                    PosterCard(item, onClick = { nav.push(Screen.Detail(item.id)) }, width = UiScale.posterWidth, showTitle = true)
                }
            }
        }
    }
}
