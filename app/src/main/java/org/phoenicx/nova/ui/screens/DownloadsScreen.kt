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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import org.phoenicx.nova.download.DownloadRec
import org.phoenicx.nova.download.Downloads
import org.phoenicx.nova.download.formatSize
import org.phoenicx.nova.player.PlayerActivity
import org.phoenicx.nova.ui.Navigator
import org.phoenicx.nova.ui.Screen
import org.phoenicx.nova.ui.components.NovaButton
import org.phoenicx.nova.ui.components.OptionSheet
import org.phoenicx.nova.ui.components.ProgressBar
import org.phoenicx.nova.ui.components.SheetOption
import org.phoenicx.nova.ui.components.TopNav
import org.phoenicx.nova.ui.theme.Lay
import org.phoenicx.nova.ui.theme.Nova
import org.phoenicx.nova.ui.theme.NovaType
import java.io.File

/** Settings → Downloads: what's saved on this phone for offline viewing */
@Composable
fun DownloadsScreen(nav: Navigator) {
    val context = LocalContext.current
    // Reading the list makes this page follow the progress live
    @Suppress("UNUSED_VARIABLE") val all by Downloads.all
    val list = Downloads.mine()
    var confirm by remember { mutableStateOf<DownloadRec?>(null) }

    Column(Modifier.fillMaxSize()) {
        TopNav(nav, Screen.Settings)
        PageHeader(
            "Downloads",
            "${list.count { it.done }} ready · ${formatSize(Downloads.usedBytes())} used · ${formatSize(Downloads.freeBytes())} free on this device",
        ) {}
        LazyColumn(
            contentPadding = PaddingValues(start = Lay.side, end = Lay.side, top = 8.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Nova.Cyan.copy(alpha = 0.08f))
                        .border(1.dp, Nova.Cyan.copy(alpha = 0.25f), RoundedCornerShape(14.dp)).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Lock, null, tint = Nova.Cyan, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Downloads are kept privately inside PHOENIXA and play only here, also without internet.",
                        style = NovaType.Label.copy(fontSize = 13.sp), color = Nova.TextDim,
                    )
                }
            }
            if (list.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.DownloadForOffline, null, tint = Nova.TextFaint, modifier = Modifier.size(56.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("Nothing downloaded yet", style = NovaType.Section, color = Nova.Text)
                        Spacer(Modifier.height(4.dp))
                        Text("Open a movie or episode and tap Download.", style = NovaType.Body, color = Nova.TextDim)
                    }
                }
            }
            items(list, key = { it.id }) { r ->
                DownloadRow(
                    r,
                    onPlay = { PlayerActivity.startLocal(context, r.id) },
                    onRetry = { Downloads.retry(r.id) },
                    onDelete = { confirm = r },
                )
            }
        }
    }

    confirm?.let { r ->
        OptionSheet(
            title = if (r.done) "Delete ${r.name}?" else "Stop downloading ${r.name}?",
            options = listOf(
                SheetOption(if (r.done) "Delete download" else "Stop and delete", if (r.doneBytes > 0) "Frees ${formatSize(r.doneBytes)}" else "", false),
                SheetOption("Cancel", "", false),
            ),
            onSelect = { i -> if (i == 0) Downloads.delete(r.id); confirm = null },
            onDismiss = { confirm = null },
        )
    }
}

@Composable
private fun DownloadRow(r: DownloadRec, onPlay: () -> Unit, onRetry: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0x14FFFFFF))
            .border(1.dp, Nova.Stroke, RoundedCornerShape(16.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(width = 62.dp, height = 92.dp).clip(RoundedCornerShape(10.dp)).background(Nova.BgDeep)) {
            val poster = File(Downloads.dir(r.id), "poster.jpg")
            if (poster.exists()) AsyncImage(poster, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            if (r.type == "Episode" && r.seriesName != null) {
                Text(r.seriesName, style = NovaType.Label.copy(fontSize = 12.sp), color = Nova.TextFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(if (r.season != null && r.episode != null) "S${r.season}:E${r.episode}" else null, r.name).joinToString(" · "),
                    style = NovaType.Button, color = Nova.Text, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            } else {
                Text(r.name + (r.year?.let { " ($it)" } ?: ""), style = NovaType.Button, color = Nova.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(4.dp))
            val status = when (r.state) {
                "done" -> formatSize(r.doneBytes) + (if (r.resumeMs > 0 && r.runTimeTicks > 0)
                    " · ${((r.resumeMs * 10_000 * 100) / r.runTimeTicks).coerceIn(0, 99)}% watched" else " · Ready offline")
                "downloading" -> "Downloading ${r.percent}% · ${formatSize(r.doneBytes)} of ${formatSize(r.totalBytes)}"
                "queued" -> r.error ?: (if (r.doneBytes > 0) "Paused at ${r.percent}% · waiting for internet" else "Waiting…")
                else -> "Failed: " + (r.error ?: "try again")
            }
            Text(status, style = NovaType.Label.copy(fontSize = 12.sp),
                color = if (r.state == "failed") Nova.Gold else Nova.TextDim, maxLines = 2)
            if (!r.done) {
                Spacer(Modifier.height(8.dp))
                ProgressBar(r.percent / 100f, Modifier.fillMaxWidth())
            }
        }
        Spacer(Modifier.width(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            when {
                r.done -> NovaButton("", Icons.Filled.PlayArrow, primary = true) { onPlay() }
                r.state == "failed" -> NovaButton("", Icons.Outlined.Refresh) { onRetry() }
            }
            NovaButton("", Icons.Outlined.DeleteOutline) { onDelete() }
        }
    }
}
