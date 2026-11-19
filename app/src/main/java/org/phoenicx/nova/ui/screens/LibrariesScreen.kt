package org.phoenicx.nova.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import org.phoenicx.nova.data.Library
import org.phoenicx.nova.ui.Navigator
import org.phoenicx.nova.ui.Screen
import org.phoenicx.nova.ui.components.FocusSurface
import org.phoenicx.nova.ui.components.ShimmerBox
import org.phoenicx.nova.ui.components.TopNav
import org.phoenicx.nova.ui.components.api
import org.phoenicx.nova.ui.theme.Lay
import org.phoenicx.nova.ui.theme.Nova
import org.phoenicx.nova.ui.theme.NovaType

/** Phones: every library on one page (the bottom bar's "Library" tab), plus Favorites */
@Composable
fun LibrariesScreen(nav: Navigator) {
    LaunchedEffect(Unit) {
        if (nav.libraries.value.isEmpty()) runCatching { api.libraries() }.onSuccess { nav.libraries.value = it }
    }
    val libs = nav.libraries.value.filter { it.collectionType in setOf("movies", "tvshows", "boxsets", null, "homevideos") }

    Column(Modifier.fillMaxSize()) {
        TopNav(nav, Screen.Libraries)
        PageHeader("Library", if (libs.isNotEmpty()) "${libs.size} libraries" else null) {}
        LazyVerticalGrid(
            columns = GridCells.Adaptive(if (Lay.phone) 160.dp else 300.dp),
            contentPadding = PaddingValues(start = Lay.side, end = Lay.side, top = 14.dp, bottom = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "fav") {
                Tile("Favorites", null, Icons.Filled.Favorite) { nav.push(Screen.Favorites) }
            }
            items(libs, key = { it.id }) { lib ->
                Tile(lib.name, lib.primaryTag?.let { api.image(lib.id, "Primary", it, 640) }, iconFor(lib)) { nav.push(Screen.Lib(lib)) }
            }
        }
    }
}

private fun iconFor(lib: Library): ImageVector = when (lib.collectionType) {
    "movies" -> Icons.Outlined.Movie
    "tvshows" -> Icons.Outlined.Tv
    "boxsets" -> Icons.Outlined.CollectionsBookmark
    else -> Icons.Outlined.VideoLibrary
}

@Composable
private fun Tile(name: String, image: String?, icon: ImageVector, onClick: () -> Unit) {
    FocusSurface(onClick = onClick, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f), shape = RoundedCornerShape(16.dp)) { _ ->
        if (image != null) {
            ShimmerBox(Modifier.fillMaxSize())
            AsyncImage(image, name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Nova.Cyan.copy(alpha = 0.45f), Nova.Violet.copy(alpha = 0.35f)))))
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to Nova.BgDeep.copy(alpha = 0.92f))))
        Row(Modifier.align(Alignment.BottomStart).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(name, style = NovaType.Section, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
