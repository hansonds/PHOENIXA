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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.phoenicx.nova.data.Item
import org.phoenicx.nova.data.Library
import org.phoenicx.nova.ui.Navigator
import org.phoenicx.nova.ui.Screen
import org.phoenicx.nova.ui.components.CenterMessage
import org.phoenicx.nova.ui.components.FocusSurface
import org.phoenicx.nova.ui.components.NovaLoader
import org.phoenicx.nova.ui.components.PosterCard
import org.phoenicx.nova.ui.components.TopNav
import org.phoenicx.nova.ui.components.api
import org.phoenicx.nova.ui.theme.Nova
import org.phoenicx.nova.ui.theme.NovaType
import org.phoenicx.nova.ui.theme.UiScale
import kotlinx.coroutines.delay

private data class SortOpt(val label: String, val by: String, val order: String)

private val sorts = listOf(
    SortOpt("Recently Added", "DateCreated,SortName", "Descending"),
    SortOpt("A – Z", "SortName", "Ascending"),
    SortOpt("Top Rated", "CommunityRating,SortName", "Descending"),
    SortOpt("Release Year", "ProductionYear,SortName", "Descending"),
    SortOpt("Random", "Random", "Ascending"),
)

@Composable
fun LibraryScreen(nav: Navigator, lib: Library) {
    val fromNav = remember { nav.focusNavOnEnter }
    val saved = remember { if (fromNav) null else nav.libraryPos[lib.id] }
    var sortIdx by remember { mutableIntStateOf(saved?.first ?: 0) }
    val restoreRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    var restoreIndex by remember { mutableIntStateOf(saved?.second ?: -1) }
    var items by remember { mutableStateOf<List<Item>>(emptyList()) }
    var total by remember { mutableIntStateOf(-1) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val grid = rememberLazyGridState()

    suspend fun loadMore(reset: Boolean) {
        if (loading) return
        if (!reset && total >= 0 && items.size >= total) return
        loading = true
        try {
            val s = sorts[sortIdx]
            val (page, t) = api.libraryItems(lib, s.by, s.order, if (reset) 0 else items.size, 60)
            items = if (reset) page else items + page
            total = t
            error = null
        } catch (e: Exception) {
            error = e.message
        } finally {
            loading = false
        }
    }

    LaunchedEffect(lib.id, sortIdx) {
        items = emptyList(); total = -1
        loadMore(reset = true)
        val target = restoreIndex
        if (target > 0) {
            while (items.size <= target && (total < 0 || items.size < total)) {
                val before = items.size
                loadMore(reset = false)
                if (items.size == before) break
            }
        }
        grid.scrollToItem(target.coerceIn(0, (items.size - 1).coerceAtLeast(0)))
        if (target >= 0) {
            delay(120)
            runCatching { restoreRequester.requestFocus() }
            restoreIndex = -1
        }
    }
    LaunchedEffect(grid) {
        snapshotFlow { grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { last -> if (last > items.size - 21) loadMore(reset = false) }
    }

    Column(Modifier.fillMaxSize()) {
        TopNav(nav, Screen.Lib(lib))
        Row(Modifier.padding(start = 56.dp, end = 56.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(lib.name, style = NovaType.Title, color = Nova.Text)
                if (total >= 0) Text("$total titles", style = NovaType.Label, color = Nova.TextFaint)
            }
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                sorts.forEachIndexed { i, s ->
                    SortChip(s.label, i == sortIdx) { sortIdx = i }
                }
            }
        }
        when {
            items.isEmpty() && error != null -> CenterMessage("Couldn't load library", error)
            items.isEmpty() && (loading || total < 0) -> NovaLoader()
            items.isEmpty() -> CenterMessage("Nothing here yet")
            else -> LazyVerticalGrid(
                state = grid,
                columns = GridCells.Adaptive(UiScale.posterWidth.dp),
                contentPadding = PaddingValues(start = 56.dp, end = 56.dp, top = 18.dp, bottom = 60.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(items, key = { _, it -> it.id }) { index, item ->
                    PosterCard(
                        item,
                        onClick = {
                            nav.libraryPos[lib.id] = sortIdx to index
                            nav.push(Screen.Detail(item.id))
                        },
                        width = UiScale.posterWidth,
                        showTitle = true,
                        focusRequester = if (index == (saved?.second ?: -2)) restoreRequester else null,
                    )
                }
            }
        }
    }
}

@Composable
fun SortChip(text: String, selected: Boolean, onClick: () -> Unit) {
    FocusSurface(onClick = onClick, shape = RoundedCornerShape(18.dp), focusedScale = 1.05f, borderWidth = 0.dp, glow = false) { focused ->
        val bg = when {
            focused -> Brush.horizontalGradient(listOf(Color(0xFFF4F7FF), Color(0xFFE2E8FF)))
            selected -> Brush.horizontalGradient(listOf(Color(0x556CE5F5), Color(0x55A58BFF)))
            else -> Brush.horizontalGradient(listOf(Color(0x1FFFFFFF), Color(0x14FFFFFF)))
        }
        Box(
            Modifier.background(bg).border(1.dp, Nova.Stroke, RoundedCornerShape(18.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(text, style = NovaType.Label, color = if (focused) Color(0xFF12152A) else Nova.Text)
        }
    }
}

