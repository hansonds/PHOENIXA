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
import java.util.Locale

data class TrackOption(
    val label: String,
    val detail: String,
    val selected: Boolean,
    val group: Tracks.Group?,
    val index: Int,
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
            .setUserAgent("PhoenicxNova/1.1 (Android TV)")
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(20_000, 120_000, 900, 2_500)
            .setPrioritizeTimeOverSizeThresholds(true)
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

    fun load(itemId: String, startTicks: Long, streamMode: StreamMode = StreamMode.Direct) {
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
        load(next.id, 0)
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

    fun tracks(type: Int): List<TrackOption> {
        val out = mutableListOf<TrackOption>()
        val disabled = player.trackSelectionParameters.disabledTrackTypes.contains(type)
        var anySelected = false
        player.currentTracks.groups.filter { it.type == type }.forEach { g ->
            for (i in 0 until g.length) {
                if (!g.isTrackSupported(i)) continue
                val f = g.getTrackFormat(i)
                val lang = f.language?.let { code ->
                    runCatching { Locale(code).displayLanguage }.getOrNull()?.takeIf { it.isNotBlank() && it != code } ?: code.uppercase()
                }
                val label = f.label ?: lang ?: "Track ${out.size + 1}"
                val detail = buildList {
                    if (f.label != null && lang != null) add(lang)
                    f.sampleMimeType?.substringAfter('/')?.uppercase()?.let { add(it.replace("X-", "")) }
                    if (f.channelCount > 0) add(if (f.channelCount >= 6) "${f.channelCount - 1}.1" else "${f.channelCount}ch")
                    if ((f.selectionFlags and C.SELECTION_FLAG_FORCED) != 0) add("Forced")
                }.joinToString(" · ")
                val sel = !disabled && g.isTrackSelected(i)
                if (sel) anySelected = true
                out += TrackOption(label, detail, sel, g, i)
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
