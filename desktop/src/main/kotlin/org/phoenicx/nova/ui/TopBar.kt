package org.phoenicx.nova.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.FavoriteBorder
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import org.phoenicx.nova.App
import org.phoenicx.nova.Navigator
import org.phoenicx.nova.Screen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Height reserved at the top of pages for the navigation bar */
val TopBarHeight = 76.dp

@Composable
fun TopBar(nav: Navigator) {
    val cur = nav.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(TopBarHeight)
            .background(Brush.verticalGradient(listOf(Nova.Bg.copy(alpha = 0.92f), Nova.Bg.copy(alpha = 0.55f), Color.Transparent)))
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painterResource("logo_stacked.png"), "PHOENIXA",
            modifier = Modifier.size(54.dp).clip(CircleShape).clickableHand { nav.go(Screen.Home) },
        )
        Spacer(Modifier.width(10.dp))
        if (nav.canGoBack) {
            IconPill(Icons.Outlined.ArrowBack, "Back", false) { nav.back() }
            Spacer(Modifier.width(8.dp))
        }
        Row(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(30.dp))
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavPill("Home", cur == Screen.Home) { nav.go(Screen.Home) }
            IconPill(Icons.Outlined.FavoriteBorder, "Favorites", cur == Screen.Favorites) { nav.go(Screen.Favorites) }
            App.libraries.forEach { lib ->
                NavPill(lib.name, cur is Screen.Lib && cur.library.id == lib.id) { nav.go(Screen.Lib(lib)) }
            }
        }
        Spacer(Modifier.width(12.dp))
        IconPill(Icons.Outlined.Search, "Search", cur == Screen.Search) { nav.go(Screen.Search) }
        Spacer(Modifier.width(6.dp))
        IconPill(Icons.Outlined.Settings, "Settings", cur == Screen.Settings) { nav.go(Screen.Settings) }
        Spacer(Modifier.width(14.dp))
        Clock()
        Spacer(Modifier.width(14.dp))
        Avatar(36)
    }
}

@Composable
private fun NavPill(text: String, selected: Boolean, onClick: () -> Unit) {
    val (src, hovered) = rememberHover()
    val shape = RoundedCornerShape(22.dp)
    Box(
        Modifier
            .widthIn(max = 220.dp)
            .clip(shape)
            .background(
                when {
                    selected -> Nova.Focus
                    hovered -> Color(0x26FFFFFF)
                    else -> Color.Transparent
                }, shape
            )
            .hoverable(src)
            .clickableHand(onClick)
            .padding(horizontal = 18.dp, vertical = 9.dp)
    ) {
        Text(
            text, style = NovaType.Button, maxLines = 1,
            color = if (selected) Nova.OnFocus else if (hovered) Nova.Text else Nova.TextDim,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
        )
    }
}

@Composable
private fun IconPill(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val (src, hovered) = rememberHover()
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(
                when {
                    selected -> Nova.Focus
                    hovered -> Color(0x26FFFFFF)
                    else -> Color(0x10FFFFFF)
                }
            )
            .hoverable(src)
            .clickableHand(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = if (selected) Nova.OnFocus else Nova.Text, modifier = Modifier.size(21.dp))
    }
}

@Composable
private fun Clock() {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) { now = Date(); delay(15_000) }
    }
    Text(SimpleDateFormat("h:mm a", Locale.getDefault()).format(now), style = NovaType.Button, color = Nova.TextDim, maxLines = 1)
}

@Composable
fun Avatar(sizeDp: Int) {
    Box(
        Modifier.size(sizeDp.dp).clip(CircleShape).background(Nova.AccentBrush).border(1.dp, Nova.Stroke, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text((App.prefs.userName ?: "?").take(1).uppercase(), style = NovaType.Button, color = Nova.OnFocus)
        AsyncImage(model = App.api.userImage(), contentDescription = null, modifier = Modifier.size(sizeDp.dp).clip(CircleShape),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop)
    }
}
