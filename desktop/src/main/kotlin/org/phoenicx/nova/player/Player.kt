package org.phoenicx.nova.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import org.phoenicx.nova.App
import org.phoenicx.nova.data.Item
import org.phoenicx.nova.data.Lang
import org.phoenicx.nova.data.PlaySelection
import org.phoenicx.nova.data.PlaybackSource
import java.io.File
import java.io.RandomAccessFile

/**
 * Plays videos in mpv, a free video player bundled next to PHOENIXA.exe (folder "mpv").
 * mpv plays practically every format (HEVC, AV1, HDR, Dolby Vision, DTS, TrueHD, PGS subtitles…).
 * Watch progress is read from mpv and sent to the Jellyfin server.
 */
object Player {
    var status by mutableStateOf<String?>(null); private set
    var statusIsError by mutableStateOf(false); private set
    var nowPlaying by mutableStateOf<Item?>(null); private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var session: Job? = null
    @Volatile private var process: Process? = null

    private val api get() = App.api
    private val prefs get() = App.prefs

    init {
        Runtime.getRuntime().addShutdownHook(Thread { runCatching { process?.destroy() } })
    }

    fun clearStatus() { status = null; statusIsError = false }

    private fun info(msg: String?) { status = msg; statusIsError = false }
    private fun fail(msg: String) { status = msg; statusIsError = true }

    /** Finds mpv.exe: next to the app, in the working folder, or on the PATH. */
    fun mpvPath(): File? {
        val candidates = mutableListOf<File>()
        System.getProperty("jpackage.app-path")?.let { candidates += File(it).parentFile.resolve("mpv/mpv.exe") }
        System.getProperty("compose.application.resources.dir")?.let { candidates += File(it).resolve("mpv/mpv.exe") }
        candidates += File(System.getProperty("user.dir"), "mpv/mpv.exe")
        candidates += File(prefs.folder, "mpv/mpv.exe")
        System.getenv("PATH")?.split(File.pathSeparator)?.forEach { dir ->
            candidates += File(dir, "mpv.exe")
            candidates += File(dir, "mpv")
        }
        return candidates.firstOrNull { it.isFile }
    }

    fun stop() {
        runCatching { process?.destroy() }
    }

    /**
     * Plays a movie or episode. For a series, plays the next episode to watch.
     * [fromStart] ignores the saved resume position.
     */
    fun play(item: Item, sel: PlaySelection? = null, fromStart: Boolean = false) {
        // Closing the current player lets its session finish and report the position by itself
        stop()
        session = scope.launch {
            try {
                info("Opening ${item.name}…")
                val target = resolveTarget(item) ?: run { fail("Nothing to play here"); return@launch }
                val mpv = mpvPath() ?: run {
                    fail("Video player (mpv) not found. Please re-download PHOENIXA for Windows.")
                    return@launch
                }
                val startTicks = if (fromStart) 0L else target.playbackPositionTicks
                val src = api.playbackInfo(
                    target.id, startTicks, DesktopProfile.json(prefs.maxBitrateMbps),
                    allowDirect = true, forceDirect = prefs.directPlayOnly, sel = sel,
                )
                runSession(mpv, target, src, startTicks, sel)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(e.message ?: "Couldn't start playback")
            }
        }
    }

    private suspend fun resolveTarget(item: Item): Item? = when {
        item.isSeries -> api.nextUpForSeries(item.id) ?: api.episodes(item.id).firstOrNull()
        item.type == "Season" && item.seriesId != null -> api.episodes(item.seriesId, item.id)
            .let { eps -> eps.firstOrNull { !it.played } ?: eps.firstOrNull() }
        item.isCollection -> null
        else -> item
    }

    private fun titleOf(it: Item): String = if (it.isEpisode)
        listOfNotNull(it.seriesName, it.episodeLabel, it.name).joinToString(" – ")
    else listOfNotNull(it.name, it.year?.toString()?.let { y -> "($y)" }).joinToString(" ")

    private suspend fun runSession(mpv: File, target: Item, src: PlaybackSource, startTicks: Long, sel: PlaySelection?) {
        val pipe = """\\.\pipe\phoenixa-mpv-${System.nanoTime()}"""
        val title = titleOf(target).replace("\"", "'")

        // Which audio / subtitle track to start with
        val audioIdx = sel?.audioIndex ?: Lang.pickAudio(src.streams, src.defaultAudioIndex)
        val subIdx = sel?.subtitleIndex ?: Lang.pickSubtitle(src.streams, src.defaultSubtitleIndex)

        val args = mutableListOf(
            mpv.absolutePath,
            "--force-window=immediate",
            "--keep-open=no",
            "--idle=no",
            "--input-ipc-server=$pipe",
            "--force-media-title=$title",
            "--title=$title – PHOENIXA",
            "--hwdec=auto-safe",
            "--save-position-on-quit=no",
            "--user-agent=PHOENIXA-Desktop/${App.version}",
        )
        if (prefs.playerFullscreen) args += "--fullscreen"
        if (startTicks > 0) args += "--start=${startTicks / 10_000_000}"

        if (!src.isTranscoding) {
            val a = src.embeddedAudio.indexOf(audioIdx)
            if (a >= 0) args += "--aid=${a + 1}"
        }
        src.externalSubs.forEach { args += "--sub-file=${it.url}" }
        when {
            subIdx < 0 -> args += "--sid=no"
            !src.isTranscoding && src.embeddedSubs.indexOf(subIdx) >= 0 -> args += "--sid=${src.embeddedSubs.indexOf(subIdx) + 1}"
            else -> {
                val ext = src.externalSubs.indexOfFirst { it.index == subIdx }
                if (ext >= 0) args += "--sid=${(if (src.isTranscoding) 0 else src.embeddedSubs.size) + ext + 1}"
            }
        }
        args += src.url

        val proc = ProcessBuilder(args)
            .directory(mpv.parentFile)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
        process = proc
        nowPlaying = target
        info(null)
        api.reportStart(target.id, src, startTicks)

        val state = PlaybackState(startTicks / 10_000_000.0)
        val reader = scope.launch { readIpc(pipe, proc, state) }
        val progress = scope.launch {
            while (isActive && proc.isAlive) {
                delay(10_000)
                if (proc.isAlive) api.reportProgress(target.id, src, state.ticks, state.paused)
            }
        }

        // Wait until the player window is closed
        while (proc.isAlive) delay(500)
        progress.cancel()
        reader.cancel()
        if (process === proc) process = null
        nowPlaying = null

        // Closed within the last 90 seconds counts as finished too
        if (target.runTimeTicks > 0 && state.ticks >= target.runTimeTicks - 900_000_000L) state.eof = true
        val endTicks = if (state.eof && target.runTimeTicks > 0) target.runTimeTicks else state.ticks
        api.reportStopped(target.id, src, endTicks)
        App.refreshTick++

        // Next episode
        if (state.eof && prefs.autoPlayNext && target.isEpisode && target.seriesId != null) {
            val next = runCatching {
                val eps = api.episodes(target.seriesId)
                val i = eps.indexOfFirst { it.id == target.id }
                if (i >= 0) eps.getOrNull(i + 1) else null
            }.getOrNull()
            if (next != null) play(next, fromStart = true)
        }
    }

    private class PlaybackState(startSec: Double) {
        @Volatile var positionSec: Double = startSec
        @Volatile var paused: Boolean = false
        @Volatile var eof: Boolean = false
        val ticks: Long get() = (positionSec * 10_000_000).toLong()
    }

    /** Listens to mpv's control pipe for the current position, pause state and end of file. */
    private fun readIpc(pipePath: String, proc: Process, state: PlaybackState) {
        var raf: RandomAccessFile? = null
        val deadline = System.currentTimeMillis() + 20_000
        while (raf == null && proc.isAlive && System.currentTimeMillis() < deadline) {
            raf = runCatching { RandomAccessFile(pipePath, "rw") }.getOrNull()
            if (raf == null) Thread.sleep(200)
        }
        val pipe = raf ?: return
        try {
            // Send all commands first, then only read (Windows pipes don't like reading and writing at once)
            val cmds = listOf("time-pos", "pause", "eof-reached").mapIndexed { i, prop ->
                JSONObject().put("command", JSONArray().put("observe_property").put(i + 1).put(prop)).toString() + "\n"
            }
            pipe.write(cmds.joinToString("").toByteArray(Charsets.UTF_8))
            while (proc.isAlive) {
                val line = pipe.readLine() ?: break
                val o = runCatching { JSONObject(line) }.getOrNull() ?: continue
                when (o.optString("event")) {
                    "property-change" -> when (o.optString("name")) {
                        "time-pos" -> if (!o.isNull("data")) state.positionSec = o.optDouble("data", state.positionSec)
                        "pause" -> state.paused = o.optBoolean("data")
                        "eof-reached" -> if (o.optBoolean("data")) state.eof = true
                    }
                    "end-file" -> if (o.optString("reason") == "eof") state.eof = true
                }
            }
        } catch (_: Exception) {
        } finally {
            runCatching { pipe.close() }
        }
    }

    /** Used by Settings to show whether the player was found */
    fun playerInfo(): String = mpvPath()?.absolutePath ?: "Not found"
}
