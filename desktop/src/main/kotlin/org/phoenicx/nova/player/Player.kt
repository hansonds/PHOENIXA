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
import org.phoenicx.nova.App
import org.phoenicx.nova.data.Item
import org.phoenicx.nova.data.Lang
import org.phoenicx.nova.data.PlaySelection
import org.phoenicx.nova.data.PlaybackSource

/** What is playing right now */
class NowPlaying(
    val item: Item,
    val source: PlaybackSource,
    val session: MpvSession,
    val startTicks: Long,
) {
    var nextEpisode by mutableStateOf<Item?>(null)
}

/**
 * Starts and stops playback inside the PHOENIXA window and keeps the Jellyfin server
 * informed (start, progress every 10 s, stop).
 */
object Player {
    /** Non-null while the player screen is open */
    var current by mutableStateOf<NowPlaying?>(null); private set

    var status by mutableStateOf<String?>(null); private set
    var statusIsError by mutableStateOf(false); private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var reporter: Job? = null
    private val api get() = App.api
    private val prefs get() = App.prefs

    fun clearStatus() { status = null; statusIsError = false }
    private fun info(msg: String?) { status = msg; statusIsError = false }
    private fun fail(msg: String) { status = msg; statusIsError = true }

    /** Plays a movie or episode; for a series, the next episode to watch. [fromStart] ignores the resume point. */
    fun play(item: Item, sel: PlaySelection? = null, fromStart: Boolean = false) {
        scope.launch {
            try {
                info("Opening ${item.name}…")
                val target = resolveTarget(item) ?: run { fail("Nothing to play here"); return@launch }
                val startTicks = if (fromStart) 0L else target.playbackPositionTicks
                val src = api.playbackInfo(
                    target.id, startTicks, DesktopProfile.json(prefs.maxBitrateMbps),
                    allowDirect = true, forceDirect = prefs.directPlayOnly, sel = sel,
                )
                val (session, err) = MpvSession.create()
                if (session == null) { fail(err ?: "Couldn't start the player"); return@launch }

                closeCurrent()
                val now = NowPlaying(target, src, session, startTicks)
                current = now
                info(null)

                val audioIdx = sel?.audioIndex ?: Lang.pickAudio(src.streams, src.defaultAudioIndex)
                val subIdx = sel?.subtitleIndex ?: Lang.pickSubtitle(src.streams, src.defaultSubtitleIndex)
                session.load(src.url, startTicks / 10_000_000.0, titleOf(target)) {
                    // Runs when the file is open: add external subtitles, then pick the tracks
                    src.externalSubs.forEach { session.command("sub-add", it.url, "auto", it.title) }
                    if (!src.isTranscoding) {
                        val a = src.embeddedAudio.indexOf(audioIdx)
                        if (a >= 0) session.set("aid", (a + 1).toString())
                    }
                    when {
                        subIdx < 0 -> session.set("sid", "no")
                        !src.isTranscoding && src.embeddedSubs.indexOf(subIdx) >= 0 ->
                            session.set("sid", (src.embeddedSubs.indexOf(subIdx) + 1).toString())
                        else -> {
                            val ext = src.externalSubs.indexOfFirst { it.index == subIdx }
                            if (ext >= 0) session.set("sid", ((if (src.isTranscoding) 0 else src.embeddedSubs.size) + ext + 1).toString())
                        }
                    }
                    session.refreshTracks()
                }
                api.reportStart(target.id, src, startTicks)
                startReporting(now)

                if (target.isEpisode && target.seriesId != null) {
                    now.nextEpisode = runCatching {
                        val eps = api.episodes(target.seriesId)
                        val i = eps.indexOfFirst { it.id == target.id }
                        if (i >= 0) eps.getOrNull(i + 1) else null
                    }.getOrNull()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(e.message ?: "Couldn't start playback")
            }
        }
    }

    private fun startReporting(now: NowPlaying) {
        reporter?.cancel()
        reporter = scope.launch {
            while (isActive && current === now) {
                delay(10_000)
                if (current === now) api.reportProgress(now.item.id, now.source, ticks(now), now.session.paused)
            }
        }
    }

    private fun ticks(now: NowPlaying): Long = (now.session.position * 10_000_000).toLong()

    /** Plays the next episode, if there is one */
    fun playNext() {
        val next = current?.nextEpisode ?: return
        play(next, fromStart = true)
    }

    /** Called when the video reaches its end */
    fun onFinished() {
        val now = current ?: return
        if (prefs.autoPlayNext && now.nextEpisode != null) playNext() else close()
    }

    /** Closes the player and goes back to where you were */
    fun close() {
        scope.launch { closeCurrent() }
    }

    private suspend fun closeCurrent() {
        val now = current ?: return
        current = null
        reporter?.cancel()
        val s = now.session
        var endTicks = ticks(now)
        val runtime = now.item.runTimeTicks
        // Closed within the last 90 seconds (or at the very end) counts as watched
        if (runtime > 0 && (s.eof || endTicks >= runtime - 900_000_000L)) endTicks = runtime
        s.destroy()
        api.reportStopped(now.item.id, now.source, endTicks)
        App.refreshTick++
    }

    /** Immediate stop when the app window closes */
    fun stop() {
        val now = current ?: return
        current = null
        runCatching { now.session.destroy() }
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

    /** Used by Settings */
    fun engineInfo(): String = LibMpv.dllPath()?.absolutePath ?: "Not found"
}
