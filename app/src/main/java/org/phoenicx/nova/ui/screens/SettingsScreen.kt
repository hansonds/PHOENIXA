package org.phoenicx.nova.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.phoenicx.nova.NovaApp
import org.phoenicx.nova.ui.Navigator
import org.phoenicx.nova.ui.Screen
import org.phoenicx.nova.ui.components.FocusSurface
import org.phoenicx.nova.ui.components.NovaButton
import org.phoenicx.nova.ui.components.TopNav
import org.phoenicx.nova.ui.theme.Nova
import org.phoenicx.nova.ui.theme.NovaType
import org.phoenicx.nova.ui.theme.UiScale

@Composable
fun SettingsScreen(nav: Navigator, onSignOut: () -> Unit) {
    val prefs = NovaApp.instance.prefs
    val bitrates = listOf(0, 120, 60, 40, 20, 10, 4)
    var bitrateIdx by remember { mutableIntStateOf(bitrates.indexOf(prefs.maxBitrateMbps).coerceAtLeast(0)) }
    var autoNext by remember { mutableStateOf(prefs.autoPlayNext) }
    var heroRotate by remember { mutableStateOf(prefs.heroAutoRotate) }
    var directOnly by remember { mutableStateOf(prefs.directPlayOnly) }
    val app = NovaApp.instance
    var crash by remember { mutableStateOf(runCatching { app.crashFile().takeIf { it.exists() }?.readText() }.getOrNull()) }
    val version = remember { runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull() ?: "" }

    Column(Modifier.fillMaxSize()) {
        TopNav(nav, Screen.Settings)
        Column(
            Modifier.padding(horizontal = 56.dp).widthIn(max = 760.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Settings", style = NovaType.Title, color = Nova.Text)
            Spacer(Modifier.height(4.dp))

            InfoCard("Signed in as", prefs.userName ?: "—", prefs.server)

            SettingRow(
                "Direct play only",
                "Always play the original file. Turn off to let the server transcode if a file won't play",
                if (directOnly) "On" else "Off",
            ) { directOnly = !directOnly; prefs.directPlayOnly = directOnly }
            SettingRow(
                "Streaming quality",
                "Lower this if playback buffers on a slow connection",
                if (bitrates[bitrateIdx] == 0) "Maximum" else "${bitrates[bitrateIdx]} Mbps",
            ) {
                bitrateIdx = (bitrateIdx + 1) % bitrates.size
                prefs.maxBitrateMbps = bitrates[bitrateIdx]
            }
            SettingRow(
                "Card size",
                "Make posters and thumbnails smaller or bigger",
                UiScale.labels[UiScale.cardSizeIndex],
            ) { UiScale.set((UiScale.cardSizeIndex + 1) % 3) }
            SettingRow("Auto-play next episode", "Start the next episode when one ends", if (autoNext) "On" else "Off") {
                autoNext = !autoNext; prefs.autoPlayNext = autoNext
            }
            SettingRow("Rotate featured carousel", "Cycle the home banner automatically", if (heroRotate) "On" else "Off") {
                heroRotate = !heroRotate; prefs.heroAutoRotate = heroRotate
            }
            Spacer(Modifier.height(8.dp))
            NovaButton("Sign out", Icons.Outlined.Logout) { onSignOut() }
            Spacer(Modifier.height(10.dp))
            Text("PHOENIXA $version · made by Hanson", style = NovaType.Label, color = Nova.TextFaint)
            crash?.let { report ->
                Spacer(Modifier.height(10.dp))
                Text("LAST CRASH REPORT", style = NovaType.Caption, color = Nova.Rose)
                Text(report.lines().take(14).joinToString("\n"), style = NovaType.Label, color = Nova.TextDim)
                NovaButton("Clear crash report") { runCatching { app.crashFile().delete() }; crash = null }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun InfoCard(title: String, value: String, sub: String) {
    Row(
        Modifier.fillMaxWidth()
            .background(Color(0x1FFFFFFF), RoundedCornerShape(18.dp))
            .border(1.dp, Nova.Stroke, RoundedCornerShape(18.dp))
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp).background(Nova.AccentBrush, CircleShape), contentAlignment = Alignment.Center) {
            Text(value.take(1).uppercase(), style = NovaType.Title, color = Color(0xFF12152A))
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title.uppercase(), style = NovaType.Caption, color = Nova.TextFaint)
            Text(value, style = NovaType.Section, color = Nova.Text)
            Text(sub, style = NovaType.Label, color = Nova.TextDim)
        }
    }
}

@Composable
private fun SettingRow(title: String, sub: String, value: String, onClick: () -> Unit) {
    FocusSurface(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), focusedScale = 1.02f) { focused ->
        Row(
            Modifier.fillMaxWidth()
                .background(if (focused) Color(0x336CE5F5) else Color(0x1AFFFFFF))
                .padding(horizontal = 22.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = NovaType.Section, color = Nova.Text)
                Text(sub, style = NovaType.Label, color = Nova.TextDim)
            }
            Text(value, style = NovaType.Button, color = Nova.Cyan)
        }
    }
}
