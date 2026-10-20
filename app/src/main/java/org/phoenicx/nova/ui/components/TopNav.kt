package org.phoenicx.nova.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import org.phoenicx.nova.data.Library
import org.phoenicx.nova.ui.Navigator
import org.phoenicx.nova.ui.Screen
import org.phoenicx.nova.ui.theme.Nova
import org.phoenicx.nova.ui.theme.NovaType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NovaWordmark() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("✦", color = Nova.Cyan, fontSize = 22.sp)
        Spacer(Modifier.width(8.dp))
        Text("NOVA", style = NovaType.Section.copy(fontWeight = FontWeight.Black, letterSpacing = 4.sp, fontSize = 18.sp), color = Nova.Text)
    }
}

@Composable
fun TopNav(nav: Navigator, selected: Screen, modifier: Modifier = Modifier) {
    val selReq = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (nav.focusNavOnEnter) {
            delay(90)
            runCatching { selReq.requestFocus() }
            nav.focusNavOnEnter = false
        }
    }
    val libs = nav.libraries.value.filter { it.collectionType in setOf("movies", "tvshows", "boxsets", null, "homevideos") }
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp, vertical = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NovaWordmark()
        Spacer(Modifier.width(32.dp))
        Row(
            Modifier
                .clip(RoundedCornerShape(26.dp))
                .background(Color(0x5C1A1F33))
                .border(1.dp, Nova.Stroke, RoundedCornerShape(26.dp))
                .padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavIcon(Icons.Outlined.Search, selected == Screen.Search, if (selected == Screen.Search) selReq else null) { if (selected != Screen.Search) nav.tab(Screen.Search) }
            NavPill("Home", selected == Screen.Home, if (selected == Screen.Home) selReq else null) { if (selected != Screen.Home) nav.tab(Screen.Home) }
            libs.take(5).forEach { lib ->
                val isSel = (selected as? Screen.Lib)?.library?.id == lib.id
                NavPill(lib.name, isSel, if (isSel) selReq else null) { if (!isSel) nav.tab(Screen.Lib(lib)) }
            }
        }
        Spacer(Modifier.weight(1f))
        Clock()
        Spacer(Modifier.width(18.dp))
        NavIcon(Icons.Outlined.Settings, selected == Screen.Settings, if (selected == Screen.Settings) selReq else null) { if (selected != Screen.Settings) nav.tab(Screen.Settings) }
        Spacer(Modifier.width(10.dp))
        Box(Modifier.size(38.dp).clip(CircleShape).background(Nova.AccentBrush), contentAlignment = Alignment.Center) {
            Text((org.phoenicx.nova.NovaApp.instance.prefs.userName ?: "?").take(1).uppercase(), style = NovaType.Button, color = Color(0xFF12152A))
            AsyncImage(api.userImage(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun NavPill(text: String, selected: Boolean, req: FocusRequester?, onClick: () -> Unit) {
    FocusSurface(onClick = onClick, shape = RoundedCornerShape(22.dp), focusedScale = 1.04f, borderWidth = 0.dp, glow = false, focusRequester = req) { focused ->
        val bg = when {
            focused -> Brush.horizontalGradient(listOf(Color(0xFFF4F7FF), Color(0xFFE2E8FF)))
            selected -> Brush.horizontalGradient(listOf(Color(0x406CE5F5), Color(0x40A58BFF)))
            else -> Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
        }
        Box(Modifier.height(40.dp).background(bg).padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
            Text(text, style = NovaType.Button, color = if (focused) Color(0xFF12152A) else if (selected) Nova.Text else Nova.TextDim)
        }
    }
}

@Composable
private fun NavIcon(icon: ImageVector, selected: Boolean, req: FocusRequester?, onClick: () -> Unit) {
    FocusSurface(onClick = onClick, shape = CircleShape, focusedScale = 1.06f, borderWidth = 0.dp, glow = false, focusRequester = req) { focused ->
        Box(
            Modifier.size(40.dp).background(
                when {
                    focused -> Color(0xFFF4F7FF)
                    selected -> Color(0x406CE5F5)
                    else -> Color.Transparent
                }
            ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = if (focused) Color(0xFF12152A) else Nova.Text, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
fun Clock() {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) { now = Date(); delay(15_000) }
    }
    val fmt = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    Text(fmt.format(now), style = NovaType.Section.copy(fontWeight = FontWeight.Medium), color = Nova.Text)
}

@Suppress("unused")
private fun Library.isVideo() = collectionType in setOf("movies", "tvshows")
