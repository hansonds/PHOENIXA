@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package org.phoenicx.nova.player

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.phoenicx.nova.NovaApp
import org.phoenicx.nova.data.Item
import org.phoenicx.nova.data.PlaybackSource
import org.phoenicx.nova.data.PlaySelection
import org.phoenicx.nova.data.Lang
import org.phoenicx.nova.data.MediaStreamInfo
import java.util.Locale

data class TrackOption(
    val label: String,
    val detail: String,
    val selected: Boolean,
    val group: Tracks.Group?,
    val index: Int,
    val language: String? = null,
)

/** How the video reaches the TV, tried in this order until one works. */
enum class StreamMode(val label: String) { Direct("Direct play"), Hls("Transcoding"), Progressive("Transcoding (compat)") }

class PlayerController(context: Context) {
    private val api = NovaApp.instance.api
    private val prefs = NovaApp.instance.prefs
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val player: ExoPlayer

    var item by mutableStateOf<Item?>(null); private set
    var nextEpisode by mutableStateOf<Item?>(null); private set
    var isPlaying by mutableStateOf(false); private set
    var buffering by mutableStateOf(true); private set
    var position by mutableLongStateOf(0L); private set
    var duration by mutableLongStateOf(0L); private set
    var error by mutableStateOf<String?>(null); private set
    var finished by mutableStateOf(false); private set
    var tracksVersion by mutableLongStateOf(0L); private set
    var transcoding by mutableStateOf(false); private set
    var mode by mutableStateOf(StreamMode.Direct); private set

    private var source: PlaybackSource? = null
    private var progressJob: Job? = null
    private val lastErrors = mutableListOf<String>()
    var speed by mutableStateOf(1f); private set

    fun changeSpeed(v: Float) {
        speed = v
        player.setPlaybackSpeed(v)
    }

    init {
        val renderers = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        val selector = DefaultTrackSelector(context).apply {
            setParameters(
                buildUponParameters()
                    .setPreferredAudioLanguage(Locale.getDefault().language)
                    .setPreferredTextLanguage(null)
            )
        }
        // All video requests carry the Jellyfin token as a header (works with Jellyfin 10.11+)
        val http = OkHttpDataSource.Factory(api.http)
            .setDefaultRequestProperties(
                mapOf(
                    "Authorization" to api.authHeader(),
                    "X-Emby-Authorization" to api.authHeader(),
                )
            )
            .setUserAgent("PHOENIXA/1.4 (Android TV)")
        // Read-ahead buffer is capped by the app's memory, not just by time. A time-only limit let
        // high-bitrate 4K files fill the memory on Fire TV Sticks and crash the app back to Home.
        val heap = Runtime.getRuntime().maxMemory()
        val bufferBytes = (heap / 3).coerceIn(32L * 1024 * 1024, 160L * 1024 * 1024).toInt()
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15_000, 50_000, 1_000, 2_500)
            .setTargetBufferBytes(bufferBytes)
            .setPrioritizeTimeOverSizeThresholds(false)
            .build()
        player = ExoPlayer.Builder(context, renderers)
            .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(http))
            .setLoadControl(loadControl)
            .setTrackSelector(selector)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                report(paused = !playing)
            }

            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) duration = player.duration.coerceAtLeast(0)
                if (state == Player.STATE_ENDED) onEnded()
            }

            override fun onTracksChanged(tracks: Tracks) {
                tracksVersion++
                if (!pendingApplied && !tracks.isEmpty) applySelection()
            }

            override fun onPlayerError(e: PlaybackException) {
                val pos = player.currentPosition.coerceAtLeast(0)
                val next = when (mode) {
                    StreamMode.Direct -> if (prefs.directPlayOnly) null else StreamMode.Hls
                    StreamMode.Hls -> StreamMode.Progressive
                    StreamMode.Progressive -> null
                }
                val it = item
                if (next != null && it != null) {
                    lastErrors += "${mode.label}: ${e.errorCodeName}"
                    load(it.id, pos * 10_000, next)
                } else {
                    lastErrors += "${mode.label}: ${e.errorCodeName} ${e.cause?.message?.take(80) ?: ""}"
                    error = lastErrors.joinToString("\n")
                }
            }
        })
    }

    /** Version / audio / subtitle chosen on the details page (null = server defaults) */
    private var selection: PlaySelection? = null
    private var pendingApplied = true

    fun load(itemId: String, startTicks: Long, streamMode: StreamMode = StreamMode.Direct, sel: PlaySelection? = selection) {
        selection = sel
        pendingApplied = false
        if (streamMode == StreamMode.Direct) lastErrors.clear()
        mode = streamMode
        error = null
        finished = false
        buffering = true
        scope.launch {
            try {
                val prev = source
                val prevItem = item
                if (prev != null && prevItem != null && prevItem.id != itemId) {
                    val ticks = player.currentPosition * 10_000
                    scope.launch { api.reportStopped(prevItem.id, prev, ticks) }
                }
                // Ask the server for the item and the stream at the same time (faster start)
                val itemD = scope.async { api.item(itemId) }
                val srcD = scope.async {
                    api.playbackInfo(
                        itemId, startTicks,
                        DeviceProfile.get(progressive = streamMode == StreamMode.Progressive),
                        allowDirect = streamMode == StreamMode.Direct,
                        forceDirect = streamMode == StreamMode.Direct && prefs.directPlayOnly,
                        sel = sel,
                    )
                }
                val src = srcD.await()
                // If the server can't direct play it, we're really transcoding (HLS)
                if (streamMode == StreamMode.Direct && src.isTranscoding) mode = StreamMode.Hls
                source = src
                transcoding = src.isTranscoding

                val subs = src.externalSubs.map { s ->
                    MediaItem.SubtitleConfiguration.Builder(Uri.parse(s.url))
                        .setMimeType(MimeTypes.TEXT_VTT)
                        .setLanguage(s.language)
                        .setLabel(s.title)
                        .setId("ext${s.index}")
                        .build()
                }
                val mi = MediaItem.Builder()
                    .setUri(src.url)
                    .setMediaId(itemId)
                    .apply {
                        when {
                            src.url.contains(".m3u8", ignoreCase = true) -> setMimeType(MimeTypes.APPLICATION_M3U8)
                            src.isTranscoding -> setMimeType(MimeTypes.VIDEO_MP2T)
                        }
                    }
                    .setSubtitleConfigurations(subs)
                    .build()
                player.setMediaItem(mi, startTicks / 10_000)
                player.setPlaybackSpeed(speed)
                player.prepare()
                player.playWhenReady = true
                val it = itemD.await()
                item = it
                api.reportStart(itemId, src, startTicks)
                startProgressLoop()
                nextEpisode = null
                if (it.isEpisode && it.seriesId != null) {
                    nextEpisode = runCatching {
                        val eps = api.episodes(it.seriesId)
                        val i = eps.indexOfFirst { e -> e.id == it.id }
                        if (i >= 0) eps.getOrNull(i + 1) else null
                    }.getOrNull()
                }
            } catch (e: Exception) {
                error = (lastErrors + (e.message ?: "Couldn't start playback")).joinToString("\n")
            }
        }
    }

    private fun startProgressLoop() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                delay(10_000)
                report(paused = !player.isPlaying)
            }
        }
    }

    private fun report(paused: Boolean) {
        val src = source ?: return
        val it = item ?: return
        val ticks = player.currentPosition * 10_000
        scope.launch { api.reportProgress(it.id, src, ticks, paused) }
    }

    fun tick() {
        position = player.currentPosition.coerceAtLeast(0)
        val d = player.duration
        if (d > 0) duration = d
        else if (duration == 0L) duration = (item?.runTimeTicks ?: 0) / 10_000
    }

    private fun onEnded() {
        val next = nextEpisode
        if (next != null && prefs.autoPlayNext) playNext() else finished = true
    }

    fun playNext() {
        val next = nextEpisode ?: return
        load(next.id, 0, StreamMode.Direct, sel = null)
    }

    fun togglePlay() {
        if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
        player.playWhenReady = !player.playWhenReady
    }

    fun seekBy(ms: Long) {
        val target = (player.currentPosition + ms).coerceIn(0, if (duration > 0) duration - 1000 else Long.MAX_VALUE)
        player.seekTo(target)
        position = target
    }

    /** Jellyfin stream info for an ExoPlayer track (matched by order / external id), for proper names */
    private fun streamFor(type: Int, ordinal: Int, formatId: String?): MediaStreamInfo? {
        val src = source ?: return null
        val idx = if (type == C.TRACK_TYPE_TEXT && formatId?.contains("ext") == true) {
            formatId!!.substringAfterLast("ext").toIntOrNull()
        } else if (type == C.TRACK_TYPE_AUDIO) src.embeddedAudio.getOrNull(ordinal)
        else src.embeddedSubs.getOrNull(ordinal)
        return src.streams.firstOrNull { it.index == idx }
    }

    fun tracks(type: Int): List<TrackOption> {
        val out = mutableListOf<TrackOption>()
        val disabled = player.trackSelectionParameters.disabledTrackTypes.contains(type)
        var anySelected = false
        var embeddedOrdinal = 0
        player.currentTracks.groups.filter { it.type == type }.forEach { g ->
            for (i in 0 until g.length) {
                val f = g.getTrackFormat(i)
                val isExt = f.id?.contains("ext") == true
                val js = streamFor(type, embeddedOrdinal, f.id)
                if (!isExt) embeddedOrdinal++
                if (!g.isTrackSupported(i)) continue
                val (label, detail) = if (js != null) Lang.label(js) else {
                    val lang = Lang.name(f.language)
                    val main = f.label ?: lang ?: "Unknown language"
                    main to buildList {
                        if (f.label != null && lang != null && !f.label!!.contains(lang, true)) add(lang)
                        f.sampleMimeType?.substringAfter('/')?.uppercase()?.let { add(it.replace("X-", "")) }
                        if (f.channelCount > 0) add(if (f.channelCount >= 6) "${f.channelCount - 1}.1" else "${f.channelCount}ch")
                        if ((f.selectionFlags and C.SELECTION_FLAG_FORCED) != 0) add("Forced")
                    }.joinToString(" · ")
                }
                val sel = !disabled && g.isTrackSelected(i)
                if (sel) anySelected = true
                out += TrackOption(label, detail, sel, g, i, js?.language ?: f.language)
            }
        }
        if (type == C.TRACK_TYPE_TEXT) {
            out.add(0, TrackOption("Off", "", disabled || !anySelected, null, -1))
        }
        return out
    }

    fun select(type: Int, opt: TrackOption) {
        val b = player.trackSelectionParameters.buildUpon()
        if (opt.group == null) {
            b.setTrackTypeDisabled(type, true)
        } else {
            b.setTrackTypeDisabled(type, false)
            b.setOverrideForType(TrackSelectionOverride(opt.group.mediaTrackGroup, opt.index))
        }
        player.trackSelectionParameters = b.build()
        // Remember the language so the next movie / episode starts the same way
        if (type == C.TRACK_TYPE_AUDIO) Lang.rememberAudio(opt.language)
        else if (type == C.TRACK_TYPE_TEXT) Lang.rememberSubtitle(opt.language, off = opt.group == null)
    }

    /** Turns the details-page choice into ExoPlayer track picks once the file's tracks are known. */
    private fun applySelection() {
        pendingApplied = true
        val src = source ?: return
        // No explicit choice (next episode, Continue Watching…): use your remembered languages
        val sel = selection ?: PlaySelection(
            audioIndex = Lang.pickAudio(src.streams, src.defaultAudioIndex),
            subtitleIndex = Lang.pickSubtitle(src.streams, src.defaultSubtitleIndex),
        )
        val b = player.trackSelectionParameters.buildUpon()

        sel.audioIndex?.let { want ->
            val k = src.embeddedAudio.indexOf(want)
            val audio = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
                .flatMap { g -> (0 until g.length).map { g to it } }
            if (k >= 0 && k < audio.size) {
                val (g, i) = audio[k]
                b.setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, i))
            }
        }

        when (val want = sel.subtitleIndex) {
            null -> {}
            -1 -> b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            else -> {
                val text = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
                    .flatMap { g -> (0 until g.length).map { g to it } }
                val ext = text.firstOrNull { (g, i) -> g.getTrackFormat(i).id?.endsWith("ext$want") == true }
                val pick = ext ?: run {
                    val k = src.embeddedSubs.indexOf(want)
                    val embedded = text.filter { (g, i) -> g.getTrackFormat(i).id?.contains("ext") != true }
                    if (k >= 0 && k < embedded.size) embedded[k] else null
                }
                if (pick != null) {
                    b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    b.setOverrideForType(TrackSelectionOverride(pick.first.mediaTrackGroup, pick.second))
                }
            }
        }
        player.trackSelectionParameters = b.build()
    }

    fun release() {
        val src = source
        val it = item
        val ticks = player.currentPosition * 10_000
        progressJob?.cancel()
        if (src != null && it != null) {
            // fire-and-forget on a detached scope so it survives activity teardown
            CoroutineScope(Dispatchers.IO).launch { api.reportStopped(it.id, src, ticks) }
        }
        player.release()
        scope.cancel()
    }
}
