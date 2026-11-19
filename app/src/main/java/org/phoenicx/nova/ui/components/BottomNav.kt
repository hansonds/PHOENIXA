package org.phoenicx.nova.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import org.phoenicx.nova.NovaApp
import org.phoenicx.nova.ui.Navigator
import org.phoenicx.nova.ui.Screen
import org.phoenicx.nova.ui.theme.Nova
import org.phoenicx.nova.ui.theme.NovaType

/** Phones (portrait): Home · Library · Search & Add · Search · Profile at the bottom, easy to reach with a thumb */
@Composable
fun BottomNav(nav: Navigator) {
    val current = nav.current
    val torvia = nav.torvia.value
    val inLibrary = current == Screen.Libraries || current is Screen.Lib || current == Screen.Favorites
    val inAdd = current == Screen.Torvia || current is Screen.TorviaTitle

    Row(
        Modifier
            .fillMaxWidth()
            .background(Nova.BgDeep.copy(alpha = 0.97f))
            .windowInsetsPadding(WindowInsets.navigationBars)
            .height(64.dp)
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tab("Home", Icons.Filled.Home, Icons.Outlined.Home, current == Screen.Home) { nav.tab(Screen.Home) }
        // Every library (and Favorites) on one page
        Tab("Library", Icons.Filled.VideoLibrary, Icons.Outlined.VideoLibrary, inLibrary) {
            if (current != Screen.Libraries) nav.tab(Screen.Libraries)
        }
        // Only on servers with the Torvia plugin
        if (torvia != null) {
            Tab(torvia.buttonLabel, Icons.Filled.AddCircle, Icons.Outlined.AddCircleOutline, inAdd) { if (current != Screen.Torvia) nav.tab(Screen.Torvia) }
        }
        Tab("Search", Icons.Filled.Search, Icons.Outlined.Search, current == Screen.Search) { if (current != Screen.Search) nav.tab(Screen.Search) }
        ProfileTab(current == Screen.Settings || current == Screen.Profiles) { if (current != Screen.Settings) nav.tab(Screen.Settings) }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Tab(
    label: String, on: ImageVector, off: ImageVector, selected: Boolean, onClick: () -> Unit,
) {
    val tint by animateColorAsState(if (selected) Nova.Cyan else Nova.TextDim, label = "tab")
    Column(
        Modifier.weight(1f).fillMaxSize()
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.width(56.dp).height(30.dp).clip(RoundedCornerShape(15.dp))
                .background(if (selected) Nova.Cyan.copy(alpha = 0.16f) else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (selected) on else off, label, tint = tint, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(3.dp))
        Text(label, style = NovaType.Label.copy(fontSize = if (label.length > 8) 10.sp else 11.sp), color = tint, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.ProfileTab(selected: Boolean, onClick: () -> Unit) {
    val prefs = NovaApp.instance.prefs
    val tint by animateColorAsState(if (selected) Nova.Cyan else Nova.TextDim, label = "tab")
    Column(
        Modifier.weight(1f).fillMaxSize()
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.height(30.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(26.dp).clip(CircleShape).background(Nova.AccentBrush)
                    .border(if (selected) 2.dp else 0.dp, Nova.Cyan, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text((prefs.userName ?: "?").take(1).uppercase(), style = NovaType.Label.copy(fontSize = 12.sp), color = Nova.OnFocus)
                AsyncImage(NovaApp.instance.api.userImage(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Spacer(Modifier.height(3.dp))
        Text("Profile", style = NovaType.Label.copy(fontSize = 11.sp), color = tint, maxLines = 1)
    }
}
