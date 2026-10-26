package org.phoenicx.nova.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.sun.jna.Memory
import com.sun.jna.Pointer
import com.sun.jna.ptr.PointerByReference
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.phoenicx.nova.data.Lang
import java.util.ArrayDeque
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/** One audio or subtitle track as mpv sees it */
data class MpvTrack(
    val id: Int,
    val type: String,
    val label: String,
    val detail: String,
    val language: String?,
    val selected: Boolean,
)

/**
 * Plays one video with libmpv inside the PHOENIXA window. mpv decodes the video and draws each frame
 * into memory; the frame is then shown by Compose, so PHOENIXA's own controls sit on top of it.
 */
class MpvSession private constructor(private val lib: LibMpv, private val h: Pointer) {

    // ---- State shown by the player screen ----
    var frame by mutableStateOf<ImageBitmap?>(null); private set
    var position by mutableStateOf(0.0); private set
    var duration by mutableStateOf(0.0); private set
    var paused by mutableStateOf(false); private set
    var buffering by mutableStateOf(true); private set
    var eof by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var volume by mutableFloatStateOf(100f); private set
    var muted by mutableStateOf(false); private set
    var speed by mutableFloatStateOf(1f); private set
    var loaded by mutableStateOf(false); private set
    var tracksVersion by mutableIntStateOf(0); private set

    /** Wanted size of the picture in pixels (set by the screen) */
    @Volatile var targetW = 1280
    @Volatile var targetH = 720

    private var renderCtx: Pointer? = null
    private val running = AtomicBoolean(true)
    private val frameReady = Semaphore(0)
    private var renderThread: Thread? = null
    private var eventThread: Thread? = null
    @Volatile private var onLoaded: (() -> Unit)? = null

    // Must stay referenced, or the JVM would free the callback while mpv still uses it
    private val updateCallback = object : LibMpv.UpdateCallback {
        override fun invoke(data: Pointer?) { frameReady.release() }
    }

    companion object {
        /** Creates the player, or returns an error message */
        fun create(): Pair<MpvSession?, String?> {
            val lib = LibMpv.get() ?: return null to (LibMpv.loadError ?: "Video engine missing")
            val h = lib.mpv_create() ?: return null to "Couldn't create the video player"
            val s = MpvSession(lib, h)
            return try {
                s.init()
                s to null
            } catch (e: Exception) {
                runCatching { lib.mpv_terminate_destroy(h) }
                null to (e.message ?: "Couldn't start the video player")
            }
        }
    }

    private fun opt(name: String, value: String) { lib.mpv_set_option_string(h, name, value) }

    private fun init() {
        opt("vo", "libmpv")
        opt("hwdec", "auto-copy")
        opt("keep-open", "yes")
        opt("terminal", "no")
        opt("config", "no")
        opt("input-default-bindings", "no")
        opt("input-vo-keyboard", "no")
        opt("osc", "no")
        opt("idle", "yes")
        opt("cache", "yes")
        opt("demuxer-max-bytes", "150MiB")
        opt("demuxer-readahead-secs", "30")
        opt("user-agent", "PHOENIXA-Desktop")
        opt("sub-font", "Segoe UI Semibold")
        opt("sub-font-size", "46")
        opt("sub-border-size", "2.5")
        opt("sub-shadow-offset", "1.5")
        opt("sub-margin-y", "40")
        opt("tone-mapping", "auto")
        val r = lib.mpv_initialize(h)
        if (r < 0) throw IllegalStateException("Video player failed to start (${lib.mpv_error_string(r)})")

        // Software render context: mpv draws frames into our memory
        val apiType = Memory(8).apply { setString(0, "sw", "UTF-8") }
        val params = Memory(32).apply {
            clear()
            setInt(0, LibMpv.RENDER_PARAM_API_TYPE)
            setPointer(8, apiType)
        }
        val ref = PointerByReference()
        val rc = lib.mpv_render_context_create(ref, h, params)
        if (rc < 0) throw IllegalStateException("Video output failed (${lib.mpv_error_string(rc)})")
        val ctx = ref.value
        renderCtx = ctx
        lib.mpv_render_context_set_update_callback(ctx, updateCallback, null)

        renderThread = thread(name = "mpv-render", isDaemon = true) { renderLoop(ctx) }
        eventThread = thread(name = "mpv-events", isDaemon = true) { eventLoop() }
    }

    // ------------------------------------------------------------------ playback

    fun load(url: String, startSec: Double, title: String, afterLoad: () -> Unit) {
        onLoaded = afterLoad
        if (startSec > 1) opt("start", "+${"%.1f".format(java.util.Locale.US, startSec)}")
        lib.mpv_set_property_string(h, "force-media-title", title)
        lib.mpv_command(h, arrayOf("loadfile", url, null))
    }

    val isAlive: Boolean get() = running.get()

    fun command(vararg args: String) { if (running.get()) lib.mpv_command(h, arrayOf(*args, null)) }
    fun set(name: String, value: String) { if (running.get()) lib.mpv_set_property_string(h, name, value) }

    fun get(name: String): String? {
        if (!running.get()) return null
        val p = lib.mpv_get_property_string(h, name) ?: return null
        return try { p.getString(0, "UTF-8") } finally { lib.mpv_free(p) }
    }

    fun togglePause() { set("pause", if (paused) "no" else "yes"); paused = !paused }
    fun seekRelative(sec: Double) {
        position = (position + sec).coerceIn(0.0, if (duration > 0) duration else Double.MAX_VALUE)
        command("seek", "%.2f".format(java.util.Locale.US, sec), "relative")
    }
    fun seekTo(sec: Double) {
        position = sec
        command("seek", "%.2f".format(java.util.Locale.US, sec.coerceAtLeast(0.0)), "absolute")
    }
    fun changeVolume(v: Float) { volume = v.coerceIn(0f, 130f); set("volume", "%.0f".format(java.util.Locale.US, volume)); if (muted) toggleMute() }
    fun toggleMute() { muted = !muted; set("mute", if (muted) "yes" else "no") }
    fun changeSpeed(v: Float) { speed = v; set("speed", v.toString()) }

    /** Reads position, pause, buffering … from mpv (called a few times a second) */
    fun poll() {
        get("time-pos")?.toDoubleOrNull()?.let { position = it }
        get("duration")?.toDoubleOrNull()?.let { if (it > 0) duration = it }
        paused = get("pause") == "yes"
        val cache = get("paused-for-cache") == "yes"
        val seeking = get("seeking") == "yes"
        val idle = get("core-idle") == "yes"
        buffering = !loaded || cache || seeking || (idle && !paused && !eof)
        if (get("eof-reached") == "yes") eof = true
    }

    // ------------------------------------------------------------------ tracks

    fun tracks(type: String): List<MpvTrack> {
        val n = get("track-list/count")?.toIntOrNull() ?: return emptyList()
        val out = mutableListOf<MpvTrack>()
        for (i in 0 until n) {
            val p = "track-list/$i/"
            if (get(p + "type") != type) continue
            val id = get(p + "id")?.toIntOrNull() ?: continue
            val lang = get(p + "lang")
            val title = get(p + "title")?.trim()?.takeIf { it.isNotEmpty() }
            val langName = Lang.name(lang)
            val label = when {
                title != null && langName != null && !title.contains(langName, true) -> "$langName · $title"
                title != null -> title
                langName != null -> langName
                else -> if (type == "sub") "Subtitle $id" else "Track $id"
            }
            val detail = buildList {
                get(p + "codec")?.let { add(codecName(it)) }
                if (type == "audio") get(p + "demux-channel-count")?.toIntOrNull()?.let { c ->
                    add(when { c >= 8 -> "7.1"; c >= 6 -> "5.1"; c == 2 -> "Stereo"; c == 1 -> "Mono"; else -> "${c}ch" })
                }
                if (get(p + "forced") == "yes") add("Forced")
                if (get(p + "external") == "yes") add("External")
            }.joinToString(" · ")
            out += MpvTrack(id, type, label, detail, lang, get(p + "selected") == "yes")
        }
        return out
    }

    private fun codecName(c: String) = when (c.lowercase()) {
        "subrip", "srt" -> "SRT"
        "hdmv_pgs_subtitle", "pgssub" -> "PGS"
        "ass", "ssa" -> "ASS"
        "webvtt" -> "VTT"
        "dvd_subtitle" -> "DVD"
        "eac3" -> "E-AC3"
        "truehd" -> "TrueHD"
        "dts" -> "DTS"
        else -> c.uppercase()
    }

    fun selectAudio(t: MpvTrack) {
        set("aid", t.id.toString())
        Lang.rememberAudio(t.language)
        tracksVersion++
    }

    fun selectSub(t: MpvTrack?) {
        if (t == null) { set("sid", "no"); Lang.rememberSubtitle(null, off = true) }
        else { set("sid", t.id.toString()); Lang.rememberSubtitle(t.language, off = false) }
        tracksVersion++
    }

    fun refreshTracks() { tracksVersion++ }

    // ------------------------------------------------------------------ threads

    private fun eventLoop() {
        while (running.get()) {
            val ev = lib.mpv_wait_event(h, 1.0) ?: continue
            when (ev.getInt(0)) {
                LibMpv.EVENT_SHUTDOWN -> return
                LibMpv.EVENT_FILE_LOADED -> {
                    loaded = true
                    runCatching { onLoaded?.invoke() }
                    tracksVersion++
                }
                LibMpv.EVENT_END_FILE -> {
                    val data = ev.getPointer(16)
                    if (data != null) {
                        val reason = data.getInt(0)
                        val err = data.getInt(4)
                        if (reason == LibMpv.END_ERROR) error = lib.mpv_error_string(err) ?: "Playback error"
                        if (reason == LibMpv.END_EOF) eof = true
                    }
                }
            }
        }
    }

    private fun renderLoop(ctx: Pointer) {
        val size = Memory(8)
        val format = Memory(8).apply { setString(0, "bgr0", "UTF-8") }
        val strideMem = Memory(8)
        val params = Memory(16L * 5)
        var buf: Pointer? = null
        var bytes = ByteArray(0)
        var w = 0
        var h = 0
        var stride = 0
        val old = ArrayDeque<Image>()

        while (running.get()) {
            frameReady.tryAcquire(40, TimeUnit.MILLISECONDS)
            if (!running.get()) break
            val flags = lib.mpv_render_context_update(ctx)
            val tw = targetW.coerceAtLeast(16)
            val th = targetH.coerceAtLeast(16)
            val resized = tw != w || th != h
            if ((flags and LibMpv.RENDER_UPDATE_FRAME) == 0L && !resized) continue
            if (resized) {
                w = tw; h = th
                stride = ((w * 4 + 63) / 64) * 64
                buf = Memory(stride.toLong() * h + 64).align(64)
                bytes = ByteArray(stride * h)
            }
            val b = buf ?: continue
            size.setInt(0, w); size.setInt(4, h)
            strideMem.setLong(0, stride.toLong())
            params.clear()
            params.setInt(0, LibMpv.RENDER_PARAM_SW_SIZE); params.setPointer(8, size)
            params.setInt(16, LibMpv.RENDER_PARAM_SW_FORMAT); params.setPointer(24, format)
            params.setInt(32, LibMpv.RENDER_PARAM_SW_STRIDE); params.setPointer(40, strideMem)
            params.setInt(48, LibMpv.RENDER_PARAM_SW_POINTER); params.setPointer(56, b)
            if (lib.mpv_render_context_render(ctx, params) < 0) continue
            b.read(0, bytes, 0, bytes.size)
            val img = Image.makeRaster(ImageInfo(w, h, ColorType.BGRA_8888, ColorAlphaType.OPAQUE), bytes, stride)
            frame = img.toComposeImageBitmap()
            // Free old frames promptly (they hold native memory); keep a few in case one is still on screen
            old.addLast(img)
            while (old.size > 6) runCatching { old.removeFirst().close() }
        }
        old.forEach { runCatching { it.close() } }
    }

    /** Stops playback and frees everything (call off the UI thread) */
    @Synchronized
    fun destroy() {
        if (!running.get()) return
        runCatching { command("stop") }
        running.set(false)
        frameReady.release()
        lib.mpv_wakeup(h)
        runCatching { renderThread?.join(2000) }
        runCatching { eventThread?.join(2000) }
        renderCtx?.let { runCatching { lib.mpv_render_context_free(it) } }
        renderCtx = null
        runCatching { lib.mpv_terminate_destroy(h) }
    }
}
