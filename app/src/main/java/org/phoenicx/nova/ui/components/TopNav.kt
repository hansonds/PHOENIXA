package org.phoenicx.nova.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.filled.Favorite
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
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
    // Stacked PHOENIXA logo (phoenix above the name), transparent background
    androidx.compose.foundation.Image(
        painter = androidx.compose.ui.res.painterResource(org.phoenicx.nova.R.drawable.logo_stacked),
        contentDescription = "PHOENIXA",
        modifier = Modifier.size(64.dp),
    )
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
    val phone = org.phoenicx.nova.ui.theme.Lay.phone

    if (phone) {
        // Phone (portrait): no top bar. Everything is in the bottom bar; just keep clear of the camera cut-out.
        Spacer(modifier.fillMaxWidth().windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.displayCutout).height(12.dp))
        return
    }

    // TV / tablet: the same five places as the phone's bottom bar, always in the same spot (no scrolling)
    val inLibrary = selected == Screen.Libraries || selected is Screen.Lib || selected == Screen.Favorites
    val inProfile = selected == Screen.Settings || selected == Screen.Profiles
    Box(modifier.fillMaxWidth()) {
        // Small clock in the top-right corner, above the bar
        Box(Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 52.dp)) { Clock() }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 44.dp, end = 48.dp, top = 18.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NovaWordmark()
            Spacer(Modifier.width(22.dp))
            Row(
                Modifier
                    .clip(RoundedCornerShape(26.dp))
                    .background(Nova.Surface.copy(alpha = 0.36f))
                    .border(1.dp, Nova.Stroke, RoundedCornerShape(26.dp))
                    .padding(5.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NavPill("Home", selected == Screen.Home, if (selected == Screen.Home) selReq else null, Icons.Outlined.Home) {
                    if (selected != Screen.Home) nav.tab(Screen.Home)
                }
                NavPill("Library", inLibrary, if (inLibrary) selReq else null, Icons.Outlined.VideoLibrary) {
                    if (selected != Screen.Libraries) nav.tab(Screen.Libraries)
                }
                NavPill("Search", selected == Screen.Search, if (selected == Screen.Search) selReq else null, Icons.Outlined.Search) {
                    if (selected != Screen.Search) nav.tab(Screen.Search)
                }
                // Only on servers with the Torvia plugin
                if (nav.torvia.value != null) {
                    val isSel = selected == Screen.Torvia || selected is Screen.TorviaTitle
                    NavPill("Add", isSel, if (isSel) selReq else null, Icons.Outlined.AddCircleOutline) {
                        if (selected != Screen.Torvia) nav.tab(Screen.Torvia)
                    }
                }
                NavPill("Profile", inProfile, if (inProfile) selReq else null, null, avatar = true) {
                    if (selected != Screen.Settings) nav.tab(Screen.Settings)
                }
            }
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun NavPill(
    text: String, selected: Boolean, req: FocusRequester?, icon: ImageVector? = null,
    avatar: Boolean = false, onClick: () -> Unit,
) {
    FocusSurface(onClick = onClick, shape = RoundedCornerShape(22.dp), focusedScale = 1.04f, borderWidth = 0.dp, glow = false, focusRequester = req) { focused ->
        val bg = when {
            focused -> Brush.horizontalGradient(listOf(Nova.Focus, Nova.Focus))
            selected -> Brush.horizontalGradient(listOf(Nova.Cyan.copy(alpha = 0.25f), Nova.Violet.copy(alpha = 0.25f)))
            else -> Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
        }
        val tint = if (focused) Nova.OnFocus else if (selected) Nova.Text else Nova.TextDim
        Row(Modifier.height(40.dp).background(bg).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (avatar) {
                Box(Modifier.size(26.dp).clip(CircleShape).background(Nova.AccentBrush), contentAlignment = Alignment.Center) {
                    Text((org.phoenicx.nova.NovaApp.instance.prefs.userName ?: "?").take(1).uppercase(), style = NovaType.Label, color = Nova.OnFocus)
                    AsyncImage(api.userImage(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                Spacer(Modifier.width(8.dp))
            } else if (icon != null) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, style = NovaType.Button, maxLines = 1, softWrap = false, color = tint)
        }
    }
}

@Composable
private fun NavIcon(icon: ImageVector, selected: Boolean, req: FocusRequester?, onClick: () -> Unit) {
    FocusSurface(onClick = onClick, shape = CircleShape, focusedScale = 1.06f, borderWidth = 0.dp, glow = false, focusRequester = req) { focused ->
        Box(
            Modifier.size(40.dp).background(
                when {
                    focused -> Nova.Focus
                    selected -> Nova.Cyan.copy(alpha = 0.25f)
                    else -> Color.Transparent
                }
            ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = if (focused) Nova.OnFocus else Nova.Text, modifier = Modifier.size(22.dp))
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
    Text(
        fmt.format(now),
        style = NovaType.Label.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp),
        color = Nova.TextDim, maxLines = 1, softWrap = false,
    )
}

@Suppress("unused")
private fun Library.isVideo() = collectionType in setOf("movies", "tvshows")
