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
import org.phoenicx.nova.syncplay.SyncPlay
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
    /** Jellyfin's number for this audio / subtitle stream (used to keep the choice when the player reloads) */
    val streamIndex: Int? = null,
)

/** How the video reaches the TV, tried in this order until one works. */
enum class StreamMode(val label: String) { Direct("Direct play"), Hls("Transcoding"), Progressive("Transcoding (compat)") }

class PlayerController(private val context: Context) : org.phoenicx.nova.syncplay.SyncTarget {
    private val api = NovaApp.instance.api
    private val prefs = NovaApp.instance.prefs
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Rebuilt once with the app's own (FFmpeg) audio decoders if the device's decoder crashes. */
    var player: ExoPlayer by mutableStateOf(buildPlayer(preferExtensions = false)); private set
    private var preferExtensions = false

    /** Lets "Alexa, pause", Google Assistant, and headset / Bluetooth buttons control the video */
    private var session: androidx.media3.session.MediaSession? = runCatching {
        androidx.media3.session.MediaSession.Builder(context, player).setId("phoenixa-${System.nanoTime()}").build()
    }.getOrNull()

    /** Swap in a freshly built player (keeps the media session pointing at it) */
    private fun replacePlayer(p: ExoPlayer) {
        player = p
        runCatching { session?.player = p }
    }

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

    /** Network trouble: tries again on its own a few times before showing an error */
    private var netRetries = 0
    var reconnecting by mutableStateOf(false); private set
    private var currentId: String? = null
    private var lastPos = 0L
    private var stallJob: Job? = null
    private val maxRetries = 3
    private val lastErrors = mutableListOf<String>()
    var speed by mutableStateOf(1f); private set

    /** True while the video is in the small picture-in-picture window (no controls shown) */
    var inPip by mutableStateOf(false)

    /** Picture size: 0 = Fit (whole picture), 1 = Zoom (fills the screen, edges cut), 2 = Stretch */
    /** Every video starts "Fit to screen"; a change (button, pinch) lasts for this video only */
    var videoFit by mutableStateOf(0); private set
    val videoFitLabel: String get() = listOf("Fit to screen", "Zoom to fill", "Stretch")[videoFit]

    fun setFit(mode: Int) {
        videoFit = mode.coerceIn(0, 2)
    }

    fun cycleFit() = setFit((videoFit + 1) % 3)

    fun changeSpeed(v: Float) {
        // SyncPlay: everyone has to move at the same pace
        if (SyncPlay.inGroup && v != 1f) { SyncPlay.notice.value = System.nanoTime() to "Speed stays at 1× in SyncPlay"; return }
        speed = v
        player.setPlaybackSpeed(v)
        // Dolby / DTS sent straight to the TV or soundbar can't be sped up or slowed down.
        // Switch to decoding the sound in the app (and carry on from the same moment).
        // (Phones decode it in the app already, so only TVs need this.)
        if (v != 1f && !pcmOnly && NovaApp.instance.isTv && player.audioFormat?.sampleMimeType in passthroughTypes) switchToPcm()
    }

    /** Audio decoded in the app (no passthrough), so the speed can be changed */
    private var pcmOnly = false
    private val passthroughTypes = setOf(
        MimeTypes.AUDIO_AC3, MimeTypes.AUDIO_E_AC3, MimeTypes.AUDIO_E_AC3_JOC, MimeTypes.AUDIO_AC4,
        MimeTypes.AUDIO_DTS, MimeTypes.AUDIO_DTS_HD, MimeTypes.AUDIO_DTS_EXPRESS, MimeTypes.AUDIO_TRUEHD,
    )

    private fun switchToPcm() {
        val current = item ?: return
        val pos = player.currentPosition.coerceAtLeast(0)
        val keepMode = mode
        val keepErrors = lastErrors.toList()
        runCatching { player.release() }
        pcmOnly = true
        replacePlayer(buildPlayer(preferExtensions))
        player.setPlaybackSpeed(speed)
        load(current.id, pos * 10_000, keepMode)
        lastErrors.clear()
        lastErrors += keepErrors
    }

    private fun buildPlayer(preferExtensions: Boolean): ExoPlayer {
        val renderers = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: android.content.Context, enableFloatOutput: Boolean, enableAudioOutputPlaybackParams: Boolean,
            ): androidx.media3.exoplayer.audio.AudioSink? =
                if (pcmOnly) {
                    // No context = no passthrough: everything is decoded to normal audio, which can change speed
                    @Suppress("DEPRECATION")
                    androidx.media3.exoplayer.audio.DefaultAudioSink.Builder().build()
                } else super.buildAudioSink(context, enableFloatOutput, enableAudioOutputPlaybackParams)
        }
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(
                if (preferExtensions) DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                else DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
            )
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
        val player = ExoPlayer.Builder(context, renderers)
            // Pause for phone calls / other apps playing sound, and when headphones are unplugged
            .setAudioAttributes(
                androidx.media3.common.AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus= */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            // Internet streams go through OkHttp; downloads on this phone (file://) are read straight from storage
            .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(androidx.media3.datasource.DefaultDataSource.Factory(context, http)))
            .setLoadControl(loadControl)
            .setTrackSelector(selector)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
        player.addListener(object : Player.Listener {
            override fun onPlaybackParametersChanged(p: androidx.media3.common.PlaybackParameters) {
                // The chosen speed didn't take (sound going straight to the TV): decode it in the app instead
                if (!pcmOnly && speed != 1f && kotlin.math.abs(p.speed - speed) > 0.01f) {
                    android.os.Handler(android.os.Looper.getMainLooper()).post { if (!pcmOnly) switchToPcm() }
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                report(paused = !playing)
            }

            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_BUFFERING || state == Player.STATE_READY) {
                    SyncPlay.onPlayerState(this@PlayerController, state == Player.STATE_BUFFERING)
                }
                if (state == Player.STATE_READY) {
                    duration = player.duration.coerceAtLeast(0)
                    netRetries = 0
                    reconnecting = false
                }
                if (state == Player.STATE_ENDED) onEnded()
                // Stuck loading for a long time (slow or unreachable server): treat it like a dropped connection
                stallJob?.cancel()
                if (state == Player.STATE_BUFFERING) {
                    stallJob = scope.launch {
                        // (Transcoding can take a while to start on a small server, so wait longer then)
                        delay(if (transcoding) 90_000 else 45_000)
                        if (player.playbackState == Player.STATE_BUFFERING && player.playWhenReady && error == null) {
                            networkTrouble("The server is taking too long to respond.")
                        }
                    }
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                tracksVersion++
                // Wait for the item details (to know its series) unless a choice was passed in
                if (!pendingApplied && !tracks.isEmpty && (selection != null || item?.id == loadingId)) applySelection()
            }

            override fun onPlayerError(e: PlaybackException) {
                val pos = player.currentPosition.coerceAtLeast(0)
                // Some devices (e.g. Fire TV Stick 3rd gen) have a hardware decoder that crashes on
                // certain files (Dolby Digital Plus, ...). Like the official Jellyfin app: rebuild the
                // player once with the app's own decoders preferred and continue at the same spot.
                val current = item
                // Network dropped / timed out: try the same way again a few times before anything else
                if (isNetworkError(e) && netRetries < maxRetries) {
                    networkTrouble("${mode.label}: ${e.errorCodeName}")
                    return
                }
                if (!preferExtensions && current != null && isDecoderCrash(e)) {
                    lastErrors += "${mode.label}: ${e.errorCodeName} (retrying with app decoders)"
                    switchToAppDecoders(current.id, pos * 10_000)
                    return
                }
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
        return player
    }

    private fun isNetworkError(e: PlaybackException): Boolean =
        e.errorCode in 2000..2999 || e.errorCode == PlaybackException.ERROR_CODE_TIMEOUT

    /**
     * Connection trouble: wait a moment and start again at the same spot, the same way (direct play stays
     * direct play). After a few tries, show the error with "Try again".
     */
    private fun networkTrouble(reason: String) {
        val id = currentId ?: return
        val pos = player.currentPosition.takeIf { it > 0 } ?: lastPos
        if (netRetries >= maxRetries) {
            reconnecting = false
            lastErrors += reason
            error = lastErrors.joinToString("\n")
            return
        }
        netRetries++
        reconnecting = true
        buffering = true
        scope.launch {
            delay(2_000L * netRetries)
            load(id, pos * 10_000, mode)
        }
    }

    /** "Try again" on the error box: same way as before, from where it stopped */
    fun retry() {
        val id = currentId ?: return
        if (offline) {
            org.phoenicx.nova.download.Downloads.playable(id)?.let { loadLocal(it, lastPos) }
            return
        }
        netRetries = 0
        lastErrors.clear()
        load(id, lastPos * 10_000, if (prefs.directPlayOnly) StreamMode.Direct else mode)
    }

    /** "Try with transcoding" (only offered when Direct play only is off) */
    fun retryTranscoding() {
        val id = currentId ?: return
        netRetries = 0
        load(id, lastPos * 10_000, StreamMode.Hls)
    }

    private fun isDecoderCrash(e: PlaybackException): Boolean {
        if (e.errorCode in PlaybackException.ERROR_CODE_DECODER_INIT_FAILED..PlaybackException.ERROR_CODE_DECODING_RESOURCES_RECLAIMED) return true
        var c: Throwable? = e
        while (c != null) {
            if (c is android.media.MediaCodec.CodecException ||
                c.javaClass.name.contains("MediaCodec", ignoreCase = true)
            ) return true
            c = c.cause
        }
        return false
    }

    private fun switchToAppDecoders(itemId: String, startTicks: Long) {
        val keepMode = mode
        val keepErrors = lastErrors.toList()
        runCatching { player.release() }
        preferExtensions = true
        replacePlayer(buildPlayer(preferExtensions = true))
        player.setPlaybackSpeed(speed)
        load(itemId, startTicks, keepMode)
        lastErrors.clear()
        lastErrors += keepErrors
    }

    /** Version / audio / subtitle chosen on the details page (null = server defaults) */
    private var selection: PlaySelection? = null
    private var pendingApplied = true
    private var loadingId: String? = null

    fun load(
        itemId: String, startTicks: Long, streamMode: StreamMode = StreamMode.Direct, sel: PlaySelection? = selection,
        /** SyncPlay: load and wait; the group starts together */
        startPaused: Boolean = false,
    ) {
        // While switching videos, don't save progress (it could land on the wrong episode)
        progressJob?.cancel()
        currentId = itemId
        lastPos = startTicks / 10_000
        selection = sel
        loadingId = itemId
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
                if (prev != null && prevItem != null && prevItem.id != itemId && player.currentMediaItem?.mediaId == prevItem.id) {
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
                player.playWhenReady = !startPaused
                val it = itemD.await()
                item = it
                if (!pendingApplied && !player.currentTracks.isEmpty) applySelection()
                api.reportStart(itemId, src, startTicks)
                startProgressLoop()
                nextEpisode = null
                if (it.isEpisode && it.seriesId != null) {
                    nextEpisode = runCatching {
                        val all = api.episodes(it.seriesId)
                        // Specials (season 0) aren't "next" after a normal episode
                        val eps = if (it.parentIndexNumber == 0) all else all.filter { e -> e.parentIndexNumber != 0 }
                        val i = eps.indexOfFirst { e -> e.id == it.id }
                        if (i >= 0) eps.getOrNull(i + 1) else null
                    }.getOrNull()
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                // Couldn't reach the server: try again a few times, then show the error
                if (e is java.io.IOException && netRetries < maxRetries) networkTrouble(e.message ?: "Network error")
                else error = (lastErrors + (e.message ?: "Couldn't start playback")).joinToString("\n")
            }
        }
    }

    /** Playing a downloaded file (no server needed) */
    var offline by mutableStateOf(false); private set

    /** Play a download from this phone's private storage */
    fun loadLocal(rec: org.phoenicx.nova.download.DownloadRec, startMs: Long) {
        val src = org.phoenicx.nova.download.Downloads.sourceFor(rec) ?: run { error = "This download is missing. Delete it and download it again."; return }
        progressJob?.cancel()
        offline = true
        currentId = rec.id
        lastPos = startMs
        selection = PlaySelection(mediaSourceId = rec.mediaSourceId, audioIndex = rec.audioIndex, subtitleIndex = rec.subtitleIndex)
            .takeIf { rec.audioIndex != null || rec.subtitleIndex != null }
        loadingId = rec.id
        pendingApplied = false
        mode = StreamMode.Direct
        error = null; finished = false; buffering = true
        source = src
        transcoding = false
        pendingSeriesId = rec.seriesId
        item = Item(
            id = rec.id, name = rec.name, type = rec.type, overview = rec.overview, year = rec.year,
            runTimeTicks = rec.runTimeTicks, seriesId = rec.seriesId, seriesName = rec.seriesName,
            indexNumber = rec.episode, parentIndexNumber = rec.season,
        )
        val subs = src.externalSubs.map { s ->
            MediaItem.SubtitleConfiguration.Builder(Uri.parse(s.url))
                .setMimeType(MimeTypes.TEXT_VTT).setLanguage(s.language).setLabel(s.title).setId("ext${s.index}").build()
        }
        player.setMediaItem(MediaItem.Builder().setUri(src.url).setMediaId(rec.id).setSubtitleConfigurations(subs).build(), startMs)
        player.prepare()
        player.playWhenReady = true
        nextEpisode = null
        // Save where you are, now and then
        progressJob = scope.launch {
            while (isActive) {
                delay(10_000)
                if (player.currentMediaItem?.mediaId == rec.id) org.phoenicx.nova.download.Downloads.saveResume(rec.id, player.currentPosition)
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
        if (offline) return
        val src = source ?: return
        val it = item ?: return
        // Only for the video that's really in the player (not the previous episode while the next one loads)
        if (player.currentMediaItem?.mediaId != it.id) return
        val ticks = player.currentPosition * 10_000
        scope.launch { api.reportProgress(it.id, src, ticks, paused) }
    }

    fun tick() {
        position = player.currentPosition.coerceAtLeast(0)
        if (position > 0 && player.playbackState == Player.STATE_READY) lastPos = position
        val d = player.duration
        if (d > 0) duration = d
        else if (duration == 0L) duration = (item?.runTimeTicks ?: 0) / 10_000
    }

    private fun onEnded() {
        if (offline) {
            currentId?.let { id ->
                org.phoenicx.nova.download.Downloads.saveResume(id, 0)
                // Tell the server it's watched, if we're online (ignored when not)
                CoroutineScope(Dispatchers.IO).launch { runCatching { api.setPlayed(id, true) } }
            }
            finished = true
            return
        }
        if (SyncPlay.inGroup) {
            // The group moves on together (or stops at the end)
            if (!(prefs.autoPlayNext && SyncPlay.onEnded())) finished = true
            return
        }
        val next = nextEpisode
        if (next != null && prefs.autoPlayNext) playNext() else finished = true
    }

    /** Series of the episode being loaded (tracks can be ready before the item details arrive) */
    private var pendingSeriesId: String? = null

    fun playNext() {
        if (SyncPlay.inGroup) { SyncPlay.requestNext(); return }
        val next = nextEpisode ?: return
        pendingSeriesId = next.seriesId
        if (pcmOnly && speed == 1f) {
            // Speed is back to normal: let Dolby / DTS go straight to the TV or soundbar again
            pcmOnly = false
            // Tell the server the finished episode stopped (the new player won't know about it)
            val src = source; val done = item; val ticks = player.currentPosition * 10_000
            if (src != null && done != null) CoroutineScope(Dispatchers.IO).launch { api.reportStopped(done.id, src, ticks) }
            runCatching { player.release() }
            replacePlayer(buildPlayer(preferExtensions))
        }
        load(next.id, 0, StreamMode.Direct, sel = null)
    }

    fun togglePlay() {
        if (SyncPlay.inGroup) {
            // Ask the group; the server tells everyone (us too) when to pause or play
            if (player.playWhenReady) SyncPlay.requestPause() else SyncPlay.requestUnpause()
            return
        }
        if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
        player.playWhenReady = !player.playWhenReady
    }

    /** Jump to a point of the video (0 = start, 1 = end); used by the touch seek bar */
    fun seekTo(fraction: Float) {
        if (duration <= 0) return
        val target = (duration * fraction).toLong().coerceIn(0, duration - 1000)
        position = target
        if (SyncPlay.inGroup) { SyncPlay.requestSeek(target); return }
        player.seekTo(target)
    }

    fun seekBy(ms: Long) {
        val target = (player.currentPosition + ms).coerceIn(0, if (duration > 0) duration - 1000 else Long.MAX_VALUE)
        position = target
        if (SyncPlay.inGroup) { SyncPlay.requestSeek(target); return }
        player.seekTo(target)
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
        source?.let { if (it.isTranscoding) return transcodeTracks(type, it) }
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
                out += TrackOption(label, detail, sel, g, i, js?.language ?: f.language,
                    streamIndex = js?.index ?: f.id?.takeIf { isExt }?.substringAfterLast("ext")?.toIntOrNull())
            }
        }
        if (type == C.TRACK_TYPE_TEXT) {
            out.add(0, TrackOption("Off", "", disabled || !anySelected, null, -1))
        }
        return out
    }

    /**
     * While transcoding the server sends one sound track (and burns picture subtitles into the video),
     * so list every track the file has; picking one asks the server again from the same spot.
     */
    private fun transcodeTracks(type: Int, src: PlaybackSource): List<TrackOption> {
        val kind = if (type == C.TRACK_TYPE_AUDIO) "Audio" else "Subtitle"
        val streams = src.streams.filter { it.type == kind }
        val cur = selection
        val chosen: Int = if (type == C.TRACK_TYPE_AUDIO) {
            cur?.audioIndex ?: src.defaultAudioIndex ?: streams.firstOrNull()?.index ?: -1
        } else {
            val textOff = player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
            val shownExt = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
                .flatMap { g -> (0 until g.length).filter { g.isTrackSelected(it) }.map { g.getTrackFormat(it) } }
                .firstNotNullOfOrNull { f -> f.id?.takeIf { it.contains("ext") }?.substringAfterLast("ext")?.toIntOrNull() }
            when {
                cur?.subtitleIndex != null && cur.subtitleIndex >= 0 &&
                    streams.any { it.index == cur.subtitleIndex && !it.isTextSubtitle } -> cur.subtitleIndex
                !textOff && shownExt != null -> shownExt
                else -> -1
            }
        }
        val out = streams.map { st ->
            val (label, detail) = Lang.label(st)
            TrackOption(label, detail, st.index == chosen, null, -1, st.language, streamIndex = st.index)
        }.toMutableList()
        if (type == C.TRACK_TYPE_TEXT) out.add(0, TrackOption("Off", "", chosen == -1, null, -1, streamIndex = -1))
        return out
    }

    /** Start again from the current spot with the current choices (transcoding track changes) */
    private fun reloadHere() {
        val it = item ?: return
        load(it.id, player.currentPosition.coerceAtLeast(0) * 10_000, mode, selection)
    }

    fun select(type: Int, opt: TrackOption) {
        // Keep the choice, so it survives any reload (speed change, switching to transcoding, retries…)
        val base = selection ?: PlaySelection(mediaSourceId = source?.mediaSourceId)
        val src = source
        val sid = item?.seriesId
        if (type == C.TRACK_TYPE_AUDIO) {
            Lang.rememberAudio(opt.language, sid)
            selection = base.copy(audioIndex = opt.streamIndex ?: base.audioIndex)
            if (src?.isTranscoding == true) { reloadHere(); return }
        } else if (type == C.TRACK_TYPE_TEXT) {
            val off = opt.group == null && (opt.streamIndex == null || opt.streamIndex < 0)
            Lang.rememberSubtitle(opt.language, off = off, seriesId = sid)
            val newSub = if (off) -1 else opt.streamIndex
            val wasBurnedIn = src?.isTranscoding == true && (base.subtitleIndex ?: -1) >= 0 &&
                src.streams.any { it.index == base.subtitleIndex && !it.isTextSubtitle }
            selection = base.copy(subtitleIndex = newSub ?: base.subtitleIndex)
            if (src?.isTranscoding == true) {
                val picture = newSub != null && newSub >= 0 && src.streams.any { it.index == newSub && !it.isTextSubtitle }
                // Picture subtitles are drawn into the video by the server: ask it again
                if (picture || wasBurnedIn) { reloadHere(); return }
                val b = player.trackSelectionParameters.buildUpon()
                if (newSub == null || newSub < 0) b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                else {
                    val pick = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
                        .flatMap { g -> (0 until g.length).map { g to it } }
                        .firstOrNull { (g, i) -> g.getTrackFormat(i).id?.endsWith("ext$newSub") == true }
                    if (pick != null) {
                        b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        b.setOverrideForType(TrackSelectionOverride(pick.first.mediaTrackGroup, pick.second))
                    }
                }
                player.trackSelectionParameters = b.build()
                return
            }
        }
        if (opt.group == null && type != C.TRACK_TYPE_TEXT) return
        val b = player.trackSelectionParameters.buildUpon()
        if (opt.group == null) {
            b.setTrackTypeDisabled(type, true)
        } else {
            b.setTrackTypeDisabled(type, false)
            b.setOverrideForType(TrackSelectionOverride(opt.group.mediaTrackGroup, opt.index))
        }
        player.trackSelectionParameters = b.build()
    }

    /** Turns the details-page choice into ExoPlayer track picks once the file's tracks are known. */
    private fun applySelection() {
        pendingApplied = true
        val src = source ?: return
        // No explicit choice (next episode, Continue Watching…): use your remembered languages
        val sid = item?.seriesId ?: pendingSeriesId
        val sel = selection ?: PlaySelection(
            audioIndex = Lang.pickAudio(src.streams, src.defaultAudioIndex, sid),
            subtitleIndex = Lang.pickSubtitle(src.streams, src.defaultSubtitleIndex, sid),
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

    // ---- Subtitles from the internet (the server's OpenSubtitles plugin) ----
    suspend fun searchSubtitles(lang3: String): Pair<List<org.phoenicx.nova.data.RemoteSubs.Result>, String?> {
        val it = item ?: return emptyList<org.phoenicx.nova.data.RemoteSubs.Result>() to "Still loading, try again in a moment"
        return org.phoenicx.nova.data.RemoteSubs.search(it.id, lang3)
    }

    /** Download it to the server, then switch to it right here. Returns an error message, or null. */
    suspend fun downloadSubtitle(sub: org.phoenicx.nova.data.RemoteSubs.Result): String? {
        val cur = item ?: return "Still loading"
        val (added, err) = org.phoenicx.nova.data.RemoteSubs.download(cur.id, source?.mediaSourceId, sub)
        if (added == null) return err
        Lang.rememberSubtitle(added.language, off = false, seriesId = cur.seriesId)
        selection = (selection ?: PlaySelection(mediaSourceId = source?.mediaSourceId)).copy(subtitleIndex = added.index)
        reloadHere()
        return null
    }

    // ---- SyncPlay: the server drives the player ----
    /** Called when the group wants us to stop (the activity closes the player) */
    var onSyncStop: (() -> Unit)? = null

    override val syncItemId: String? get() = if (player.currentMediaItem?.mediaId == currentId) currentId else null
    override val syncPositionMs: Long get() = player.currentPosition.coerceAtLeast(0)
    override val syncPlayWhenReady: Boolean get() = player.playWhenReady
    override val syncReady: Boolean get() = player.playbackState == Player.STATE_READY
    override fun syncLoad(itemId: String, startMs: Long) {
        if (speed != 1f) { speed = 1f; player.setPlaybackSpeed(1f) }
        load(itemId, startMs * 10_000, StreamMode.Direct, sel = null, startPaused = true)
    }
    override fun syncPlay() { player.playWhenReady = true }
    override fun syncPause() { player.playWhenReady = false }
    override fun syncSeek(ms: Long) { player.seekTo(ms); position = ms }
    override fun syncStop() { player.playWhenReady = false; onSyncStop?.invoke() }

    fun release() {
        stallJob?.cancel()
        if (offline) currentId?.let { id ->
            val pos = player.currentPosition
            if (!finished && pos > 0) org.phoenicx.nova.download.Downloads.saveResume(id, pos)
        }
        val src = source
        val it = item
        val ticks = player.currentPosition * 10_000
        progressJob?.cancel()
        if (src != null && it != null) {
            // fire-and-forget on a detached scope so it survives activity teardown
            CoroutineScope(Dispatchers.IO).launch { api.reportStopped(it.id, src, ticks) }
        }
        runCatching { session?.release() }
        session = null
        player.release()
        scope.cancel()
    }
}
