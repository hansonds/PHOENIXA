package org.phoenicx.nova.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.outlined.Forward10
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Replay10
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.SurroundSound
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import org.phoenicx.nova.App
import org.phoenicx.nova.player.MpvSession
import org.phoenicx.nova.player.MpvTrack
import org.phoenicx.nova.player.NowPlaying
import org.phoenicx.nova.player.Player
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class PlayerPanel { None, Subtitles, Audio, Speed }

/** UI state of the player that the window's keyboard handler also needs */
object PlayerUi {
    var overlay by mutableStateOf(true)
    var panel by mutableStateOf(PlayerPanel.None)
    var showInfo by mutableStateOf(false)
    var lastInput by mutableLongStateOf(System.currentTimeMillis())
    var seekFlash by mutableIntStateOf(0)

    fun poke() { lastInput = System.currentTimeMillis(); overlay = true }

    fun reset() { overlay = true; panel = PlayerPanel.None; showInfo = false; poke() }

    /** Keyboard shortcuts; returns true if the key was used */
    fun onKey(key: Key, s: MpvSession, toggleFullscreen: () -> Unit, isFullscreen: Boolean): Boolean {
        when (key) {
            Key.Spacebar, Key.K, Key.MediaPlayPause -> { s.togglePause(); poke() }
            Key.DirectionLeft, Key.J -> { s.seekRelative(-10.0); seekFlash++ }
            Key.DirectionRight, Key.L -> { s.seekRelative(10.0); seekFlash++ }
            Key.DirectionUp -> { s.changeVolume(s.volume + 5f); poke() }
            Key.DirectionDown -> { s.changeVolume(s.volume - 5f); poke() }
            Key.M -> { s.toggleMute(); poke() }
            Key.F, Key.F11 -> toggleFullscreen()
            Key.N, Key.MediaNext -> Player.playNext()
            Key.Escape, Key.Backspace -> when {
                panel != PlayerPanel.None -> panel = PlayerPanel.None
                showInfo -> showInfo = false
                isFullscreen && key == Key.Escape -> toggleFullscreen()
                else -> Player.close()
            }
            else -> return false
        }
        return true
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PlayerScreen(now: NowPlaying, isFullscreen: Boolean, toggleFullscreen: () -> Unit) {
    val s = now.session

    LaunchedEffect(now) { PlayerUi.reset() }
    // Read position etc. from mpv a few times a second
    LaunchedEffect(now) {
        while (true) {
            s.poll()
            delay(250)
        }
    }
    LaunchedEffect(s.eof) { if (s.eof) { delay(400); Player.onFinished() } }
    // Hide the controls after 3 s without mouse movement while playing
    LaunchedEffect(PlayerUi.overlay, PlayerUi.lastInput, s.paused, PlayerUi.panel, PlayerUi.showInfo) {
        if (PlayerUi.overlay && !s.paused && PlayerUi.panel == PlayerPanel.None && !PlayerUi.showInfo) {
            delay(3000)
            PlayerUi.overlay = false
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPointerEvent(PointerEventType.Move) { PlayerUi.poke() }
            .pointerHoverIcon(if (PlayerUi.overlay) PointerIcon.Default else hiddenPointer)
    ) {
        // The video picture
        Canvas(
            Modifier
                .fillMaxSize()
                .onSizeChanged { sz -> setTarget(s, sz) }
                .pointerInput(now) {
                    detectTapGestures(
                        onTap = {
                            if (PlayerUi.panel != PlayerPanel.None) PlayerUi.panel = PlayerPanel.None
                            else { s.togglePause(); PlayerUi.poke() }
                        },
                        onDoubleTap = { toggleFullscreen() },
                    )
                }
        ) {
            val img = s.frame
            if (img != null) {
                drawImage(
                    img,
                    dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                    filterQuality = FilterQuality.Medium,
                )
            }
        }

        if ((s.buffering || s.frame == null) && s.error == null) {
            CircularProgressIndicator(color = Nova.Cyan, strokeWidth = 4.dp, modifier = Modifier.align(Alignment.Center).size(64.dp))
        }

        if (!PlayerUi.overlay && PlayerUi.seekFlash > 0) SeekToast(s, PlayerUi.seekFlash)

        AnimatedVisibility(PlayerUi.overlay, enter = fadeIn(tween(200)), exit = fadeOut(tween(400))) {
            Overlay(now, isFullscreen, toggleFullscreen)
        }

        AnimatedVisibility(
            PlayerUi.panel != PlayerPanel.None,
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            when (PlayerUi.panel) {
                PlayerPanel.Speed -> SpeedPanel(s)
                PlayerPanel.Audio -> TrackPanel(s, "audio", "Audio")
                else -> TrackPanel(s, "sub", "Subtitles")
            }
        }

        s.error?.let { err ->
            Column(
                Modifier.align(Alignment.Center).background(Nova.Bg.copy(alpha = 0.92f), RoundedCornerShape(22.dp)).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Can't play this", style = NovaType.Title, color = Nova.Text)
                Spacer(Modifier.height(8.dp))
                Text(err, style = NovaType.Body, color = Nova.TextDim)
                Spacer(Modifier.height(20.dp))
                NovaButton("Go back", primary = true) { Player.close() }
            }
        }
    }
}

/** Renders at the window size, but not above 1920 px wide (keeps the CPU cool) */
private fun setTarget(s: MpvSession, sz: IntSize) {
    if (sz.width <= 0 || sz.height <= 0) return
    val scale = minOf(1f, 1920f / sz.width, 1080f / sz.height)
    s.targetW = (sz.width * scale).toInt()
    s.targetH = (sz.height * scale).toInt()
}

/** An invisible mouse pointer for when the controls are hidden */
private val hiddenPointer: PointerIcon by lazy { PointerIcon(blankCursor()) }

private fun blankCursor(): java.awt.Cursor = try {
    val img = java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB)
    java.awt.Toolkit.getDefaultToolkit().createCustomCursor(img, java.awt.Point(0, 0), "blank")
} catch (_: Exception) {
    java.awt.Cursor.getDefaultCursor()
}

@Composable
private fun Overlay(now: NowPlaying, isFullscreen: Boolean, toggleFullscreen: () -> Unit) {
    val s = now.session
    val item = now.item
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Nova.BgDeep.copy(alpha = 0.80f), 0.22f to Nova.BgDeep.copy(alpha = 0.15f),
                    0.6f to Nova.BgDeep.copy(alpha = 0.15f), 1f to Nova.BgDeep.copy(alpha = 0.95f),
                )
            )
        )
        if (PlayerUi.showInfo) Box(Modifier.fillMaxSize().background(Nova.BgDeep.copy(alpha = 0.6f)))

        // Top bar
        Row(
            Modifier.fillMaxWidth().padding(start = 40.dp, end = 40.dp, top = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundIcon(Icons.Outlined.ArrowBack, "Back") { Player.close() }
            Spacer(Modifier.width(8.dp))
            RoundIcon(Icons.Outlined.ClosedCaption, "Subtitles", active = PlayerUi.panel == PlayerPanel.Subtitles) { PlayerUi.panel = PlayerPanel.Subtitles }
            RoundIcon(Icons.Outlined.SurroundSound, "Audio", active = PlayerUi.panel == PlayerPanel.Audio) { PlayerUi.panel = PlayerPanel.Audio }
            RoundIcon(Icons.Outlined.Speed, "Speed", active = s.speed != 1f) { PlayerUi.panel = PlayerPanel.Speed }
            if (now.nextEpisode != null) RoundIcon(Icons.Filled.SkipNext, "Next episode") { Player.playNext() }
            RoundIcon(Icons.Outlined.Info, "Info", active = PlayerUi.showInfo) { PlayerUi.showInfo = !PlayerUi.showInfo }
            Spacer(Modifier.weight(1f))
            if (now.source.isTranscoding) Text("TRANSCODING", style = NovaType.Caption, color = Nova.Gold)
            RoundIcon(if (isFullscreen) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen, "Full screen") { toggleFullscreen() }
        }

        // Synopsis + cast
        if (PlayerUi.showInfo) {
            Row(Modifier.fillMaxWidth().padding(start = 48.dp, end = 48.dp, top = 110.dp)) {
                Column(Modifier.weight(1f).padding(end = 48.dp)) {
                    Text("SYNOPSIS", style = NovaType.Caption, color = Nova.TextFaint)
                    Spacer(Modifier.height(12.dp))
                    Text(item.overview, style = NovaType.Body.copy(fontSize = 16.sp, lineHeight = 26.sp), color = Nova.Text, maxLines = 8, overflow = TextOverflow.Ellipsis)
                }
                Column(Modifier.width(340.dp)) {
                    Text("CAST & CREW", style = NovaType.Caption, color = Nova.TextFaint)
                    Spacer(Modifier.height(10.dp))
                    LazyColumn(Modifier.height(300.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(item.people.take(20)) { p ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(44.dp).clip(CircleShape).background(Nova.SurfaceHi), contentAlignment = Alignment.Center) {
                                    Text(p.name.take(1), style = NovaType.Section, color = Nova.TextDim)
                                    AsyncImage(App.api.personImage(p, 120), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                }
                                Spacer(Modifier.width(14.dp))
                                Column {
                                    Text(p.name, style = NovaType.Button, color = Nova.Text)
                                    Text(p.role.ifEmpty { p.type }, style = NovaType.Label, color = Nova.TextFaint, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Bottom block
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 48.dp, vertical = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val logo = App.api.logo(item, 600)
                if (logo != null && !item.isEpisode) {
                    AsyncImage(logo, item.name, contentScale = ContentScale.Fit, alignment = Alignment.CenterStart,
                        modifier = Modifier.height(48.dp).width(240.dp))
                } else {
                    Text(if (item.isEpisode) (item.seriesName ?: item.name) else item.name, style = NovaType.Title, color = Nova.Text, maxLines = 1)
                }
                val bits = listOfNotNull(
                    item.year?.toString(),
                    item.communityRating?.let { "★ " + String.format("%.1f", it) },
                    item.episodeLabel,
                    if (item.isEpisode) item.name else null,
                )
                bits.forEach { b ->
                    Text("  •  ", style = NovaType.Section, color = Nova.TextFaint)
                    Text(b, style = NovaType.Section, color = if (b == item.episodeLabel) Nova.Cyan else Nova.Text, maxLines = 1)
                }
            }
            Spacer(Modifier.height(16.dp))
            SeekBar(s)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth()) {
                Text(formatTime(s.position), style = NovaType.Label, color = Nova.TextDim)
                Spacer(Modifier.weight(1f))
                if (s.duration > 0) {
                    val ends = SimpleDateFormat("h:mm a", Locale.getDefault())
                        .format(Date(System.currentTimeMillis() + ((s.duration - s.position) * 1000 / s.speed.coerceAtLeast(0.1f)).toLong()))
                    Text("Ends at $ends   ·   ", style = NovaType.Label, color = Nova.TextFaint)
                }
                Text("-" + formatTime((s.duration - s.position).coerceAtLeast(0.0)), style = NovaType.Label, color = Nova.TextDim)
            }
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RoundIcon(Icons.Outlined.Replay10, "Back 10 seconds", size = 52) { s.seekRelative(-10.0); PlayerUi.poke() }
                    PlayButton(!s.paused) { s.togglePause(); PlayerUi.poke() }
                    RoundIcon(Icons.Outlined.Forward10, "Forward 10 seconds", size = 52) { s.seekRelative(10.0); PlayerUi.poke() }
                    if (now.nextEpisode != null) RoundIcon(Icons.Filled.SkipNext, "Next episode", size = 52) { Player.playNext() }
                }
                VolumeControl(s, Modifier.align(Alignment.CenterEnd))
            }
        }
    }
}

@Composable
private fun VolumeControl(s: MpvSession, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        RoundIcon(if (s.muted || s.volume <= 0f) Icons.Outlined.VolumeOff else Icons.Outlined.VolumeUp, "Mute", size = 40) { s.toggleMute() }
        Spacer(Modifier.width(8.dp))
        Slider(
            value = if (s.muted) 0f else s.volume.coerceIn(0f, 100f),
            onValueChange = { s.changeVolume(it); PlayerUi.poke() },
            valueRange = 0f..100f,
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Nova.Cyan, inactiveTrackColor = Color(0x40FFFFFF)),
            modifier = Modifier.width(130.dp),
        )
    }
}

@Composable
private fun SeekBar(s: MpvSession) {
    val (src, hovered) = rememberHover()
    var dragFrac by remember { mutableStateOf<Float?>(null) }
    var widthPx by remember { mutableFloatStateOf(1f) }
    val frac = dragFrac ?: if (s.duration > 0) (s.position / s.duration).toFloat().coerceIn(0f, 1f) else 0f
    val active = hovered || dragFrac != null
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(24.dp)
            .hoverable(src)
            .pointerHoverIcon(PointerIcon.Hand)
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(s) {
                detectTapGestures { o ->
                    if (s.duration > 0) s.seekTo((o.x / widthPx).coerceIn(0f, 1f) * s.duration)
                    PlayerUi.poke()
                }
            }
            .pointerInput(s) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> dragFrac = (o.x / widthPx).coerceIn(0f, 1f) },
                    onDragEnd = {
                        dragFrac?.let { f -> if (s.duration > 0) s.seekTo(f * s.duration) }
                        dragFrac = null
                    },
                    onDragCancel = { dragFrac = null },
                    onHorizontalDrag = { change, _ ->
                        dragFrac = (change.position.x / widthPx).coerceIn(0f, 1f)
                        PlayerUi.poke()
                    },
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val h = if (active) 8.dp else 4.dp
        Box(Modifier.fillMaxWidth().height(h).clip(RoundedCornerShape(4.dp)).background(Color(0x33FFFFFF)))
        Box(Modifier.fillMaxWidth(frac).height(h).clip(RoundedCornerShape(4.dp)).background(Nova.AccentBrushH))
        if (active) {
            Box(Modifier.offset(x = maxWidth * frac - 9.dp).size(18.dp).clip(CircleShape).background(Color.White).border(3.dp, Nova.Cyan, CircleShape))
        }
        dragFrac?.let { f ->
            Text(
                formatTime(f * s.duration), style = NovaType.Label, color = Nova.Text,
                modifier = Modifier.offset(x = (maxWidth * f - 24.dp).coerceAtLeast(0.dp), y = (-26).dp)
                    .background(Nova.Bg.copy(alpha = 0.9f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun PlayButton(playing: Boolean, onClick: () -> Unit) {
    val (src, hovered) = rememberHover()
    Box(
        Modifier.size(76.dp).clip(CircleShape)
            .background(if (hovered) Color.White else Color(0x1FFFFFFF))
            .border(2.dp, if (hovered) Color.Transparent else Color(0xCCFFFFFF), CircleShape)
            .hoverable(src)
            .clickableHand(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (playing) "Pause" else "Play",
            tint = if (hovered) Nova.OnFocus else Color.White, modifier = Modifier.size(38.dp))
    }
}

@Composable
private fun RoundIcon(icon: ImageVector, label: String?, size: Int = 46, active: Boolean = false, onClick: () -> Unit) {
    val (src, hovered) = rememberHover()
    Box(
        Modifier.size(size.dp).clip(CircleShape)
            .background(
                when {
                    hovered -> Color.White
                    active -> Nova.Cyan.copy(alpha = 0.33f)
                    else -> Color(0x1AFFFFFF)
                }
            )
            .hoverable(src)
            .clickableHand(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = if (hovered) Nova.OnFocus else Color.White, modifier = Modifier.size((size * 0.5f).dp))
    }
}

@Composable
private fun SeekToast(s: MpvSession, trigger: Int) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(trigger) { visible = true; delay(1600); visible = false }
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Nova.BgDeep.copy(alpha = 0.8f))))
                    .padding(horizontal = 48.dp, vertical = 30.dp)
            ) {
                val frac = if (s.duration > 0) (s.position / s.duration).toFloat().coerceIn(0f, 1f) else 0f
                Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(Color(0x33FFFFFF))) {
                    Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(Nova.AccentBrushH))
                }
                Spacer(Modifier.height(8.dp))
                Text("${formatTime(s.position)} / ${formatTime(s.duration)}", style = NovaType.Label, color = Nova.Text)
            }
        }
    }
}

@Composable
private fun SidePanel(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxHeight()
            .width(400.dp)
            .background(Brush.horizontalGradient(listOf(Nova.Bg.copy(alpha = 0.92f), Nova.Bg.copy(alpha = 0.98f))))
            .border(1.dp, Nova.Stroke)
            .padding(28.dp)
    ) {
        Text(title, style = NovaType.Title, color = Nova.Text)
        Spacer(Modifier.height(18.dp))
        content()
    }
}

@Composable
private fun PanelRow(label: String, detail: String, selected: Boolean, onClick: () -> Unit) {
    val (src, hovered) = rememberHover()
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(if (hovered) Nova.Cyan.copy(alpha = 0.20f) else Color(0x14FFFFFF))
            .hoverable(src)
            .clickableHand(onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = NovaType.Button, color = Nova.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (detail.isNotEmpty()) Text(detail, style = NovaType.Label, color = Nova.TextDim, maxLines = 1)
        }
        if (selected) Icon(Icons.Outlined.Check, null, tint = Nova.Cyan)
    }
}

@Composable
private fun TrackPanel(s: MpvSession, type: String, title: String) {
    val tracks = remember(s.tracksVersion, type, s.loaded) { s.tracks(type) }
    SidePanel(title) {
        if (tracks.isEmpty() && type == "audio") Text("No tracks available", style = NovaType.Body, color = Nova.TextDim)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (type == "sub") item {
                PanelRow("Off", "", tracks.none { it.selected }) { s.selectSub(null); PlayerUi.panel = PlayerPanel.None }
            }
            items(tracks) { t: MpvTrack ->
                PanelRow(t.label, t.detail, t.selected) {
                    if (type == "audio") s.selectAudio(t) else s.selectSub(t)
                    PlayerUi.panel = PlayerPanel.None
                }
            }
        }
    }
}

private val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

private fun speedLabel(v: Float): String =
    if (v == 1f) "Normal (1x)" else (if (v % 1f == 0f) "${v.toInt()}x" else "${v}x")

@Composable
private fun SpeedPanel(s: MpvSession) {
    SidePanel("Playback speed") {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(speeds) { v ->
                PanelRow(speedLabel(v), "", v == s.speed) { s.changeSpeed(v); PlayerUi.panel = PlayerPanel.None }
            }
        }
    }
}

fun formatTime(sec: Double): String {
    val t = sec.toLong().coerceAtLeast(0)
    val h = t / 3600
    val m = (t % 3600) / 60
    val ss = t % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, ss) else "%d:%02d".format(m, ss)
}

fun formatTime(sec: Float): String = formatTime(sec.toDouble())
