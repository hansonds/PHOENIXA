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
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.FastForward
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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import org.phoenicx.nova.NovaApp
import org.phoenicx.nova.immersiveOnPhone
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
        // Draw behind the system bars and camera cut-out (Android 15+ default), handled with insets
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        immersiveOnPhone()
        controller = PlayerController(this)
        val itemId = intent.getStringExtra(EXTRA_ID) ?: return finish()
        val sel = if (intent.hasExtra(EXTRA_SOURCE) || intent.hasExtra(EXTRA_AUDIO) || intent.hasExtra(EXTRA_SUB))
            org.phoenicx.nova.data.PlaySelection(
                mediaSourceId = intent.getStringExtra(EXTRA_SOURCE),
                audioIndex = if (intent.hasExtra(EXTRA_AUDIO)) intent.getIntExtra(EXTRA_AUDIO, 0) else null,
                subtitleIndex = if (intent.hasExtra(EXTRA_SUB)) intent.getIntExtra(EXTRA_SUB, -1) else null,
            ) else null
        controller.load(itemId, intent.getLongExtra(EXTRA_TICKS, 0L), StreamMode.Direct, sel)
        // The play / pause button in the picture-in-picture window
        androidx.core.content.ContextCompat.registerReceiver(
            this, pipReceiver, android.content.IntentFilter(PIP_TOGGLE), androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        setContent {
            // Keep the small window's button showing the right symbol
            androidx.compose.runtime.LaunchedEffect(controller.isPlaying, controller.inPip) {
                if (controller.inPip && android.os.Build.VERSION.SDK_INT >= 26) pipParams()?.let { runCatching { setPictureInPictureParams(it) } }
            }
            NovaTheme {
                PlayerScreen(controller, onExit = { finish() }, registerRescue = { rescue = it }, registerMediaKeys = { mediaKeys = it })
            }
        }
    }

    /** Set by the player screen; puts the remote's highlight back somewhere sensible. */
    private var rescue: (() -> Unit)? = null

    /** Set by the player screen: handles the remote's media keys (play/pause, rewind, fast-forward…) */
    private var mediaKeys: ((Int, Int) -> Boolean)? = null

    private val mediaCodes = setOf(
        AKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, AKeyEvent.KEYCODE_MEDIA_PLAY, AKeyEvent.KEYCODE_MEDIA_PAUSE,
        AKeyEvent.KEYCODE_HEADSETHOOK, AKeyEvent.KEYCODE_MEDIA_FAST_FORWARD, AKeyEvent.KEYCODE_MEDIA_REWIND,
        AKeyEvent.KEYCODE_MEDIA_NEXT, AKeyEvent.KEYCODE_MEDIA_PREVIOUS, AKeyEvent.KEYCODE_MEDIA_SKIP_FORWARD,
        AKeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD, AKeyEvent.KEYCODE_MEDIA_STEP_FORWARD, AKeyEvent.KEYCODE_MEDIA_STEP_BACKWARD,
    )

    // Media keys always work, even when nothing on screen has the highlight
    override fun dispatchKeyEvent(event: AKeyEvent): Boolean {
        if (event.keyCode in mediaCodes) {
            if (event.action == AKeyEvent.ACTION_DOWN) mediaKeys?.invoke(event.keyCode, event.repeatCount)
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: AKeyEvent): Boolean {
        // Only reached when no on-screen control handled the key, i.e. focus was lost
        val nav = keyCode in setOf(
            AKeyEvent.KEYCODE_DPAD_UP, AKeyEvent.KEYCODE_DPAD_DOWN, AKeyEvent.KEYCODE_DPAD_LEFT,
            AKeyEvent.KEYCODE_DPAD_RIGHT, AKeyEvent.KEYCODE_DPAD_CENTER, AKeyEvent.KEYCODE_ENTER,
        )
        if (nav && rescue != null) { rescue?.invoke(); return true }
        return super.onKeyDown(keyCode, event)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) immersiveOnPhone()
    }

    /** Phones / tablets: pressing Home while a video plays keeps it going in a small floating window */
    private fun pipParams(): android.app.PictureInPictureParams? {
        if (android.os.Build.VERSION.SDK_INT < 26 || NovaApp.instance.isTv) return null
        val vs = controller.player.videoSize
        val ratio = if (vs.width > 0 && vs.height > 0) vs.width.toFloat() * vs.pixelWidthHeightRatio / vs.height else 16f / 9f
        // Android only allows shapes between 1:2.39 and 2.39:1
        val r = ratio.coerceIn(1f / 2.39f + 0.01f, 2.39f - 0.01f)
        val b = android.app.PictureInPictureParams.Builder().setAspectRatio(android.util.Rational((r * 1000).toInt(), 1000))
        // A play / pause button in the small window
        val playing = controller.player.playWhenReady
        val pi = android.app.PendingIntent.getBroadcast(
            this, 1, Intent(PIP_TOGGLE).setPackage(packageName),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        b.setActions(listOf(
            android.app.RemoteAction(
                android.graphics.drawable.Icon.createWithResource(this, if (playing) org.phoenicx.nova.R.drawable.ic_pip_pause else org.phoenicx.nova.R.drawable.ic_pip_play),
                if (playing) "Pause" else "Play", if (playing) "Pause" else "Play", pi,
            )
        ))
        if (android.os.Build.VERSION.SDK_INT >= 31) b.setSeamlessResizeEnabled(true)
        return b.build()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (!::controller.isInitialized || !controller.player.isPlaying) return
        val p = pipParams() ?: return
        if (packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            runCatching { enterPictureInPictureMode(p) }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (::controller.isInitialized) controller.inPip = isInPictureInPictureMode
    }

    override fun onPause() {
        super.onPause()
        // Keep playing in the floating window; pause otherwise
        val pip = android.os.Build.VERSION.SDK_INT >= 24 && isInPictureInPictureMode
        if (::controller.isInitialized && !pip) controller.player.pause()
    }

    override fun onStop() {
        super.onStop()
        // The floating window was closed, or the app went to the background: pause
        if (::controller.isInitialized) controller.player.pause()
    }

    private val pipReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            if (this@PlayerActivity::controller.isInitialized) controller.togglePlay()
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(pipReceiver) }
        if (::controller.isInitialized) controller.release()
        // Android TV: refresh "Continue watching" once the server has the new position
        val app = applicationContext
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ org.phoenicx.nova.tv.WatchNext.sync(app) }, 2500)
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_ID = "id"
        private const val EXTRA_TICKS = "ticks"
        private const val EXTRA_SOURCE = "source"
        private const val EXTRA_AUDIO = "audio"
        private const val EXTRA_SUB = "sub"
        private const val PIP_TOGGLE = "org.phoenicx.nova.PIP_TOGGLE"
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

private enum class Panel { None, Subtitles, Audio, Speed, Quick }

@Composable
private fun PlayerScreen(
    c: PlayerController,
    onExit: () -> Unit,
    registerRescue: ((() -> Unit)) -> Unit,
    registerMediaKeys: (((Int, Int) -> Boolean)) -> Unit,
) {
    var overlay by remember { mutableStateOf(true) }
    val touch = !NovaApp.instance.isTv
    // Phone / tablet: screen lock against pocket touches, hold for 2x, swipe to seek, double-tap ripple
    var locked by remember { mutableStateOf(false) }
    var unlockShownAt by remember { mutableLongStateOf(0L) }
    var holdSpeedFrom by remember { mutableStateOf<Float?>(null) }
    var seekPreview by remember { mutableStateOf<Long?>(null) }
    var ripple by remember { mutableStateOf(Ripple(0, 0, 0)) }
    // Remote: a long press on OK opens the quick options
    var okLong by remember { mutableStateOf(false) }
    var okDown by remember { mutableStateOf(false) }
    // Picture-in-picture: only the video, no controls or panels
    LaunchedEffect(c.inPip) { if (c.inPip) overlay = false }
    var showInfo by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf(Panel.None) }
    LaunchedEffect(c.inPip) { if (c.inPip) { panel = Panel.None; showInfo = false } }
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
    registerRescue { if (!overlay && c.error == null && !locked) overlay = true; refocusTick++ }
    registerMediaKeys { code, repeat ->
        lastInput = System.currentTimeMillis()
        val step = if (repeat > 6) 30_000L else 10_000L
        when (code) {
            AKeyEvent.KEYCODE_MEDIA_PLAY -> { c.player.play(); if (!locked) overlay = true }
            AKeyEvent.KEYCODE_MEDIA_PAUSE -> { c.player.pause(); if (!locked) overlay = true }
            AKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, AKeyEvent.KEYCODE_HEADSETHOOK -> { c.togglePlay(); if (!locked) overlay = true }
            AKeyEvent.KEYCODE_MEDIA_FAST_FORWARD, AKeyEvent.KEYCODE_MEDIA_SKIP_FORWARD, AKeyEvent.KEYCODE_MEDIA_STEP_FORWARD -> { c.seekBy(step); seekFlash++ }
            AKeyEvent.KEYCODE_MEDIA_REWIND, AKeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD, AKeyEvent.KEYCODE_MEDIA_STEP_BACKWARD -> { c.seekBy(-step); seekFlash++ }
            AKeyEvent.KEYCODE_MEDIA_NEXT -> if (repeat == 0) c.playNext()
            AKeyEvent.KEYCODE_MEDIA_PREVIOUS -> if (repeat == 0) { c.seekTo(0f); seekFlash++ }
        }
        true
    }
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
            // Locked: Back only shows the unlock button (so a pocket can't close the video)
            locked -> unlockShownAt = System.currentTimeMillis()
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
                val isOk = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
                // OK held down: open the quick options (and don't also press the button under the highlight)
                if (isOk && c.error == null) {
                    if (e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount > 0) {
                        if (!okLong) { okLong = true; panel = Panel.Quick }
                        return@onPreviewKeyEvent true
                    }
                    if (e.type == KeyEventType.KeyUp) {
                        if (okLong) { okLong = false; okDown = false; return@onPreviewKeyEvent true }
                        if (okDown) { okDown = false; c.togglePlay(); overlay = true; return@onPreviewKeyEvent true }
                        return@onPreviewKeyEvent false
                    }
                }
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (e.key == Key.Menu && c.error == null) { panel = if (panel == Panel.Quick) Panel.None else Panel.Quick; return@onPreviewKeyEvent true }
                // Overlay is up but the highlight sits on the background: pull it onto the controls
                if (overlay && rootFocused && panel == Panel.None) {
                    when (e.key) {
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.DirectionUp, Key.DirectionDown,
                        Key.DirectionLeft, Key.DirectionRight -> { refocus(); return@onPreviewKeyEvent true }
                        else -> {}
                    }
                }
                // Controls hidden and no panel open: the remote controls the video directly.
                // (With a panel open, its buttons get the keys, even if the controls have faded away.)
                if (!overlay && panel == Panel.None) {
                    when (e.key) {
                        // Play / pause when OK is let go (so holding it can open the quick options instead)
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { okDown = true; true }
                        Key.DirectionLeft -> { c.seekBy(-10_000); seekFlash++; true }
                        Key.DirectionRight -> { c.seekBy(10_000); seekFlash++; true }
                        Key.DirectionUp, Key.DirectionDown -> { overlay = true; true }
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
            // The player is rebuilt once if the device's decoder crashes: show the new one.
            update = { view ->
                if (view.player !== c.player) view.player = c.player
                // Keep the screen on while playing or loading; when paused it may sleep as usual
                view.keepScreenOn = c.isPlaying || c.buffering
                view.resizeMode = when (c.videoFit) {
                    1 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    2 -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Touch: tap shows / hides the controls (or closes a panel), double-tap left / right side seeks 10 s,
        // pinch out to zoom the picture to fill the screen, pinch in to fit it again
        var fitHint by remember { mutableStateOf<String?>(null) }
        val activity = androidx.compose.ui.platform.LocalContext.current as android.app.Activity
        val gestures = remember { SwipeLevels(activity) }
        LaunchedEffect(fitHint) { if (fitHint != null) { delay(1400); fitHint = null } }
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    // One finger up/down: right half = volume, left half = brightness (phones only).
                    // Two fingers: pinch out to zoom the picture, pinch in to fit it again.
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (locked) {
                            // Locked: ignore everything (a tap shows the unlock button, handled below)
                            do { val ev = awaitPointerEvent() } while (ev.changes.any { it.pressed })
                            return@awaitEachGesture
                        }
                        var zoomAcc = 1f
                        var swipe: String? = null // "volume", "brightness" or "seek" once decided
                        val seekFrom = c.position
                        // Leave the left and right edges to Android's back gesture
                        val sideEdge = 32.dp.toPx()
                        val canSeek = down.position.x > sideEdge && down.position.x < size.width - sideEdge
                        var totalY = 0f
                        var totalX = 0f
                        val slop = viewConfiguration.touchSlop
                        val leftSide = down.position.x < size.width / 2
                        // Leave the top and bottom edges to Android's own swipe gestures (home, recent apps, notifications)
                        val edge = maxOf(size.height * 0.15f, 72.dp.toPx())
                        val nearEdge = down.position.y < edge || down.position.y > size.height - edge
                        do {
                            val ev = awaitPointerEvent()
                            val pressed = ev.changes.filter { it.pressed }
                            if (pressed.size >= 2) {
                                zoomAcc *= ev.calculateZoom()
                                if (zoomAcc > 1.2f && c.videoFit == 0) { c.setFit(1); fitHint = c.videoFitLabel; zoomAcc = 1f }
                                else if (zoomAcc < 0.83f && c.videoFit != 0) { c.setFit(0); fitHint = c.videoFitLabel; zoomAcc = 1f }
                                ev.changes.forEach { it.consume() }
                            } else if (!NovaApp.instance.isTv && !nearEdge && pressed.size == 1) {
                                val ch = pressed[0]
                                val d = ch.position - ch.previousPosition
                                totalY += d.y; totalX += d.x
                                if (swipe == null && kotlin.math.abs(totalY) > slop && kotlin.math.abs(totalY) > kotlin.math.abs(totalX) * 1.5f) {
                                    swipe = if (leftSide) "brightness" else "volume"
                                }
                                if (swipe == null && canSeek && holdSpeedFrom == null && kotlin.math.abs(totalX) > slop * 2 &&
                                    kotlin.math.abs(totalX) > kotlin.math.abs(totalY) * 1.5f && c.duration > 0) {
                                    swipe = "seek"
                                }
                                if (swipe == "seek") {
                                    // The whole screen width = 2 minutes
                                    val ms = (totalX / size.width * 120_000f).toLong()
                                    seekPreview = (seekFrom + ms).coerceIn(0L, (c.duration - 1000).coerceAtLeast(0))
                                    ch.consume()
                                } else if (swipe != null) {
                                    // Full screen height = full range
                                    val delta = -d.y / (size.height * 0.85f)
                                    if (swipe == "volume") gestures.changeVolume(delta) else gestures.changeBrightness(delta)
                                    ch.consume()
                                }
                            }
                        } while (ev.changes.any { it.pressed })
                        seekPreview?.let { target -> if (c.duration > 0) c.seekTo(target.toFloat() / c.duration) }
                        seekPreview = null
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            tryAwaitRelease()
                            // Let go after "hold for 2x": back to the normal speed
                            holdSpeedFrom?.let { c.changeSpeed(it); holdSpeedFrom = null }
                        },
                        onLongPress = {
                            if (touch && !locked && c.error == null && seekPreview == null && c.isPlaying) {
                                holdSpeedFrom = c.speed
                                c.changeSpeed(2f)
                                overlay = false
                            }
                        },
                        onTap = {
                            lastInput = System.currentTimeMillis()
                            when {
                                locked -> unlockShownAt = System.currentTimeMillis()
                                panel != Panel.None -> panel = Panel.None
                                showInfo -> showInfo = false
                                else -> overlay = !overlay
                            }
                        },
                        onDoubleTap = { o ->
                            if (!locked) {
                                lastInput = System.currentTimeMillis()
                                val side = if (o.x < size.width / 2) -1 else 1
                                c.seekBy(side * 10_000L)
                                if (touch) {
                                    // Keep adding up while you keep double-tapping the same side
                                    val now = System.currentTimeMillis()
                                    val more = ripple.side == side && now - ripple.at < 1200
                                    ripple = Ripple(side, if (more) ripple.seconds + 10 else 10, now)
                                } else if (!overlay) seekFlash++
                            }
                        },
                    )
                }
        )

        SwipeIndicator(gestures)

        if (touch) {
            SeekRipple(ripple)
            seekPreview?.let { target -> SwipeSeekLabel(target, target - c.position, c.duration) }
            if (holdSpeedFrom != null) {
                Row(
                    Modifier.align(Alignment.TopCenter).padding(top = 36.dp)
                        .background(Nova.BgDeep.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("2x", style = NovaType.Button, color = Color.White)
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Filled.FastForward, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
            // Locked: a tap shows the unlock button for a few seconds
            var unlockVisible by remember { mutableStateOf(false) }
            LaunchedEffect(unlockShownAt) {
                if (unlockShownAt == 0L) return@LaunchedEffect
                unlockVisible = true; delay(3000); unlockVisible = false
            }
            AnimatedVisibility(
                locked && unlockVisible, enter = fadeIn(), exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 40.dp),
            ) {
                Row(
                    Modifier.clip(RoundedCornerShape(26.dp)).background(Nova.BgDeep.copy(alpha = 0.85f))
                        .border(1.dp, Nova.Stroke, RoundedCornerShape(26.dp))
                        .pointerInput(Unit) { detectTapGestures { locked = false; unlockVisible = false; overlay = true } }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.LockOpen, null, tint = Nova.Cyan, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Tap to unlock", style = NovaType.Button, color = Color.White)
                }
            }
        }

        fitHint?.let { hint ->
            Text(
                hint, style = NovaType.Button, color = Color.White,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 40.dp)
                    .background(Nova.BgDeep.copy(alpha = 0.75f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 18.dp, vertical = 8.dp),
            )
        }

        if (c.buffering && c.error == null) {
            Box(Modifier.align(Alignment.Center).size(90.dp)) { NovaLoader() }
            if (c.reconnecting) {
                Text(
                    "Connection lost · reconnecting…", style = NovaType.Label, color = Color.White,
                    modifier = Modifier.align(Alignment.Center).padding(top = 130.dp)
                        .background(Nova.BgDeep.copy(alpha = 0.75f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }

        // Seek feedback when the overlay is hidden
        if (!overlay && seekFlash > 0) {
            SeekToast(c, seekFlash)
        }

        AnimatedVisibility(overlay && !locked, enter = fadeIn(tween(250)), exit = fadeOut(tween(400))) {
            Overlay(
                c = c, item = item, showInfo = showInfo,
                playRequester = playRequester, seekRequester = seekRequester,
                onInfo = { showInfo = !showInfo },
                onPanel = { panel = it },
                onLock = if (touch) ({
                    panel = Panel.None; showInfo = false; overlay = false
                    locked = true; unlockShownAt = System.currentTimeMillis()
                }) else null,
            )
        }

        AnimatedVisibility(
            panel != Panel.None,
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            if (panel == Panel.Quick) {
                QuickPanel(c, showInfo,
                    onPick = { next -> panel = next },
                    onInfo = { panel = Panel.None; overlay = true; showInfo = !showInfo },
                    onDone = { panel = Panel.None })
            } else if (panel == Panel.Speed) {
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
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Try again the same way (with "Direct play only" on, it stays direct play)
                    NovaButton("Try again", primary = true, focusRequester = goBackRequester) { c.retry() }
                    if (!NovaApp.instance.prefs.directPlayOnly && c.mode == StreamMode.Direct) {
                        NovaButton("Try with transcoding") { c.retryTranscoding() }
                    }
                    NovaButton("Go back") { onExit() }
                }
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
    onLock: (() -> Unit)?,
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
            val ctx = androidx.compose.ui.platform.LocalContext.current
            RoundIcon(Icons.Outlined.AspectRatio, "Screen size", active = c.videoFit != 0) {
                c.cycleFit()
                android.widget.Toast.makeText(ctx, c.videoFitLabel, android.widget.Toast.LENGTH_SHORT).show()
            }
            if (c.nextEpisode != null) RoundIcon(Icons.Filled.SkipNext, "Next episode") { c.playNext() }
            RoundIcon(Icons.Outlined.Info, "Info", active = showInfo) { onInfo() }
            if (onLock != null) RoundIcon(Icons.Outlined.Lock, "Lock screen") { onLock() }
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
                    TitleOrLogo(item, maxLogoH = 44.dp, maxLogoW = 200.dp, big = false)
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
    var widthPx by remember { mutableIntStateOf(1) }
    var dragFrac by remember { mutableStateOf<Float?>(null) }
    val shown = dragFrac ?: frac
    Box(
        Modifier
            .fillMaxWidth()
            .height(36.dp)
            .onSizeChanged { widthPx = it.width.coerceAtLeast(1) }
            // Touch: tap a spot or drag the bar to jump there
            .pointerInput(c) {
                detectTapGestures { o -> c.seekTo((o.x / widthPx).coerceIn(0f, 1f)) }
            }
            .pointerInput(c) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> dragFrac = (o.x / widthPx).coerceIn(0f, 1f) },
                    onDragEnd = { dragFrac?.let { c.seekTo(it) }; dragFrac = null },
                    onDragCancel = { dragFrac = null },
                    onHorizontalDrag = { change, _ -> dragFrac = (change.position.x / widthPx).coerceIn(0f, 1f) },
                )
            }
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
        val active = focused || dragFrac != null
        val h = if (active) 8.dp else 4.dp
        Box(Modifier.fillMaxWidth().height(h).clip(RoundedCornerShape(4.dp)).background(Color(0x33FFFFFF)))
        Box(Modifier.fillMaxWidth(buffered).height(h).clip(RoundedCornerShape(4.dp)).background(Color(0x40FFFFFF)))
        Box(Modifier.fillMaxWidth(shown).height(h).clip(RoundedCornerShape(4.dp)).background(Nova.AccentBrushH))
        if (active) {
            Row(Modifier.fillMaxWidth(shown.coerceAtLeast(0.001f)), horizontalArrangement = Arrangement.End) {
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

/** Volume and screen brightness changed by swiping up/down on the phone player */
private class SwipeLevels(private val activity: android.app.Activity) {
    private val audio = activity.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
    private val maxVol = audio.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    private var vol = audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC).toFloat() / maxVol
    private var bright: Float = activity.window.attributes.screenBrightness.takeIf { it >= 0f }
        ?: runCatching {
            android.provider.Settings.System.getInt(activity.contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS) / 255f
        }.getOrDefault(0.5f)

    /** What to show: "volume" or "brightness", its level 0..1, and when it last changed */
    var kind by mutableStateOf<String?>(null)
    var level by mutableStateOf(0f)
    var changedAt by mutableStateOf(0L)

    fun changeVolume(delta: Float) {
        vol = (vol + delta).coerceIn(0f, 1f)
        audio.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, kotlin.math.round(vol * maxVol).toInt(), 0)
        show("volume", vol)
    }

    fun changeBrightness(delta: Float) {
        bright = (bright + delta).coerceIn(0.01f, 1f)
        activity.window.attributes = activity.window.attributes.apply { screenBrightness = bright }
        show("brightness", bright)
    }

    private fun show(k: String, v: Float) { kind = k; level = v; changedAt = System.currentTimeMillis() }
}

@Composable
private fun BoxScope.SwipeIndicator(g: SwipeLevels) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(g.changedAt) {
        if (g.changedAt == 0L) return@LaunchedEffect
        visible = true
        delay(900)
        visible = false
    }
    val kind = g.kind ?: return
    androidx.compose.animation.AnimatedVisibility(
        visible,
        modifier = Modifier.align(if (kind == "volume") Alignment.CenterEnd else Alignment.CenterStart).padding(horizontal = 56.dp),
        enter = fadeIn(), exit = fadeOut(),
    ) {
        Column(
            Modifier.background(Nova.BgDeep.copy(alpha = 0.75f), RoundedCornerShape(24.dp)).padding(horizontal = 14.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                if (kind == "volume") (if (g.level <= 0f) Icons.Outlined.VolumeOff else Icons.Outlined.VolumeUp) else Icons.Outlined.LightMode,
                null, tint = Color.White, modifier = Modifier.size(26.dp),
            )
            Spacer(Modifier.height(10.dp))
            Box(Modifier.width(8.dp).height(140.dp).clip(RoundedCornerShape(4.dp)).background(Color(0x40FFFFFF)), contentAlignment = Alignment.BottomCenter) {
                Box(Modifier.fillMaxWidth().fillMaxHeight(g.level.coerceIn(0f, 1f)).background(Nova.AccentBrush))
            }
            Spacer(Modifier.height(10.dp))
            Text("${(g.level * 100).toInt()}", style = NovaType.Label, color = Color.White)
        }
    }
}

/** Double-tap feedback: a soft circle on that side with "+10 s" (adds up while you keep tapping) */
private data class Ripple(val side: Int, val seconds: Int, val at: Long)

@Composable
private fun BoxScope.SeekRipple(r: Ripple) {
    if (r.side == 0) return
    val anim = remember { androidx.compose.animation.core.Animatable(0f) }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(r.at) {
        visible = true
        anim.snapTo(0f)
        anim.animateTo(1f, tween(450))
        delay(500)
        visible = false
    }
    if (!visible) return
    Box(
        Modifier.align(if (r.side < 0) Alignment.CenterStart else Alignment.CenterEnd)
            .fillMaxHeight().fillMaxWidth(0.42f)
            .clip(if (r.side < 0) RoundedCornerShape(topEndPercent = 50, bottomEndPercent = 50) else RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50))
            .background(Color.White.copy(alpha = 0.10f * (1.3f - anim.value).coerceIn(0f, 1f))),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size((70 + 130 * anim.value).dp).clip(CircleShape)
                .background(Color.White.copy(alpha = 0.18f * (1f - anim.value)))
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(if (r.side < 0) Icons.Filled.FastRewind else Icons.Filled.FastForward, null, tint = Color.White, modifier = Modifier.size(34.dp))
            Text((if (r.side < 0) "-" else "+") + "${r.seconds} s", style = NovaType.Button, color = Color.White)
        }
    }
}

/** Swipe left / right on the video: where it will jump to when you let go */
@Composable
private fun BoxScope.SwipeSeekLabel(target: Long, delta: Long, duration: Long) {
    Column(
        Modifier.align(Alignment.Center)
            .background(Nova.BgDeep.copy(alpha = 0.78f), RoundedCornerShape(20.dp))
            .padding(horizontal = 24.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val sign = if (delta >= 0) "+" else "-"
        Text(sign + formatTime(kotlin.math.abs(delta)), style = NovaType.Title, color = Nova.Cyan)
        Text("${formatTime(target)} / ${formatTime(duration)}", style = NovaType.Label, color = Color.White)
    }
}

/** Remote: hold OK (or press Menu) for these */
@Composable
private fun QuickPanel(c: PlayerController, showInfo: Boolean, onPick: (Panel) -> Unit, onInfo: () -> Unit, onDone: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(80); runCatching { first.requestFocus() } }
    val rows = buildList<Triple<ImageVector, String, () -> Unit>> {
        add(Triple(Icons.Outlined.ClosedCaption, "Subtitles") { onPick(Panel.Subtitles) })
        add(Triple(Icons.Outlined.SurroundSound, "Audio") { onPick(Panel.Audio) })
        add(Triple(Icons.Outlined.Speed, "Speed · " + speedLabel(c.speed)) { onPick(Panel.Speed) })
        add(Triple(Icons.Outlined.AspectRatio, "Screen size · " + c.videoFitLabel) { c.cycleFit() })
        if (c.nextEpisode != null) add(Triple(Icons.Filled.SkipNext, "Next episode") { onDone(); c.playNext() })
        add(Triple(Icons.Outlined.Info, if (showInfo) "Hide info" else "Info") { onInfo() })
    }
    Column(
        Modifier
            .fillMaxHeight()
            .width(360.dp)
            .background(Brush.horizontalGradient(listOf(Nova.Bg.copy(alpha = 0.90f), Nova.Bg.copy(alpha = 0.98f))))
            .border(1.dp, Nova.Stroke)
            .padding(28.dp)
    ) {
        Text("Quick options", style = NovaType.Title, color = Nova.Text)
        Spacer(Modifier.height(18.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(rows.size) { i ->
                val (icon, label, action) = rows[i]
                FocusSurface(
                    onClick = action,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    focusedScale = 1.02f,
                    focusRequester = if (i == 0) first else null,
                ) { focused ->
                    Row(
                        Modifier.fillMaxWidth()
                            .background(if (focused) Nova.Cyan.copy(alpha = 0.20f) else Color(0x14FFFFFF))
                            .padding(horizontal = 16.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(14.dp))
                        Text(label, style = NovaType.Button, color = Nova.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
