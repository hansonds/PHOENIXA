package org.phoenicx.nova.syncplay

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import org.phoenicx.nova.NovaApp
import org.phoenicx.nova.data.Item
import java.time.Instant
import java.util.concurrent.TimeUnit

/** What the SyncPlay engine needs from the video player */
interface SyncTarget {
    /** The item in the player right now (null while nothing is loaded) */
    val syncItemId: String?
    val syncPositionMs: Long
    val syncPlayWhenReady: Boolean
    val syncReady: Boolean
    fun syncLoad(itemId: String, startMs: Long)
    fun syncPlay()
    fun syncPause()
    fun syncSeek(ms: Long)
    fun syncStop()
}

/**
 * SyncPlay: watch the same thing at the same time as other people on the server (Jellyfin's
 * own SyncPlay, so it also works together with the Jellyfin web page and official apps).
 *
 * Pressing play / pause / seek doesn't act on the video directly: it asks the server, and the
 * server tells everyone in the group (us included) exactly when to do it. A live connection
 * (WebSocket) to the server carries those commands.
 */
object SyncPlay {
    data class Group(val id: String, val name: String, val members: List<String>, val state: String)
    data class ListedGroup(val id: String, val name: String, val members: List<String>, val state: String)

    /** The group we're in (null = not in one) */
    val group = mutableStateOf<Group?>(null)

    /** Short messages for the on-screen banner ("Divya joined"); the Long makes repeats show again */
    val notice = mutableStateOf<Pair<Long, String>?>(null)

    /** The SyncPlay menu is open; the item is the title it was opened for (null in the player) */
    val sheet = mutableStateOf<SheetRequest?>(null)
    data class SheetRequest(val item: Item?)

    /** A title the group started while no player was open: the main screen opens the player for it */
    val pendingStart = mutableStateOf<Pair<String, Long>?>(null)

    val inGroup get() = group.value != null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val main = Handler(Looper.getMainLooper())
    private val api get() = NovaApp.instance.api
    private val prefs get() = NovaApp.instance.prefs

    // ---- connection ----
    private var ws: WebSocket? = null
    private var wsOpen = false
    private var keepAliveJob: Job? = null
    private var timeJob: Job? = null
    private var reconnects = 0

    /** Server clock minus ours, in ms (so we act at the same moment as everyone else) */
    @Volatile private var offsetMs = 0L
    private var rttMs = 100L
    fun serverNow() = System.currentTimeMillis() + offsetMs

    // ---- the group's queue ----
    private var playlist: List<Pair<String, String>> = emptyList()   // (itemId, playlistItemId)
    private var playingIndex = -1
    private var playlistItemId: String? = null
    private var itemId: String? = null

    // ---- player link ----
    private var target: SyncTarget? = null
    private var awaitingReady = false
    private var reportedBuffering = false
    private var suppressBufferingUntil = 0L
    private var lastCommandKey: String? = null
    private var scheduled: Runnable? = null
    /** While playing: the server's (time, position) to check we haven't drifted */
    private var anchor: Pair<Long, Long>? = null
    private var lastCorrection = 0L
    private var driftJob: Job? = null

    private fun say(msg: String) { notice.value = System.nanoTime() to msg }

    // =============================================================== groups

    suspend fun listGroups(): List<ListedGroup> {
        val (code, body) = runCatching { api.raw("GET", "/SyncPlay/List") }.getOrElse { return emptyList() }
        if (code != 200) return emptyList()
        val a = runCatching { JSONArray(body) }.getOrNull() ?: return emptyList()
        return (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            ListedGroup(o.optString("GroupId"), o.optString("GroupName"), o.optJSONArray("Participants").strings(), o.optString("State"))
        }
    }

    /** Create a group and (optionally) start a title in it. Returns an error message, or null when it worked. */
    suspend fun create(startWith: Item?): String? {
        if (!connect()) return "Couldn't connect to the server for SyncPlay."
        val name = "${prefs.userName ?: "PHOENIXA"}'s watch party"
        val (code, _) = runCatching { api.raw("POST", "/SyncPlay/New", JSONObject().put("GroupName", name)) }.getOrElse { -1 to "" }
        if (code !in 200..299) return deniedText(code, "create")
        if (withTimeoutOrNull(6000) { while (group.value == null) delay(100) } == null) return "The server didn't answer. Try again."
        if (startWith != null) playForEveryone(startWith)
        return null
    }

    suspend fun join(id: String): String? {
        if (!connect()) return "Couldn't connect to the server for SyncPlay."
        val (code, _) = runCatching { api.raw("POST", "/SyncPlay/Join", JSONObject().put("GroupId", id)) }.getOrElse { -1 to "" }
        if (code !in 200..299) return deniedText(code, "join")
        if (withTimeoutOrNull(6000) { while (group.value == null) delay(100) } == null) return "The server didn't answer. Try again."
        return null
    }

    fun leave() {
        scope.launch { runCatching { api.raw("POST", "/SyncPlay/Leave") } }
        left("You left SyncPlay")
    }

    private fun deniedText(code: Int, what: String) = when (code) {
        401, 403 -> "Your account isn't allowed to $what SyncPlay groups. The server owner can allow it under Dashboard → Users → SyncPlay."
        else -> "Couldn't $what the group (error $code)."
    }

    private fun left(msg: String?) {
        group.value = null
        playlist = emptyList(); playingIndex = -1; playlistItemId = null; itemId = null
        anchor = null; awaitingReady = false; reportedBuffering = false
        scheduled?.let { main.removeCallbacks(it) }; scheduled = null
        driftJob?.cancel(); timeJob?.cancel(); keepAliveJob?.cancel()
        runCatching { ws?.close(1000, "bye") }
        ws = null; wsOpen = false
        msg?.let { say(it) }
    }

    /** Play a title for everyone in the group (series/season: the next episode, followed by the rest) */
    suspend fun playForEveryone(item: Item, fromStart: Boolean = false) {
        val (ids, startTicks) = runCatching { queueFor(item, fromStart) }.getOrElse { return say("Couldn't start that title") }
        if (ids.isEmpty()) return say("Nothing to play")
        val body = JSONObject()
            .put("PlayingQueue", JSONArray(ids))
            .put("PlayingItemPosition", 0)
            .put("StartPositionTicks", startTicks)
        val (code, _) = runCatching { api.raw("POST", "/SyncPlay/SetNewQueue", body) }.getOrElse { -1 to "" }
        if (code !in 200..299) say("Couldn't start it for the group")
    }

    private suspend fun queueFor(item: Item, fromStart: Boolean): Pair<List<String>, Long> {
        val start: Item = when (item.type) {
            "Series" -> api.nextUpForSeries(item.id)
                ?: api.episodes(item.id).let { eps -> eps.firstOrNull { !it.played } ?: eps.firstOrNull() } ?: return emptyList<String>() to 0L
            "Season" -> api.episodes(item.seriesId ?: "", item.id).let { eps -> eps.firstOrNull { !it.played } ?: eps.firstOrNull() }
                ?: return emptyList<String>() to 0L
            else -> item
        }
        val ticks = if (fromStart || start.played) 0L else start.playbackPositionTicks
        if (!start.isEpisode || start.seriesId == null) return listOf(start.id) to ticks
        // Episodes: this one and the ones after it, so the group carries on to the next episode together
        val all = api.episodes(start.seriesId)
        val eps = if (start.parentIndexNumber == 0) all else all.filter { it.parentIndexNumber != 0 }
        val i = eps.indexOfFirst { it.id == start.id }
        val ids = if (i < 0) listOf(start.id) else eps.drop(i).take(30).map { it.id }
        return ids to ticks
    }

    // =============================================================== connection

    private suspend fun connect(): Boolean {
        if (ws != null && wsOpen) return true
        openSocket()
        return withTimeoutOrNull(8000) { while (!wsOpen) delay(100) } != null
    }

    private fun openSocket() {
        val server = prefs.server.trimEnd('/')
        val token = prefs.token ?: return
        val url = server.replaceFirst("https://", "wss://").replaceFirst("http://", "ws://") +
            "/socket?api_key=$token&deviceId=${prefs.deviceId}"
        val client = api.http.newBuilder().pingInterval(25, TimeUnit.SECONDS).readTimeout(0, TimeUnit.MILLISECONDS).build()
        val req = Request.Builder().url(url).header("Authorization", api.authHeader()).build()
        ws = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                main.post {
                    if (ws !== webSocket) return@post
                    wsOpen = true; reconnects = 0
                    startClockSync()
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                main.post { if (ws === webSocket) runCatching { onSocketMessage(text) } }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                main.post { if (ws === webSocket) dropped() }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                main.post { if (ws === webSocket) dropped() }
            }
        })
    }

    /** Connection lost: reconnect while we're in a group (the server keeps us in it) */
    private fun dropped() {
        wsOpen = false; ws = null
        keepAliveJob?.cancel()
        if (!inGroup) return
        if (reconnects >= 5) { left("SyncPlay connection lost"); return }
        reconnects++
        say("Reconnecting to SyncPlay…")
        scope.launch { delay(2000L * reconnects); if (inGroup && ws == null) openSocket() }
    }

    private fun send(type: String, data: Any? = null) {
        val o = JSONObject().put("MessageType", type)
        if (data != null) o.put("Data", data)
        runCatching { ws?.send(o.toString()) }
    }

    private fun onSocketMessage(text: String) {
        val msg = JSONObject(text)
        when (msg.optString("MessageType")) {
            "ForceKeepAlive" -> {
                val every = (msg.optLong("Data", 60).coerceAtLeast(10) * 1000) / 2
                keepAliveJob?.cancel()
                keepAliveJob = scope.launch { while (isActive) { send("KeepAlive"); delay(every) } }
            }
            "SyncPlayGroupUpdate" -> onGroupUpdate(msg.optJSONObject("Data") ?: return)
            "SyncPlayCommand" -> onCommand(msg.optJSONObject("Data") ?: return)
        }
    }

    /** Works out how far our clock is from the server's (a few quick round trips; the fastest wins) */
    private fun startClockSync() {
        timeJob?.cancel()
        timeJob = scope.launch {
            while (isActive) {
                var best: Pair<Long, Long>? = null   // (rtt, offset)
                repeat(4) {
                    val t0 = System.currentTimeMillis()
                    val (code, body) = runCatching { api.raw("GET", "/GetUtcTime") }.getOrElse { -1 to "" }
                    val t3 = System.currentTimeMillis()
                    if (code == 200) runCatching {
                        val o = JSONObject(body)
                        val t1 = Instant.parse(o.getString("RequestReceptionTime")).toEpochMilli()
                        val t2 = Instant.parse(o.getString("ResponseTransmissionTime")).toEpochMilli()
                        val rtt = (t3 - t0) - (t2 - t1)
                        val off = ((t1 - t0) + (t2 - t3)) / 2
                        if (best == null || rtt < best!!.first) best = rtt to off
                    }
                    delay(150)
                }
                best?.let { (rtt, off) ->
                    offsetMs = off; rttMs = rtt.coerceAtLeast(1)
                    if (inGroup) runCatching { api.raw("POST", "/SyncPlay/Ping", JSONObject().put("Ping", rttMs)) }
                }
                delay(if (best == null) 10_000 else 60_000)
            }
        }
    }

    // =============================================================== from the server

    private fun onGroupUpdate(u: JSONObject) {
        val type = u.optString("Type")
        when (type) {
            "GroupJoined" -> {
                val d = u.optJSONObject("Data")
                group.value = Group(
                    d?.optString("GroupId") ?: u.optString("GroupId"),
                    d?.optString("GroupName") ?: "SyncPlay",
                    d?.optJSONArray("Participants").strings(),
                    d?.optString("State") ?: "Idle",
                )
                say("You joined ${group.value?.name}")
            }
            "UserJoined" -> {
                val who = u.optString("Data")
                group.value = group.value?.let { g -> g.copy(members = (g.members + who).distinct()) }
                if (who.isNotBlank()) say("$who joined")
            }
            "UserLeft" -> {
                val who = u.optString("Data")
                group.value = group.value?.let { g -> g.copy(members = g.members - who) }
                if (who.isNotBlank()) say("$who left")
            }
            "GroupLeft" -> left(null)
            "NotInGroup", "GroupDoesNotExist" -> left("The SyncPlay group has ended")
            "CreateGroupDenied", "JoinGroupDenied" -> say("Your account isn't allowed to use SyncPlay")
            "LibraryAccessDenied" -> say("Someone in the group can't see this title")
            "StateUpdate" -> {
                val st = u.optJSONObject("Data")?.optString("State") ?: return
                group.value = group.value?.copy(state = st)
                if (st == "Waiting") say("Waiting for everyone…")
            }
            "PlayQueue" -> onPlayQueue(u.optJSONObject("Data") ?: return)
        }
    }

    private fun onPlayQueue(d: JSONObject) {
        val a = d.optJSONArray("Playlist") ?: JSONArray()
        playlist = (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            o.optString("ItemId").ids() to o.optString("PlaylistItemId")
        }
        playingIndex = d.optInt("PlayingItemIndex", -1)
        val now = playlist.getOrNull(playingIndex)
        if (now == null) {
            playlistItemId = null; itemId = null
            target?.syncStop()
            return
        }
        val reason = d.optString("Reason")
        val changed = now.second != playlistItemId || reason == "NewPlaylist"
        playlistItemId = now.second
        itemId = now.first
        if (!changed) return
        val startMs = d.optLong("StartPositionTicks", 0) / 10_000
        anchor = null; lastCommandKey = null
        awaitingReady = true; reportedBuffering = false
        val t = target
        if (t != null) t.syncLoad(now.first, startMs)
        else pendingStart.value = now.first to startMs * 10_000
    }

    private fun onCommand(c: JSONObject) {
        val pid = c.optString("PlaylistItemId")
        if (playlistItemId != null && pid.isNotBlank() && pid != playlistItemId) return
        val key = c.optString("Command") + c.optString("When") + c.optLong("PositionTicks")
        if (key == lastCommandKey) return
        lastCommandKey = key
        val t = target ?: return
        if (t.syncItemId?.ids() != itemId) return
        val whenServer = runCatching { Instant.parse(c.optString("When")).toEpochMilli() }.getOrNull() ?: serverNow()
        val whenLocal = whenServer - offsetMs
        val posMs = c.optLong("PositionTicks") / 10_000
        scheduled?.let { main.removeCallbacks(it) }; scheduled = null
        val now = System.currentTimeMillis()

        when (c.optString("Command")) {
            "Unpause" -> {
                anchor = whenServer to posMs
                if (whenLocal > now) {
                    if (kotlin.math.abs(t.syncPositionMs - posMs) > 400) quietSeek(t, posMs)
                    val r = Runnable { t.syncPlay() }
                    scheduled = r
                    main.postDelayed(r, whenLocal - now)
                } else {
                    val expected = posMs + (now - whenLocal)
                    if (kotlin.math.abs(t.syncPositionMs - expected) > 400) quietSeek(t, expected)
                    t.syncPlay()
                }
                startDriftCheck()
            }
            "Pause" -> {
                anchor = null
                val r = Runnable {
                    t.syncPause()
                    if (kotlin.math.abs(t.syncPositionMs - posMs) > 400) quietSeek(t, posMs)
                }
                if (whenLocal > now) { scheduled = r; main.postDelayed(r, whenLocal - now) } else r.run()
            }
            "Seek" -> {
                anchor = null
                t.syncPause()
                t.syncSeek(posMs)
                awaitingReady = true
                // Already there (no loading needed): say we're ready straight away
                main.postDelayed({ if (awaitingReady && target?.syncReady == true) sendReady() }, 600)
            }
            "Stop" -> {
                anchor = null
                t.syncStop()
            }
        }
    }

    /** A small correction seek that shouldn't make the whole group wait */
    private fun quietSeek(t: SyncTarget, ms: Long) {
        suppressBufferingUntil = System.currentTimeMillis() + 3000
        t.syncSeek(ms.coerceAtLeast(0))
    }

    /** Every few seconds while playing: if we've drifted more than a second from the group, jump back in line */
    private fun startDriftCheck() {
        driftJob?.cancel()
        driftJob = scope.launch {
            while (isActive && inGroup) {
                delay(2500)
                val t = target ?: continue
                val (whenServer, pos) = anchor ?: continue
                if (!t.syncPlayWhenReady || !t.syncReady) continue
                val expected = pos + (serverNow() - whenServer)
                val diff = t.syncPositionMs - expected
                val now = System.currentTimeMillis()
                if (kotlin.math.abs(diff) > 1200 && now - lastCorrection > 6000) {
                    lastCorrection = now
                    quietSeek(t, expected)
                }
            }
        }
    }

    // =============================================================== from our player

    fun attach(t: SyncTarget) { target = t }
    fun detach(t: SyncTarget) {
        if (target === t) {
            target = null
            scheduled?.let { main.removeCallbacks(it) }; scheduled = null
        }
    }

    /** The player is loading / ready: tell the group, so nobody runs ahead */
    fun onPlayerState(t: SyncTarget, buffering: Boolean) {
        if (!inGroup || target !== t || playlistItemId == null || t.syncItemId?.ids() != itemId) return
        if (buffering) {
            if (System.currentTimeMillis() < suppressBufferingUntil || reportedBuffering) return
            reportedBuffering = true
            report("/SyncPlay/Buffering", t)
        } else if (t.syncReady && (reportedBuffering || awaitingReady)) {
            sendReady()
        }
    }

    private fun sendReady() {
        val t = target ?: return
        reportedBuffering = false; awaitingReady = false
        report("/SyncPlay/Ready", t)
    }

    private fun report(path: String, t: SyncTarget) {
        val body = JSONObject()
            .put("When", Instant.ofEpochMilli(serverNow()).toString())
            .put("PositionTicks", t.syncPositionMs * 10_000)
            .put("IsPlaying", t.syncPlayWhenReady)
            .put("PlaylistItemId", playlistItemId)
        scope.launch { runCatching { api.raw("POST", path, body) } }
    }

    fun requestPause() = post("/SyncPlay/Pause")
    fun requestUnpause() = post("/SyncPlay/Unpause")
    fun requestSeek(ms: Long) = post("/SyncPlay/Seek", JSONObject().put("PositionTicks", ms.coerceAtLeast(0) * 10_000))

    /** What the group is watching right now (null when nothing) */
    val currentItemId: String? get() = itemId
    val playerOpen get() = target != null

    /**
     * Back to the group's video after closing the player: opens it where the group is now and
     * says we're ready; the server then tells us exactly where to be.
     */
    fun rejoinPlayer(context: android.content.Context): Boolean {
        val id = itemId ?: return false
        if (target != null) return true
        val posMs = anchor?.let { (w, p) -> p + (serverNow() - w) } ?: 0L
        awaitingReady = true; reportedBuffering = false; lastCommandKey = null
        org.phoenicx.nova.player.PlayerActivity.start(context, id, posMs.coerceAtLeast(0) * 10_000, sync = true)
        return true
    }

    val hasNext get() = playingIndex in 0 until playlist.lastIndex
    fun requestNext() {
        val pid = playlistItemId ?: return
        post("/SyncPlay/NextItem", JSONObject().put("PlaylistItemId", pid))
    }

    /** Our copy reached the end: move the group on to the next episode, if there is one */
    fun onEnded(): Boolean {
        if (!hasNext) return false
        requestNext()
        return true
    }

    private fun post(path: String, body: JSONObject? = null) {
        scope.launch { runCatching { api.raw("POST", path, body) } }
    }

    // =============================================================== helpers

    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optString(it).takeIf { s -> s.isNotBlank() } }

    /** Ids in one form (some servers write them with dashes) */
    private fun String.ids() = replace("-", "").lowercase()
}
