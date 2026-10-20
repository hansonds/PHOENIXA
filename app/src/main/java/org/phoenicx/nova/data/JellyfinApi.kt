package org.phoenicx.nova.data

import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class ApiException(message: String, val code: Int = 0) : IOException(message)

data class Library(val id: String, val name: String, val collectionType: String?, val primaryTag: String?)

data class PlaybackSource(
    val url: String,
    val mediaSourceId: String,
    val playSessionId: String?,
    val isTranscoding: Boolean,
    val externalSubs: List<ExternalSub>,
    /** Jellyfin indexes of audio tracks inside the file, in file order */
    val embeddedAudio: List<Int> = emptyList(),
    /** Jellyfin indexes of subtitle tracks inside the file, in file order */
    val embeddedSubs: List<Int> = emptyList(),
)

data class ExternalSub(val url: String, val language: String?, val title: String, val mime: String, val index: Int = -1)

class JellyfinApi(private val prefs: Prefs) {

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = "application/json".toMediaType()

    val server: String get() = prefs.server.trimEnd('/')
    val userId: String get() = prefs.userId ?: ""
    val token: String get() = prefs.token ?: ""
    val isLoggedIn: Boolean get() = !prefs.token.isNullOrEmpty() && !prefs.userId.isNullOrEmpty()

    fun authHeader(tok: String? = prefs.token): String {
        val device = (Build.MODEL ?: "Android TV").replace("\"", "")
        val base = "MediaBrowser Client=\"PHOENIXA\", Device=\"$device\", DeviceId=\"${prefs.deviceId}\", Version=\"1.0.0\""
        return if (tok.isNullOrEmpty()) base else "$base, Token=\"$tok\""
    }

    private suspend fun call(
        method: String,
        path: String,
        body: JSONObject? = null,
        baseOverride: String? = null,
        tokenOverride: String? = prefs.token,
    ): String = withContext(Dispatchers.IO) {
        val url = (baseOverride ?: server) + path
        val rb = when {
            body != null -> body.toString().toRequestBody(json)
            method == "POST" || method == "DELETE" -> "".toRequestBody(json)
            else -> null
        }
        val req = Request.Builder()
            .url(url)
            .header("Authorization", authHeader(tokenOverride))
            .header("X-Emby-Authorization", authHeader(tokenOverride))
            .header("Accept", "application/json")
            .method(method, if (method == "GET") null else rb)
            .build()
        http.newCall(req).execute().use { resp ->
            val text = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                val msg = when (resp.code) {
                    401 -> "Wrong username or password"
                    404 -> "Not found"
                    else -> "Server error ${resp.code}"
                }
                throw ApiException(msg, resp.code)
            }
            text
        }
    }

    private suspend fun getObj(path: String) = JSONObject(call("GET", path))
    private suspend fun getItems(path: String): List<Item> {
        val txt = call("GET", path).trim()
        return if (txt.startsWith("[")) JSONArray(txt).items()
        else JSONObject(txt).optJSONArray("Items").items()
    }

    private fun JSONArray?.items(): List<Item> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it)?.let(Item::parse) }

    private fun enc(s: String) = Uri.encode(s)

    // ---------- Auth ----------

    fun normalizeServer(input: String): String {
        var s = input.trim().trimEnd('/')
        if (!s.startsWith("http://") && !s.startsWith("https://")) s = "https://$s"
        return s
    }

    suspend fun ping(base: String): String {
        val o = JSONObject(call("GET", "/System/Info/Public", baseOverride = base, tokenOverride = null))
        return o.optString("ServerName", "Jellyfin")
    }

    suspend fun login(base: String, user: String, pass: String) {
        val body = JSONObject().put("Username", user).put("Pw", pass)
        val o = JSONObject(call("POST", "/Users/AuthenticateByName", body, base, null))
        saveAuth(base, o)
    }

    private fun saveAuth(base: String, o: JSONObject) {
        prefs.server = base
        prefs.token = o.optString("AccessToken")
        val u = o.optJSONObject("User")
        prefs.userId = u?.optString("Id")
        prefs.userName = u?.optString("Name")
    }

    /** Returns (secret, code) */
    suspend fun quickConnectInitiate(base: String): Pair<String, String> {
        val o = JSONObject(call("POST", "/QuickConnect/Initiate", null, base, null))
        return o.optString("Secret") to o.optString("Code")
    }

    suspend fun quickConnectCheck(base: String, secret: String): Boolean {
        val o = JSONObject(call("GET", "/QuickConnect/Connect?Secret=${enc(secret)}", null, base, null))
        return o.optBoolean("Authenticated")
    }

    suspend fun quickConnectFinish(base: String, secret: String) {
        val o = JSONObject(call("POST", "/Users/AuthenticateWithQuickConnect", JSONObject().put("Secret", secret), base, null))
        saveAuth(base, o)
    }

    // ---------- Images ----------

    fun image(itemId: String?, type: String, tag: String?, maxWidth: Int): String? {
        if (itemId == null) return null
        val t = if (tag != null) "&tag=$tag" else ""
        return "$server/Items/$itemId/Images/$type?maxWidth=$maxWidth&quality=90$t"
    }

    fun poster(item: Item, w: Int = 400): String? = when {
        item.primaryTag != null && !item.isEpisode -> image(item.id, "Primary", item.primaryTag, w)
        item.isEpisode && item.seriesPrimaryTag != null -> image(item.seriesId, "Primary", item.seriesPrimaryTag, w)
        item.primaryTag != null -> image(item.id, "Primary", item.primaryTag, w)
        else -> null
    }

    fun backdrop(item: Item, w: Int = 1920): String? = when {
        item.backdropTag != null -> image(item.backdropItemId ?: item.id, "Backdrop", item.backdropTag, w)
        else -> null
    }

    /** Landscape still: episode screenshot, else thumb, else backdrop */
    fun landscape(item: Item, w: Int = 640): String? = when {
        item.isEpisode && item.primaryTag != null -> image(item.id, "Primary", item.primaryTag, w)
        item.thumbTag != null -> image(item.thumbItemId ?: item.id, "Thumb", item.thumbTag, w)
        item.backdropTag != null -> backdrop(item, w)
        else -> poster(item, w)
    }

    fun logo(item: Item, w: Int = 800): String? =
        if (item.logoTag != null) image(item.logoItemId ?: item.id, "Logo", item.logoTag, w) else null

    fun personImage(p: Person, w: Int = 240): String? =
        if (p.imageTag != null) image(p.id, "Primary", p.imageTag, w) else null

    fun userImage(): String = "$server/Users/$userId/Images/Primary?maxWidth=120&quality=90"

    // ---------- Browse ----------

    private val fields = "Overview,Genres,PrimaryImageAspectRatio,ProductionYear,CommunityRating,OfficialRating,ChildCount,MediaStreams"
    private val imgParams = "&EnableImageTypes=Primary,Backdrop,Logo,Thumb&ImageTypeLimit=1"

    suspend fun libraries(): List<Library> {
        val o = getObj("/Users/$userId/Views")
        val arr = o.optJSONArray("Items") ?: return emptyList()
        return (0 until arr.length()).mapNotNull {
            val x = arr.optJSONObject(it) ?: return@mapNotNull null
            Library(x.optString("Id"), x.optString("Name"), x.optStringOrNull("CollectionType"),
                x.optJSONObject("ImageTags")?.optStringOrNull("Primary"))
        }
    }

    suspend fun featured(limit: Int = 10): List<Item> = getItems(
        "/Users/$userId/Items?IncludeItemTypes=Movie,Series&Recursive=true&SortBy=Random&Limit=$limit" +
            "&ImageTypes=Backdrop&HasOverview=true&Fields=$fields$imgParams&IsPlayed=false"
    ).ifEmpty {
        getItems("/Users/$userId/Items?IncludeItemTypes=Movie,Series&Recursive=true&SortBy=Random&Limit=$limit" +
            "&ImageTypes=Backdrop&Fields=$fields$imgParams")
    }

    suspend fun resume(): List<Item> = getItems(
        "/Users/$userId/Items/Resume?Limit=20&MediaTypes=Video&Fields=$fields$imgParams"
    )

    suspend fun nextUp(): List<Item> = getItems(
        "/Shows/NextUp?UserId=$userId&Limit=20&Fields=$fields$imgParams&EnableResumable=false"
    )

    suspend fun nextUpForSeries(seriesId: String): Item? = getItems(
        "/Shows/NextUp?UserId=$userId&SeriesId=$seriesId&Limit=1&Fields=$fields$imgParams&EnableResumable=true"
    ).firstOrNull()

    suspend fun latest(parentId: String): List<Item> = getItems(
        "/Users/$userId/Items/Latest?ParentId=$parentId&Limit=24&Fields=$fields$imgParams"
    )

    suspend fun favorites(types: String = "Movie,Series", limit: Int = 40): List<Item> = getItems(
        "/Users/$userId/Items?Filters=IsFavorite&IncludeItemTypes=$types&Recursive=true&SortBy=SortName&Limit=$limit&Fields=$fields$imgParams"
    )

    suspend fun item(id: String): Item = Item.parse(
        getObj("/Users/$userId/Items/$id?Fields=$fields,People,Taglines,Studios,MediaSources")
    )

    suspend fun seasons(seriesId: String): List<Item> = getItems(
        "/Shows/$seriesId/Seasons?UserId=$userId&Fields=$fields$imgParams"
    )

    suspend fun episodes(seriesId: String, seasonId: String? = null): List<Item> = getItems(
        "/Shows/$seriesId/Episodes?UserId=$userId&Fields=$fields$imgParams" + (seasonId?.let { "&SeasonId=$it" } ?: "")
    )

    suspend fun similar(id: String): List<Item> = getItems(
        "/Items/$id/Similar?UserId=$userId&Limit=16&Fields=$fields$imgParams"
    )

    suspend fun libraryItems(lib: Library, sortBy: String, sortOrder: String, start: Int, limit: Int): Pair<List<Item>, Int> {
        val types = when (lib.collectionType) {
            "movies" -> "&IncludeItemTypes=Movie"
            "tvshows" -> "&IncludeItemTypes=Series"
            "boxsets" -> "&IncludeItemTypes=BoxSet"
            else -> ""
        }
        val o = getObj("/Users/$userId/Items?ParentId=${lib.id}&Recursive=true$types&SortBy=$sortBy&SortOrder=$sortOrder" +
            "&StartIndex=$start&Limit=$limit&Fields=$fields$imgParams")
        val arr = o.optJSONArray("Items")
        val list = if (arr == null) emptyList() else (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(Item::parse) }
        return list to o.optInt("TotalRecordCount", list.size)
    }

    suspend fun search(term: String): List<Item> = getItems(
        "/Users/$userId/Items?SearchTerm=${enc(term)}&Recursive=true&IncludeItemTypes=Movie,Series,Episode&Limit=60&Fields=$fields$imgParams"
    )

    suspend fun setFavorite(id: String, fav: Boolean) {
        call(if (fav) "POST" else "DELETE", "/Users/$userId/FavoriteItems/$id")
    }

    suspend fun setPlayed(id: String, played: Boolean) {
        call(if (played) "POST" else "DELETE", "/Users/$userId/PlayedItems/$id")
    }

    // ---------- Playback ----------

    suspend fun playbackInfo(itemId: String, startTicks: Long, profile: JSONObject, allowDirect: Boolean, forceDirect: Boolean = false, sel: PlaySelection? = null): PlaybackSource {
        val maxBitrate = if (prefs.maxBitrateMbps > 0) prefs.maxBitrateMbps * 1_000_000 else 200_000_000
        val body = JSONObject()
            .put("UserId", userId)
            .put("StartTimeTicks", startTicks)
            .put("MaxStreamingBitrate", maxBitrate)
            .put("EnableDirectPlay", allowDirect)
            .put("EnableDirectStream", allowDirect)
            .put("EnableTranscoding", !forceDirect)
            .put("AutoOpenLiveStream", true)
            .put("DeviceProfile", profile)
            .apply {
                sel?.mediaSourceId?.let { put("MediaSourceId", it) }
                sel?.audioIndex?.let { put("AudioStreamIndex", it) }
                sel?.subtitleIndex?.let { if (it >= 0) put("SubtitleStreamIndex", it) }
            }
        val o = JSONObject(call("POST", "/Items/$itemId/PlaybackInfo?UserId=$userId", body))
        val playSessionId = o.optStringOrNull("PlaySessionId")
        val sources = o.optJSONArray("MediaSources") ?: throw ApiException("No playable media")
        val ms = (0 until sources.length()).mapNotNull { sources.optJSONObject(it) }
            .let { all -> all.firstOrNull { it.optString("Id") == sel?.mediaSourceId } ?: all.firstOrNull() }
            ?: throw ApiException("No playable media")
        val msId = ms.optString("Id")
        val container = ms.optString("Container")

        val directOk = forceDirect || allowDirect && (ms.optBoolean("SupportsDirectPlay") || ms.optBoolean("SupportsDirectStream"))
        val transcodeUrl = ms.optStringOrNull("TranscodingUrl")

        val url: String
        val transcoding: Boolean
        if (directOk || transcodeUrl == null) {
            url = "$server/Videos/$itemId/stream?static=true&MediaSourceId=$msId&ApiKey=$token&api_key=$token" +
                (if (playSessionId != null) "&PlaySessionId=$playSessionId" else "") +
                (if (container.isNotEmpty()) "&Container=$container" else "")
            transcoding = false
        } else {
            val sep = if (transcodeUrl.contains("?")) "&" else "?"
            url = server + transcodeUrl + (if (transcodeUrl.contains("ApiKey", ignoreCase = true) || transcodeUrl.contains("api_key")) "" else "${sep}ApiKey=$token")
            transcoding = true
        }

        val subs = mutableListOf<ExternalSub>()
        val streams = ms.optJSONArray("MediaStreams")
        if (streams != null) {
            for (i in 0 until streams.length()) {
                val s = streams.optJSONObject(i) ?: continue
                if (s.optString("Type") != "Subtitle") continue
                val isExternal = s.optBoolean("IsExternal")
                val textBased = s.optBoolean("IsTextSubtitleStream")
                // Embedded subs travel inside the file when direct playing; otherwise fetch as WebVTT
                if (!textBased) continue
                if (!isExternal && !transcoding) continue
                val idx = s.optInt("Index")
                subs += ExternalSub(
                    url = "$server/Videos/$itemId/$msId/Subtitles/$idx/0/Stream.vtt?ApiKey=$token&api_key=$token",
                    language = s.optStringOrNull("Language"),
                    title = s.optString("DisplayTitle", "Subtitle $idx"),
                    mime = "text/vtt",
                    index = idx,
                )
            }
        }
        val embeddedAudio = mutableListOf<Int>()
        val embeddedSubs = mutableListOf<Int>()
        if (streams != null) {
            for (i in 0 until streams.length()) {
                val st = streams.optJSONObject(i) ?: continue
                if (st.optBoolean("IsExternal")) continue
                when (st.optString("Type")) {
                    "Audio" -> embeddedAudio += st.optInt("Index")
                    "Subtitle" -> embeddedSubs += st.optInt("Index")
                }
            }
        }
        return PlaybackSource(url, msId, playSessionId, transcoding, subs, embeddedAudio, embeddedSubs)
    }

    private fun progressBody(itemId: String, src: PlaybackSource, ticks: Long, paused: Boolean) = JSONObject()
        .put("ItemId", itemId)
        .put("MediaSourceId", src.mediaSourceId)
        .put("PlaySessionId", src.playSessionId ?: JSONObject.NULL)
        .put("PositionTicks", ticks)
        .put("IsPaused", paused)
        .put("CanSeek", true)
        .put("PlayMethod", if (src.isTranscoding) "Transcode" else "DirectPlay")

    suspend fun reportStart(itemId: String, src: PlaybackSource, ticks: Long) =
        runCatching { call("POST", "/Sessions/Playing", progressBody(itemId, src, ticks, false)) }

    suspend fun reportProgress(itemId: String, src: PlaybackSource, ticks: Long, paused: Boolean) =
        runCatching { call("POST", "/Sessions/Playing/Progress", progressBody(itemId, src, ticks, paused)) }

    suspend fun reportStopped(itemId: String, src: PlaybackSource, ticks: Long) =
        runCatching { call("POST", "/Sessions/Playing/Stopped", progressBody(itemId, src, ticks, false)) }
}
