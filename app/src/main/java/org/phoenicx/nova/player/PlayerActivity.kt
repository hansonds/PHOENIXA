@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package org.phoenicx.nova.player

import android.content.Context
import android.content.Intent
import android.graphics.Color as AColor
import android.graphics.Typeface
import android.os.Bundle
import android.view.KeyEvent as AKeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.outlined.Forward10
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Replay10
import androidx.compose.material.icons.outlined.SurroundSound
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import org.phoenicx.nova.NovaApp
import org.phoenicx.nova.data.Item
import org.phoenicx.nova.ui.components.FocusSurface
import org.phoenicx.nova.ui.components.NovaButton
import org.phoenicx.nova.ui.components.NovaLoader
import org.phoenicx.nova.ui.components.formatTime
import org.phoenicx.nova.ui.screens.TitleOrLogo
import org.phoenicx.nova.ui.theme.Nova
import org.phoenicx.nova.ui.theme.NovaTheme
import org.phoenicx.nova.ui.theme.NovaType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PlayerActivity : ComponentActivity() {

    private lateinit var controller: PlayerController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        controller = PlayerController(this)
        val itemId = intent.getStringExtra(EXTRA_ID) ?: return finish()
        val sel = if (intent.hasExtra(EXTRA_SOURCE) || intent.hasExtra(EXTRA_AUDIO) || intent.hasExtra(EXTRA_SUB))
            org.phoenicx.nova.data.PlaySelection(
                mediaSourceId = intent.getStringExtra(EXTRA_SOURCE),
                audioIndex = if (intent.hasExtra(EXTRA_AUDIO)) intent.getIntExtra(EXTRA_AUDIO, 0) else null,
                subtitleIndex = if (intent.hasExtra(EXTRA_SUB)) intent.getIntExtra(EXTRA_SUB, -1) else null,
            ) else null
        controller.load(itemId, intent.getLongExtra(EXTRA_TICKS, 0L), StreamMode.Direct, sel)
        setContent {
            NovaTheme {
                PlayerScreen(controller, onExit = { finish() }, registerRescue = { rescue = it })
            }
        }
    }

    /** Set by the player screen; puts the remote's highlight back somewhere sensible. */
    private var rescue: (() -> Unit)? = null

    override fun onKeyDown(keyCode: Int, event: AKeyEvent): Boolean {
        // Only reached when no on-screen control handled the key, i.e. focus was lost
        val nav = keyCode in setOf(
            AKeyEvent.KEYCODE_DPAD_UP, AKeyEvent.KEYCODE_DPAD_DOWN, AKeyEvent.KEYCODE_DPAD_LEFT,
            AKeyEvent.KEYCODE_DPAD_RIGHT, AKeyEvent.KEYCODE_DPAD_CENTER, AKeyEvent.KEYCODE_ENTER,
        )
        if (nav && rescue != null) { rescue?.invoke(); return true }
        return super.onKeyDown(keyCode, event)
    }

    override fun onPause() {
        super.onPause()
        if (::controller.isInitialized) controller.player.pause()
    }

    override fun onDestroy() {
        if (::controller.isInitialized) controller.release()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_ID = "id"
        private const val EXTRA_TICKS = "ticks"
        private const val EXTRA_SOURCE = "source"
        private const val EXTRA_AUDIO = "audio"
        private const val EXTRA_SUB = "sub"
        fun start(context: Context, itemId: String, startTicks: Long, sel: org.phoenicx.nova.data.PlaySelection? = null) {
            val i = Intent(context, PlayerActivity::class.java)
                .putExtra(EXTRA_ID, itemId)
                .putExtra(EXTRA_TICKS, startTicks)
            sel?.mediaSourceId?.let { i.putExtra(EXTRA_SOURCE, it) }
            sel?.audioIndex?.let { i.putExtra(EXTRA_AUDIO, it) }
            sel?.subtitleIndex?.let { i.putExtra(EXTRA_SUB, it) }
            context.startActivity(i)
        }
    }
}

private enum class Panel { None, Subtitles, Audio, Speed }

@Composable
private fun PlayerScreen(c: PlayerController, onExit: () -> Unit, registerRescue: ((() -> Unit)) -> Unit) {
    var overlay by remember { mutableStateOf(true) }
    var showInfo by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf(Panel.None) }
    var lastInput by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var seekFlash by remember { mutableIntStateOf(0) }
    val rootRequester = remember { FocusRequester() }
    val playRequester = remember { FocusRequester() }
    val seekRequester = remember { FocusRequester() }
    val item = c.item
    val goBackRequester = remember { FocusRequester() }
    var rootFocused by remember { mutableStateOf(false) }
    var anyFocus by remember { mutableStateOf(false) }
    var refocusTick by remember { mutableIntStateOf(0) }

    // Puts focus where it belongs for the current state of the screen
    fun refocus() {
        val target = when {
            c.error != null -> goBackRequester
            panel != Panel.None -> null // the panel focuses its own selected row
            overlay -> playRequester
            else -> rootRequester
        }
        if (target != null) runCatching { target.requestFocus() }.onFailure { runCatching { rootRequester.requestFocus() } }
    }
    registerRescue { if (!overlay && c.error == null) overlay = true; refocusTick++ }
    LaunchedEffect(refocusTick) { if (refocusTick > 0) { delay(30); refocus() } }

    // Watchdog: if nothing on screen has focus (a control vanished), take it back
    LaunchedEffect(anyFocus) {
        if (!anyFocus) { delay(120); if (!anyFocus) refocus() }
    }
    // After a side panel closes, its rows are gone — go back to the play button
    LaunchedEffect(panel) {
        if (panel == Panel.None) { delay(60); refocus() }
    }
    // A new episode starts (Next pressed): the Next buttons may disappear
    LaunchedEffect(item?.id) { delay(80); refocus() }
    LaunchedEffect(c.error) { if (c.error != null) { overlay = true; delay(80); refocus() } }

    // Position ticker
    LaunchedEffect(Unit) {
        while (true) { c.tick(); delay(500) }
    }
    // Auto-hide overlay while playing
    LaunchedEffect(overlay, lastInput, c.isPlaying, panel, showInfo) {
        if (overlay && c.isPlaying && panel == Panel.None && !showInfo) {
            delay(5000)
            overlay = false
        }
    }
    LaunchedEffect(overlay) {
        delay(60)
        refocus()
    }
    LaunchedEffect(c.finished) { if (c.finished) onExit() }

    BackHandler {
        when {
            panel != Panel.None -> panel = Panel.None
            showInfo -> showInfo = false
            overlay -> overlay = false
            else -> onExit()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown) lastInput = System.currentTimeMillis()
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                // Media keys work everywhere
                when (e.key.nativeKeyCode) {
                    AKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, AKeyEvent.KEYCODE_MEDIA_PLAY, AKeyEvent.KEYCODE_MEDIA_PAUSE -> {
                        c.togglePlay(); overlay = true; return@onPreviewKeyEvent true
                    }
                    AKeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { c.seekBy(30_000); seekFlash++; return@onPreviewKeyEvent true }
                    AKeyEvent.KEYCODE_MEDIA_REWIND -> { c.seekBy(-10_000); seekFlash++; return@onPreviewKeyEvent true }
                    AKeyEvent.KEYCODE_MEDIA_NEXT -> { c.playNext(); return@onPreviewKeyEvent true }
                }
                // Overlay is up but the highlight sits on the background: pull it onto the controls
                if (overlay && rootFocused && panel == Panel.None) {
                    when (e.key) {
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.DirectionUp, Key.DirectionDown,
                        Key.DirectionLeft, Key.DirectionRight -> { refocus(); return@onPreviewKeyEvent true }
                        else -> {}
                    }
                }
                if (!overlay) {
                    when (e.key) {
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { c.togglePlay(); overlay = true; true }
                        Key.DirectionLeft -> { c.seekBy(-10_000); seekFlash++; true }
                        Key.DirectionRight -> { c.seekBy(10_000); seekFlash++; true }
                        Key.DirectionUp, Key.DirectionDown, Key.Menu -> { overlay = true; true }
                        else -> false
                    }
                } else false
            }
            .onFocusChanged { rootFocused = it.isFocused; anyFocus = it.hasFocus }
            .focusRequester(rootRequester)
            .focusable()
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    player = c.player
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(AColor.BLACK)
                    keepScreenOn = true
                    isFocusable = false
                    isFocusableInTouchMode = false
                    subtitleView?.apply {
                        setStyle(
                            CaptionStyleCompat(
                                AColor.WHITE, 0x00000000, 0x00000000,
                                CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW, 0xE6000000.toInt(),
                                Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD),
                            )
                        )
                        setFractionalTextSize(0.052f)
                        setBottomPaddingFraction(0.08f)
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (c.buffering && c.error == null) {
            Box(Modifier.align(Alignment.Center).size(90.dp)) { NovaLoader() }
        }

        // Seek feedback when the overlay is hidden
        if (!overlay && seekFlash > 0) {
            SeekToast(c, seekFlash)
        }

        AnimatedVisibility(overlay, enter = fadeIn(tween(250)), exit = fadeOut(tween(400))) {
            Overlay(
                c = c, item = item, showInfo = showInfo,
                playRequester = playRequester, seekRequester = seekRequester,
                onInfo = { showInfo = !showInfo },
                onPanel = { panel = it },
            )
        }

        AnimatedVisibility(
            panel != Panel.None,
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            if (panel == Panel.Speed) {
                SpeedPanel(c, onDone = { panel = Panel.None })
            } else {
                TrackPanel(c, if (panel == Panel.Audio) C.TRACK_TYPE_AUDIO else C.TRACK_TYPE_TEXT,
                    title = if (panel == Panel.Audio) "Audio" else "Subtitles",
                    onDone = { panel = Panel.None })
            }
        }

        c.error?.let { err ->
            Column(
                Modifier.align(Alignment.Center).background(Nova.Bg.copy(alpha = 0.90f), RoundedCornerShape(22.dp)).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Can't play this", style = NovaType.Title, color = Nova.Text)
                Spacer(Modifier.height(8.dp))
                Text(err, style = NovaType.Body, color = Nova.TextDim)
                Spacer(Modifier.height(20.dp))
                NovaButton("Go back", primary = true, focusRequester = goBackRequester) { onExit() }
            }
        }
    }

    DisposableEffect(Unit) { onDispose { } }
}

@Composable
private fun Overlay(
    c: PlayerController,
    item: Item?,
    showInfo: Boolean,
    playRequester: FocusRequester,
    seekRequester: FocusRequester,
    onInfo: () -> Unit,
    onPanel: (Panel) -> Unit,
) {
    val api = NovaApp.instance.api
    Box(Modifier.fillMaxSize()) {
        // Scrims
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Nova.BgDeep.copy(alpha = 0.80f), 0.25f to Nova.BgDeep.copy(alpha = 0.20f),
                    0.55f to Nova.BgDeep.copy(alpha = 0.20f), 1f to Nova.BgDeep.copy(alpha = 0.95f),
                )
            )
        )
        if (showInfo) Box(Modifier.fillMaxSize().background(Nova.BgDeep.copy(alpha = 0.60f)))

        // Top bar icons
        Row(
            Modifier.padding(start = 48.dp, top = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RoundIcon(Icons.Outlined.ClosedCaption, "Subtitles") { onPanel(Panel.Subtitles) }
            RoundIcon(Icons.Outlined.SurroundSound, "Audio") { onPanel(Panel.Audio) }
            RoundIcon(Icons.Outlined.Speed, "Speed", active = c.speed != 1f) { onPanel(Panel.Speed) }
            if (c.nextEpisode != null) RoundIcon(Icons.Filled.SkipNext, "Next episode") { c.playNext() }
            RoundIcon(Icons.Outlined.Info, "Info", active = showInfo) { onInfo() }
        }
        if (c.mode != StreamMode.Direct) {
            Text(
                c.mode.label.uppercase(), style = NovaType.Caption, color = Nova.Gold,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 44.dp, end = 48.dp),
            )
        }

        // Info: synopsis + cast
        if (showInfo && item != null) {
            Row(Modifier.fillMaxWidth().padding(start = 48.dp, end = 48.dp, top = 104.dp)) {
                Column(Modifier.weight(1f).padding(end = 48.dp)) {
                    Text("SYNOPSIS", style = NovaType.Caption, color = Nova.TextFaint)
                    Spacer(Modifier.height(12.dp))
                    Text(item.overview, style = NovaType.Body.copy(fontSize = 17.sp, lineHeight = 27.sp), color = Nova.Text,
                        maxLines = 7, overflow = TextOverflow.Ellipsis)
                }
                Column(Modifier.width(340.dp)) {
                    Text("CAST & CREW", style = NovaType.Caption, color = Nova.TextFaint)
                    Spacer(Modifier.height(10.dp))
                    LazyColumn(Modifier.height(270.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(item.people.take(20)) { p ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(48.dp).clip(CircleShape).background(Nova.SurfaceHi), contentAlignment = Alignment.Center) {
                                    Text(p.name.take(1), style = NovaType.Section, color = Nova.TextDim)
                                    AsyncImage(api.personImage(p, 120), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                }
                                Spacer(Modifier.width(14.dp))
                                Column {
                                    Text(p.name, style = NovaType.Label.copy(fontSize = 15.sp), color = Nova.Text)
                                    Text(p.role.ifEmpty { p.type }, style = NovaType.Label.copy(fontSize = 12.sp), color = Nova.TextFaint, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Bottom block
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 48.dp, vertical = 28.dp)) {
            if (item != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TitleOrLogo(item, maxLogoH = 52.dp, maxLogoW = 260.dp, big = false)
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
                Spacer(Modifier.height(18.dp))
            }
            SeekBar(c, seekRequester)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth()) {
                Text(formatTime(c.position), style = NovaType.Label, color = Nova.TextDim)
                Spacer(Modifier.weight(1f))
                if (c.duration > 0) {
                    val ends = SimpleDateFormat("h:mm a", Locale.getDefault())
                        .format(Date(System.currentTimeMillis() + (c.duration - c.position)))
                    Text("Ends at $ends   ·   ", style = NovaType.Label, color = Nova.TextFaint)
                }
                Text("-" + formatTime(c.duration - c.position), style = NovaType.Label, color = Nova.TextDim)
            }
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundIcon(Icons.Outlined.Replay10, null, size = 52) { c.seekBy(-10_000) }
                PlayButton(c.isPlaying, playRequester) { c.togglePlay() }
                RoundIcon(Icons.Outlined.Forward10, null, size = 52) { c.seekBy(10_000) }
                if (c.nextEpisode != null) RoundIcon(Icons.Filled.SkipNext, null, size = 52) { c.playNext() }
            }
        }
    }
}

@Composable
private fun SeekBar(c: PlayerController, requester: FocusRequester) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val frac = if (c.duration > 0) (c.position.toFloat() / c.duration).coerceIn(0f, 1f) else 0f
    val buffered = if (c.duration > 0) (c.player.bufferedPosition.toFloat() / c.duration).coerceIn(0f, 1f) else 0f
    Box(
        Modifier
            .fillMaxWidth()
            .height(22.dp)
            .focusRequester(requester)
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                val step = if (e.nativeKeyEvent.repeatCount > 6) 30_000L else 10_000L
                when (e.key) {
                    Key.DirectionLeft -> { c.seekBy(-step); true }
                    Key.DirectionRight -> { c.seekBy(step); true }
                    Key.DirectionCenter, Key.Enter -> { c.togglePlay(); true }
                    else -> false
                }
            }
            .focusable(interactionSource = interaction),
        contentAlignment = Alignment.CenterStart,
    ) {
        val h = if (focused) 8.dp else 4.dp
        Box(Modifier.fillMaxWidth().height(h).clip(RoundedCornerShape(4.dp)).background(Color(0x33FFFFFF)))
        Box(Modifier.fillMaxWidth(buffered).height(h).clip(RoundedCornerShape(4.dp)).background(Color(0x40FFFFFF)))
        Box(Modifier.fillMaxWidth(frac).height(h).clip(RoundedCornerShape(4.dp)).background(Nova.AccentBrushH))
        if (focused) {
            Row(Modifier.fillMaxWidth(frac.coerceAtLeast(0.001f)), horizontalArrangement = Arrangement.End) {
                Box(
                    Modifier.offset(x = 9.dp).size(18.dp).clip(CircleShape).background(Color.White)
                        .border(3.dp, Nova.Cyan, CircleShape)
                )
            }
        }
    }
}

@Composable
private fun PlayButton(playing: Boolean, requester: FocusRequester, onClick: () -> Unit) {
    FocusSurface(onClick = onClick, shape = CircleShape, focusedScale = 1.1f, borderWidth = 0.dp, focusRequester = requester) { focused ->
        Box(
            Modifier.size(76.dp)
                .background(if (focused) Color.White else Color(0x1FFFFFFF), CircleShape)
                .border(2.dp, if (focused) Color.Transparent else Color(0xCCFFFFFF), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, null,
                tint = if (focused) Nova.OnFocus else Color.White, modifier = Modifier.size(38.dp))
        }
    }
}

@Composable
private fun RoundIcon(icon: ImageVector, label: String?, size: Int = 46, active: Boolean = false, onClick: () -> Unit) {
    FocusSurface(onClick = onClick, shape = CircleShape, focusedScale = 1.12f, borderWidth = 0.dp, glow = false) { focused ->
        Box(
            Modifier.size(size.dp).background(
                when {
                    focused -> Color.White
                    active -> Nova.Cyan.copy(alpha = 0.33f)
                    else -> Color(0x1AFFFFFF)
                }, CircleShape
            ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, label, tint = if (focused) Nova.OnFocus else Color.White, modifier = Modifier.size((size * 0.5f).dp))
        }
    }
}

@Composable
private fun SeekToast(c: PlayerController, trigger: Int) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(trigger) { visible = true; delay(1600); visible = false }
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Nova.BgDeep.copy(alpha = 0.80f))))
                    .padding(horizontal = 48.dp, vertical = 30.dp)
            ) {
                val frac = if (c.duration > 0) (c.position.toFloat() / c.duration).coerceIn(0f, 1f) else 0f
                Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(Color(0x33FFFFFF))) {
                    Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(Nova.AccentBrushH))
                }
                Spacer(Modifier.height(8.dp))
                Text("${formatTime(c.position)} / ${formatTime(c.duration)}", style = NovaType.Label, color = Nova.Text)
            }
        }
    }
}

@Composable
private fun TrackPanel(c: PlayerController, type: Int, title: String, onDone: () -> Unit) {
    val options = remember(c.tracksVersion, type) { c.tracks(type) }
    val first = remember { FocusRequester() }
    LaunchedEffect(type) { delay(80); runCatching { first.requestFocus() } }
    Column(
        Modifier
            .fillMaxHeight()
            .width(400.dp)
            .background(Brush.horizontalGradient(listOf(Nova.Bg.copy(alpha = 0.90f), Nova.Bg.copy(alpha = 0.98f))))
            .border(1.dp, Nova.Stroke)
            .padding(28.dp)
    ) {
        Text(title, style = NovaType.Title, color = Nova.Text)
        Spacer(Modifier.height(18.dp))
        if (options.isEmpty()) {
            Text("No tracks available", style = NovaType.Body, color = Nova.TextDim)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(options.size) { i ->
                val o = options[i]
                FocusSurface(
                    onClick = { c.select(type, o); onDone() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    focusedScale = 1.02f,
                    focusRequester = if (o.selected || (i == 0 && options.none { it.selected })) first else null,
                ) { focused ->
                    Row(
                        Modifier.fillMaxWidth()
                            .background(if (focused) Nova.Cyan.copy(alpha = 0.20f) else Color(0x14FFFFFF))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f).widthIn(max = 300.dp)) {
                            Text(o.label, style = NovaType.Button, color = Nova.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (o.detail.isNotEmpty()) Text(o.detail, style = NovaType.Label.copy(fontSize = 12.sp), color = Nova.TextDim, maxLines = 1)
                        }
                        if (o.selected) Icon(Icons.Outlined.Check, null, tint = Nova.Cyan)
                    }
                }
            }
        }
    }
}

private val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

private fun speedLabel(v: Float): String =
    if (v == 1f) "Normal (1x)" else (if (v % 1f == 0f) "${v.toInt()}x" else "${v}x")

@Composable
private fun SpeedPanel(c: PlayerController, onDone: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(80); runCatching { first.requestFocus() } }
    Column(
        Modifier
            .fillMaxHeight()
            .width(360.dp)
            .background(Brush.horizontalGradient(listOf(Nova.Bg.copy(alpha = 0.90f), Nova.Bg.copy(alpha = 0.98f))))
            .border(1.dp, Nova.Stroke)
            .padding(28.dp)
    ) {
        Text("Playback speed", style = NovaType.Title, color = Nova.Text)
        Spacer(Modifier.height(18.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(speeds.size) { i ->
                val v = speeds[i]
                val selected = v == c.speed
                FocusSurface(
                    onClick = { c.changeSpeed(v); onDone() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    focusedScale = 1.02f,
                    focusRequester = if (selected) first else null,
                ) { focused ->
                    Row(
                        Modifier.fillMaxWidth()
                            .background(if (focused) Nova.Cyan.copy(alpha = 0.20f) else Color(0x14FFFFFF))
                            .padding(horizontal = 16.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(speedLabel(v), style = NovaType.Button, color = Nova.Text, modifier = Modifier.weight(1f))
                        if (selected) Icon(Icons.Outlined.Check, null, tint = Nova.Cyan)
                    }
                }
            }
        }
    }
}
