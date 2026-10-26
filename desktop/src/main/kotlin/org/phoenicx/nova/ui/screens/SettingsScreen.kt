package org.phoenicx.nova.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.phoenicx.nova.App
import org.phoenicx.nova.Navigator
import org.phoenicx.nova.player.Player
import org.phoenicx.nova.ui.Avatar
import org.phoenicx.nova.ui.Chip
import org.phoenicx.nova.ui.Nova
import org.phoenicx.nova.ui.NovaButton
import org.phoenicx.nova.ui.NovaPalette
import org.phoenicx.nova.ui.NovaThemes
import org.phoenicx.nova.ui.NovaType
import org.phoenicx.nova.ui.SectionTitle
import org.phoenicx.nova.ui.TopBarHeight
import org.phoenicx.nova.ui.UiScale
import org.phoenicx.nova.ui.clickableHand
import org.phoenicx.nova.ui.rememberHover

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(nav: Navigator) {
    val prefs = App.prefs
    var directOnly by remember { mutableStateOf(prefs.directPlayOnly) }
    var fullscreen by remember { mutableStateOf(prefs.playerFullscreen) }
    var autoNext by remember { mutableStateOf(prefs.autoPlayNext) }
    var rotate by remember { mutableStateOf(prefs.heroAutoRotate) }
    val bitrates = listOf(0, 120, 60, 40, 20, 10, 4)
    var bitrate by remember { mutableIntStateOf(prefs.maxBitrateMbps) }
    val playerPath = remember { Player.playerInfo() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 64.dp, end = 64.dp, top = TopBarHeight + 16.dp, bottom = 48.dp)
    ) {
        Column(Modifier.widthIn(max = 980.dp)) {
            Text("Settings", style = NovaType.Title, color = Nova.Text)
            Spacer(Modifier.height(20.dp))

            // Account
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(56)
                    Spacer(Modifier.width(18.dp))
                    Column(Modifier.weight(1f)) {
                        Text("SIGNED IN AS", style = NovaType.Caption, color = Nova.TextFaint)
                        Text(prefs.userName ?: "—", style = NovaType.Section, color = Nova.Text)
                        Text(prefs.server, style = NovaType.Label, color = Nova.TextDim)
                    }
                    NovaButton("Sign out", Icons.Outlined.Logout) { App.signOut() }
                }
            }

            Spacer(Modifier.height(30.dp))
            SectionTitle("Theme")
            Spacer(Modifier.height(14.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                NovaThemes.all.forEachIndexed { i, t ->
                    ThemeSwatch(t, i == NovaThemes.index, i == 0) { NovaThemes.set(i) }
                }
            }

            Spacer(Modifier.height(30.dp))
            SectionTitle("Appearance")
            Spacer(Modifier.height(10.dp))
            Card {
                SettingLine("Card size", "Make posters and thumbnails smaller or bigger") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        UiScale.labels.forEachIndexed { i, l -> Chip(l, i == UiScale.cardSizeIndex) { UiScale.set(i) } }
                    }
                }
                SettingLine("Rotate featured banner", "Change the big Home banner automatically") {
                    Toggle(rotate) { rotate = it; prefs.heroAutoRotate = it }
                }
            }

            Spacer(Modifier.height(30.dp))
            SectionTitle("Playback")
            Spacer(Modifier.height(10.dp))
            Card {
                SettingLine("Open player in full screen", "Press F or double-click in the player to switch") {
                    Toggle(fullscreen) { fullscreen = it; prefs.playerFullscreen = it }
                }
                SettingLine("Auto-play next episode", "Start the next episode when one ends") {
                    Toggle(autoNext) { autoNext = it; prefs.autoPlayNext = it }
                }
                SettingLine("Direct play only", "Always play the original file. Turn off to let the server transcode if a file won't play") {
                    Toggle(directOnly) { directOnly = it; prefs.directPlayOnly = it }
                }
                SettingLine("Streaming quality", "Lower this if playback buffers on a slow connection") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.widthIn(max = 420.dp)) {
                        bitrates.forEach { b ->
                            Chip(if (b == 0) "Max" else "$b Mbps", b == bitrate) { bitrate = b; prefs.maxBitrateMbps = b }
                        }
                    }
                }
            }

            Spacer(Modifier.height(30.dp))
            SectionTitle("About")
            Spacer(Modifier.height(10.dp))
            Card {
                SettingLine("Version", "PHOENIXA for Windows · made by Hanson") {
                    Text(App.version, style = NovaType.Button, color = Nova.Cyan)
                }
                SettingLine("Video player", playerPath) {
                    if (playerPath == "Not found") Text("Missing", style = NovaType.Button, color = Nova.Rose)
                    else Icon(Icons.Filled.CheckCircle, null, tint = Nova.Green)
                }
                SettingLine("Settings folder", prefs.folder.absolutePath) {
                    NovaButton("Open", Icons.Outlined.FolderOpen) {
                        runCatching { java.awt.Desktop.getDesktop().open(prefs.folder) }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Videos play in mpv, a free and open-source player bundled with PHOENIXA. " +
                    "Player keys: Space pause · ←/→ seek · F full screen · J subtitles · # audio · Q close the player.",
                style = NovaType.Label, color = Nova.TextFaint,
            )
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color(0x12FFFFFF), RoundedCornerShape(20.dp))
            .border(1.dp, Nova.Stroke, RoundedCornerShape(20.dp))
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) { content() }
}

@Composable
private fun SettingLine(title: String, sub: String, control: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 20.dp)) {
            Text(title, style = NovaType.Section, color = Nova.Text)
            Text(sub, style = NovaType.Label, color = Nova.TextDim)
        }
        control()
    }
}

@Composable
private fun Toggle(value: Boolean, onChange: (Boolean) -> Unit) {
    Switch(
        checked = value, onCheckedChange = onChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Nova.OnFocus,
            checkedTrackColor = Nova.Cyan,
            uncheckedThumbColor = Nova.TextDim,
            uncheckedTrackColor = Color(0x26FFFFFF),
            uncheckedBorderColor = Nova.Stroke,
        ),
    )
}

@Composable
private fun ThemeSwatch(t: NovaPalette, selected: Boolean, isDefault: Boolean, onClick: () -> Unit) {
    val (src, hovered) = rememberHover()
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .width(168.dp)
            .clip(shape)
            .background(t.bg, shape)
            .border(if (selected) 2.dp else 1.dp, if (selected) t.accent else if (hovered) Color(0x55FFFFFF) else Nova.Stroke, shape)
            .hoverable(src)
            .clickableHand(onClick)
            .padding(14.dp)
    ) {
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Brush.horizontalGradient(listOf(t.accent, t.accent2))))
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(26.dp).clip(CircleShape).background(t.focus))
            Spacer(Modifier.width(6.dp))
            Box(Modifier.size(26.dp).clip(CircleShape).background(t.surfaceHi).border(2.dp, t.accent, CircleShape))
            Spacer(Modifier.weight(1f))
            if (selected) Icon(Icons.Filled.CheckCircle, "Selected", tint = t.accent, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(t.name.removeSuffix(" (default)"), style = NovaType.Button, color = t.text, maxLines = 1)
        Text(if (isDefault) "Default" else " ", style = NovaType.Label, color = t.textDim)
    }
}
