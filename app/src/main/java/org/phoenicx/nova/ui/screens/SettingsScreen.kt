package org.phoenicx.nova.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import org.phoenicx.nova.NovaApp
import org.phoenicx.nova.ui.Navigator
import org.phoenicx.nova.ui.Screen
import org.phoenicx.nova.ui.components.FocusSurface
import org.phoenicx.nova.ui.components.NovaButton
import org.phoenicx.nova.ui.components.TopNav
import org.phoenicx.nova.ui.theme.Lay
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
    var showThemes by remember { mutableStateOf(false) }
    val themeRowRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    var refocusTheme by remember { mutableIntStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(refocusTheme) {
        if (refocusTheme > 0) { kotlinx.coroutines.delay(80); runCatching { themeRowRequester.requestFocus() } }
    }
    val app = NovaApp.instance
    var crash by remember { mutableStateOf(runCatching { app.crashFile().takeIf { it.exists() }?.readText() }.getOrNull()) }
    // Was this copy installed from Google Play?
    val fromPlayStore = remember {
        runCatching {
            val installer = if (android.os.Build.VERSION.SDK_INT >= 30)
                app.packageManager.getInstallSourceInfo(app.packageName).installingPackageName
            else @Suppress("DEPRECATION") app.packageManager.getInstallerPackageName(app.packageName)
            installer == "com.android.vending"
        }.getOrDefault(false)
    }
    val version = remember { runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull() ?: "" }

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        TopNav(nav, Screen.Settings)
        Column(
            Modifier.padding(horizontal = Lay.side).widthIn(max = 760.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Settings", style = NovaType.Title, color = Nova.Text)

            // Who's signed in, with a quick way to switch
            ProfileCard(prefs.userName ?: "—", prefs.server) { nav.push(Screen.Profiles) }

            SectionLabel("Playback")
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
            SettingRow("Auto-play next episode", "Start the next episode when one ends", if (autoNext) "On" else "Off") {
                autoNext = !autoNext; prefs.autoPlayNext = autoNext
            }

            SectionLabel("Appearance")
            SettingRow(
                "Theme",
                "Change the colours of the whole app",
                org.phoenicx.nova.ui.theme.NovaThemes.current.name.substringBefore(" (default)"),
                requester = themeRowRequester,
            ) { showThemes = true }
            SettingRow(
                "Card size",
                "Make posters and thumbnails smaller or bigger",
                UiScale.labels[UiScale.cardSizeIndex],
            ) { UiScale.set((UiScale.cardSizeIndex + 1) % 3) }
            SettingRow("Rotate featured carousel", "Cycle the home banner automatically", if (heroRotate) "On" else "Off") {
                heroRotate = !heroRotate; prefs.heroAutoRotate = heroRotate
            }

            // Phones and tablets only
            if (!app.isTv) {
                var notify by remember { mutableStateOf(prefs.notifyNewTitles) }
                SectionLabel("Notifications")
                SettingRow(
                    "New movies & shows",
                    if (notify && !org.phoenicx.nova.notify.NewTitles.canNotify(app))
                        "Notifications are turned off for PHOENIXA in your phone's settings. Tap to open them."
                    else "Get a notification when something new is added to your server",
                    if (notify) "On" else "Off",
                ) {
                    if (notify && !org.phoenicx.nova.notify.NewTitles.canNotify(app)) {
                        // Allowed in PHOENIXA but blocked by the phone: open the phone's notification settings
                        runCatching {
                            app.startActivity(
                                android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, app.packageName)
                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    } else {
                        notify = !notify
                        prefs.notifyNewTitles = notify
                        org.phoenicx.nova.notify.NewTitles.schedule(app)
                    }
                }
            }

            // Only for copies installed from Google Play (sideloaded and Amazon copies don't show it)
            if (fromPlayStore) {
                SectionLabel("Enjoying PHOENIXA?")
                SettingRow("Rate on Google Play", "A quick rating helps other people find the app", "★ Rate") {
                    val ctx = app
                    val pkg = app.packageName
                    runCatching {
                        ctx.startActivity(
                            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=$pkg"))
                                .setPackage("com.android.vending")
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }.onFailure {
                        runCatching {
                            ctx.startActivity(
                                android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://play.google.com/store/apps/details?id=$pkg"))
                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }
                }
            }

            SectionLabel("Account")
            NovaButton("Sign out", Icons.Outlined.Logout) { onSignOut() }

            // About
            Spacer(Modifier.height(18.dp))
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("PHOENIXA $version", style = NovaType.Label, color = Nova.TextDim)
                Spacer(Modifier.height(4.dp))
                Text("Made with ❤️ by Hanson", style = NovaType.Label, color = Nova.TextFaint)
                Spacer(Modifier.height(4.dp))
                // Phones open the website; TVs usually have no browser, so it's just shown there
                Text(
                    "phoenicx.org", style = NovaType.Label, color = Nova.Cyan,
                    modifier = if (app.isTv) Modifier else Modifier.clickable {
                        runCatching {
                            app.startActivity(
                                android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://phoenicx.org"))
                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }.padding(6.dp),
                )
            }
            crash?.let { report ->
                Spacer(Modifier.height(10.dp))
                Text("LAST CRASH REPORT", style = NovaType.Caption, color = Nova.Rose)
                Text(report.lines().take(14).joinToString("\n"), style = NovaType.Label, color = Nova.TextDim)
                NovaButton("Clear crash report") { runCatching { app.crashFile().delete() }; crash = null }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
    if (showThemes) {
        val themes = org.phoenicx.nova.ui.theme.NovaThemes
        org.phoenicx.nova.ui.components.OptionSheet(
            title = "Theme",
            options = themes.all.mapIndexed { i, t ->
                org.phoenicx.nova.ui.components.SheetOption(t.name, if (i == 1) "Default" else if (i == 0) "Original cyan & violet look" else "", i == themes.index)
            },
            onSelect = { i -> themes.set(i); showThemes = false; refocusTheme++ },
            onDismiss = { showThemes = false; refocusTheme++ },
        )
    }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(), style = NovaType.Caption, color = Nova.TextFaint,
        modifier = Modifier.padding(start = 6.dp, top = 14.dp, bottom = 2.dp),
    )
}

/** Your picture, name and server, with a Switch button */
@Composable
private fun ProfileCard(name: String, server: String, onSwitch: () -> Unit) {
    val api = NovaApp.instance.api
    FocusSurface(onClick = onSwitch, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), focusedScale = 1.02f) { focused ->
        Row(
            Modifier.fillMaxWidth()
                .background(
                    androidx.compose.ui.graphics.Brush.horizontalGradient(
                        listOf(Nova.Cyan.copy(alpha = if (focused) 0.30f else 0.16f), Color(0x14FFFFFF))
                    )
                )
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(56.dp).clip(CircleShape).background(Nova.AccentBrush), contentAlignment = Alignment.Center) {
                Text(name.take(1).uppercase(), style = NovaType.Title, color = Nova.OnFocus)
                coil.compose.AsyncImage(api.userImage(), null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("SIGNED IN AS", style = NovaType.Caption, color = Nova.TextFaint)
                Text(name, style = NovaType.Section, color = Nova.Text, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(server.substringAfter("://"), style = NovaType.Label, color = Nova.TextDim, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier.clip(RoundedCornerShape(18.dp)).background(if (focused) Nova.Focus else Color(0x22FFFFFF))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text("Switch", style = NovaType.Button, color = if (focused) Nova.OnFocus else Nova.Text)
            }
        }
    }
}

/** One setting: title and value on the first line, the explanation below (lines up on every screen size) */
@Composable
private fun SettingRow(
    title: String, sub: String, value: String,
    requester: androidx.compose.ui.focus.FocusRequester? = null,
    onClick: () -> Unit,
) {
    FocusSurface(onClick = onClick, modifier = Modifier.fillMaxWidth(), focusRequester = requester, shape = RoundedCornerShape(18.dp), focusedScale = 1.02f) { focused ->
        Column(
            Modifier.fillMaxWidth()
                .background(if (focused) Color(0x26FFFFFF) else Color(0x1AFFFFFF))
                .padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = NovaType.Section, color = Nova.Text, modifier = Modifier.weight(1f), maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Spacer(Modifier.width(12.dp))
                Box(
                    Modifier.clip(RoundedCornerShape(14.dp)).background(Nova.Cyan.copy(alpha = 0.16f))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(value, style = NovaType.Button, color = Nova.Cyan, maxLines = 1)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(sub, style = NovaType.Label, color = Nova.TextDim)
        }
    }
}
