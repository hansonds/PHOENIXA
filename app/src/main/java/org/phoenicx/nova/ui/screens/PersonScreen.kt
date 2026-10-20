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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import org.phoenicx.nova.data.Item
import org.phoenicx.nova.ui.Navigator
import org.phoenicx.nova.ui.Screen
import org.phoenicx.nova.ui.components.CenterMessage
import org.phoenicx.nova.ui.components.GlassChip
import org.phoenicx.nova.ui.components.NovaLoader
import org.phoenicx.nova.ui.components.PosterCard
import org.phoenicx.nova.ui.components.SectionTitle
import org.phoenicx.nova.ui.components.api
import org.phoenicx.nova.ui.theme.Nova
import org.phoenicx.nova.ui.theme.NovaType
import org.phoenicx.nova.ui.theme.UiScale
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private fun parseDate(s: String?): Calendar? {
    if (s.isNullOrBlank() || s.length < 10) return null
    return runCatching {
        val d = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(s.substring(0, 10)) ?: return null
        Calendar.getInstance().apply { time = d }
    }.getOrNull()
}

private fun prettyDate(c: Calendar): String = SimpleDateFormat("d MMMM yyyy", Locale.ENGLISH).format(c.time)

private fun ageBetween(birth: Calendar, end: Calendar): Int {
    var age = end.get(Calendar.YEAR) - birth.get(Calendar.YEAR)
    if (end.get(Calendar.DAY_OF_YEAR) < birth.get(Calendar.DAY_OF_YEAR)) age--
    return age
}

/** Actor / director page: photo, birth details, biography and everything they're in on your server. */
@Composable
fun PersonScreen(nav: Navigator, personId: String, name: String) {
    var person by remember { mutableStateOf<Item?>(null) }
    var works by remember { mutableStateOf<List<Item>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val firstCard = remember { FocusRequester() }

    LaunchedEffect(personId) {
        try {
            coroutineScope {
                val p = async { runCatching { api.person(personId) }.getOrNull() }
                val w = async { api.filmography(personId) }
                works = w.await()
                person = p.await()
            }
        } catch (e: Exception) {
            error = e.message ?: "Couldn't load"
            works = emptyList()
        }
    }
    LaunchedEffect(works) {
        if (!works.isNullOrEmpty()) { delay(120); runCatching { firstCard.requestFocus() } }
    }

    val list = works
    if (list == null) { NovaLoader(); return }
    if (error != null && list.isEmpty()) { CenterMessage("Couldn't load $name", error); return }

    val p = person
    val birth = parseDate(p?.premiereDate)
    val death = parseDate(p?.endDate)

    LazyVerticalGrid(
        columns = GridCells.Adaptive(UiScale.posterWidth.dp),
        contentPadding = PaddingValues(start = 56.dp, end = 56.dp, top = 40.dp, bottom = 60.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                // Photo
                Box(
                    Modifier
                        .size(width = 200.dp, height = 260.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Nova.SurfaceHi)
                        .border(1.dp, Nova.Stroke, RoundedCornerShape(22.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(name.take(1), style = NovaType.Display, color = Nova.TextDim)
                    AsyncImage(
                        model = api.image(personId, "Primary", p?.primaryTag, 500),
                        contentDescription = name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Spacer(Modifier.width(36.dp))
                Column(Modifier.widthIn(max = 760.dp)) {
                    Text("✦  CAST & CREW", style = NovaType.Caption, color = Nova.Cyan)
                    Spacer(Modifier.height(8.dp))
                    Text(p?.name ?: name, style = NovaType.Display.copy(fontSize = 44.sp), color = Nova.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (birth != null) {
                            val now = death ?: Calendar.getInstance()
                            GlassChip("Born ${prettyDate(birth)}")
                            if (death == null) GlassChip("Age ${ageBetween(birth, now)}", accent = Nova.Cyan)
                        }
                        if (death != null) GlassChip("Died ${prettyDate(death)}")
                        GlassChip("${list.size} title${if (list.size == 1) "" else "s"} on your server", accent = Nova.Violet)
                    }
                    p?.locations?.firstOrNull()?.let { place ->
                        Spacer(Modifier.height(10.dp))
                        Text("Born in $place", style = NovaType.Label, color = Nova.TextDim)
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        p?.overview?.takeIf { it.isNotBlank() } ?: "No biography available.",
                        style = NovaType.Body, color = Nova.TextDim, maxLines = 7, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (list.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionTitle("Movies & Shows")
            }
        }
        items(list.size, key = { list[it].id }) { i ->
            val item = list[i]
            PosterCard(
                item,
                onClick = { nav.push(Screen.Detail(item.id)) },
                width = UiScale.posterWidth,
                showTitle = true,
                focusRequester = if (i == 0) firstCard else null,
            )
        }
    }
}
