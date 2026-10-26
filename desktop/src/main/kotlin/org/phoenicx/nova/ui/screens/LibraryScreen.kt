package org.phoenicx.nova.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.phoenicx.nova.App
import org.phoenicx.nova.Navigator
import org.phoenicx.nova.Screen
import org.phoenicx.nova.data.Item
import org.phoenicx.nova.data.Library
import org.phoenicx.nova.ui.Chip
import org.phoenicx.nova.ui.Loader
import org.phoenicx.nova.ui.Message
import org.phoenicx.nova.ui.Nova
import org.phoenicx.nova.ui.NovaType
import org.phoenicx.nova.ui.PosterCard
import org.phoenicx.nova.ui.TopBarHeight
import org.phoenicx.nova.ui.UiScale

private data class Sort(val label: String, val by: String, val order: String)

private val sorts = listOf(
    Sort("Recently Added", "DateCreated,SortName", "Descending"),
    Sort("A – Z", "SortName", "Ascending"),
    Sort("Top Rated", "CommunityRating,SortName", "Descending"),
    Sort("Release Year", "ProductionYear,PremiereDate,SortName", "Descending"),
    Sort("Random", "Random", "Ascending"),
)

/** Remembers the chosen sort for each library while the app is open */
private val sortMemory = mutableMapOf<String, Int>()

@Composable
fun LibraryScreen(nav: Navigator, lib: Library) {
    var sortIdx by remember(lib.id) { mutableIntStateOf(sortMemory[lib.id] ?: 0) }
    var items by remember(lib.id, sortIdx) { mutableStateOf<List<Item>>(emptyList()) }
    var total by remember(lib.id, sortIdx) { mutableIntStateOf(-1) }
    var loading by remember(lib.id, sortIdx) { mutableStateOf(false) }
    var error by remember(lib.id, sortIdx) { mutableStateOf<String?>(null) }
    val grid = rememberLazyGridState()
    val pageSize = 120

    suspend fun loadMore() {
        if (loading || (total >= 0 && items.size >= total)) return
        loading = true
        try {
            val s = sorts[sortIdx]
            val (page, count) = App.api.libraryItems(lib, s.by, s.order, items.size, pageSize)
            items = items + page
            total = count
        } catch (e: Exception) {
            error = e.message ?: "Couldn't load this library"
        }
        loading = false
    }

    LaunchedEffect(lib.id, sortIdx) {
        grid.scrollToItem(0)
        loadMore()
    }
    val nearEnd by remember(lib.id, sortIdx) {
        derivedStateOf {
            val last = grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= grid.layoutInfo.totalItemsCount - 30
        }
    }
    LaunchedEffect(nearEnd, items.size) { if (nearEnd && items.isNotEmpty()) loadMore() }

    val cardW = UiScale.posterWidth
    LazyVerticalGrid(
        columns = GridCells.Adaptive((cardW + 8).dp),
        state = grid,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = TopBarHeight + 12.dp, bottom = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(lib.name, style = NovaType.Title.copy(fontSize = 34.sp), color = Nova.Text)
                    if (total >= 0) Text("$total titles", style = NovaType.Button, color = Nova.TextFaint)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    sorts.forEachIndexed { i, s ->
                        Chip(s.label, i == sortIdx) { sortIdx = i; sortMemory[lib.id] = i }
                    }
                }
            }
        }
        items(items) { item ->
            Box(contentAlignment = Alignment.TopCenter) {
                PosterCard(item, cardW.dp) { nav.go(Screen.Detail(item.id)) }
            }
        }
        if (loading) item(span = { GridItemSpan(maxLineSpan) }) { Loader() }
        error?.let { msg -> item(span = { GridItemSpan(maxLineSpan) }) { Message(msg) } }
        if (!loading && total == 0) item(span = { GridItemSpan(maxLineSpan) }) { Message("This library is empty") }
    }
}
