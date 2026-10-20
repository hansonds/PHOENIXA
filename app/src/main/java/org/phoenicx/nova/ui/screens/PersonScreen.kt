package org.phoenicx.nova.ui.screens

import androidx.compose.foundation.background
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.launch
import org.phoenicx.nova.ui.components.NovaButton
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
    val readMoreReq = remember { FocusRequester() }
    var showBio by remember { mutableStateOf(false) }
    var bioWasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(showBio) {
        if (showBio) bioWasOpen = true
        else if (bioWasOpen) { delay(60); runCatching { readMoreReq.requestFocus() } }
    }

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

    Box(Modifier.fillMaxSize()) {
    // Fixed info panel on the left (never scrolls away) + movies grid on the right
    Row(Modifier.fillMaxSize().padding(start = 56.dp, top = 36.dp)) {
        Column(Modifier.width(360.dp).padding(end = 28.dp)) {
            Box(
                Modifier
                    .size(width = 170.dp, height = 220.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Nova.SurfaceHi)
                    .border(1.dp, Nova.Stroke, RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(name.take(1), style = NovaType.Display, color = Nova.TextDim)
                AsyncImage(
                    model = api.image(personId, "Primary", p?.primaryTag, 500),
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(p?.name ?: name, style = NovaType.Title, color = Nova.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(10.dp))
            if (birth != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassChip("Born ${prettyDate(birth)}")
                    if (death == null) GlassChip("Age ${ageBetween(birth, Calendar.getInstance())}", accent = Nova.Cyan)
                }
                Spacer(Modifier.height(8.dp))
            }
            if (death != null) {
                GlassChip("Died ${prettyDate(death)}")
                Spacer(Modifier.height(8.dp))
            }
            p?.locations?.firstOrNull()?.let { place ->
                Text("Born in $place", style = NovaType.Label, color = Nova.TextDim, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(10.dp))
            }
            val bio = p?.overview?.takeIf { it.isNotBlank() }
            Text(
                bio ?: "No biography available.",
                style = NovaType.Label.copy(fontSize = 13.sp, lineHeight = 19.sp), color = Nova.TextDim,
                maxLines = 6, overflow = TextOverflow.Ellipsis,
            )
            if (bio != null && bio.length > 260) {
                Spacer(Modifier.height(12.dp))
                NovaButton("Read more", Icons.Outlined.MenuBook, focusRequester = readMoreReq) { showBio = true }
            }
        }

        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 56.dp)) {
                SectionTitle("Movies & Shows")
                Spacer(Modifier.weight(1f))
                GlassChip("${list.size} title${if (list.size == 1) "" else "s"} on your server", accent = Nova.Violet)
            }
            if (list.isEmpty()) {
                Spacer(Modifier.height(20.dp))
                Text("Nothing on your server with this person yet.", style = NovaType.Body, color = Nova.TextDim)
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(UiScale.posterWidth.dp),
                    contentPadding = PaddingValues(start = 4.dp, end = 56.dp, top = 16.dp, bottom = 60.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
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
        }
    }
    if (showBio) BioOverlay(p?.name ?: name, p?.overview ?: "", onClose = { showBio = false })
    }
}

/** Full biography, scrolled with up/down on the remote; Back closes it. */
@Composable
private fun BioOverlay(name: String, bio: String, onClose: () -> Unit) {
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val req = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(60); runCatching { req.requestFocus() } }
    BackHandler { onClose() }
    Box(Modifier.fillMaxSize().background(Color(0xE6090B12)), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .widthIn(max = 900.dp)
                .fillMaxWidth(0.8f)
                .fillMaxHeight(0.82f)
                .clip(RoundedCornerShape(26.dp))
                .background(Nova.Surface)
                .border(1.dp, Nova.Stroke, RoundedCornerShape(26.dp))
                .padding(36.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = NovaType.Title, color = Nova.Text, modifier = Modifier.weight(1f))
                Text("▲ ▼ scroll   ·   Back to close", style = NovaType.Label, color = Nova.TextFaint)
            }
            Spacer(Modifier.height(18.dp))
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .focusRequester(req)
                    .onPreviewKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (e.key) {
                            Key.DirectionDown -> { scope.launch { scroll.animateScrollBy(260f) }; true }
                            Key.DirectionUp -> { scope.launch { scroll.animateScrollBy(-260f) }; true }
                            Key.DirectionCenter, Key.Enter -> { onClose(); true }
                            else -> true
                        }
                    }
                    .focusable()
            ) {
                Text(
                    bio,
                    style = NovaType.Body.copy(fontSize = 17.sp, lineHeight = 28.sp),
                    color = Nova.Text,
                    modifier = Modifier.verticalScroll(scroll),
                )
            }
        }
    }
}
