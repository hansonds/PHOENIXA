package org.phoenicx.nova.data

import org.json.JSONArray
import org.json.JSONObject

/*
 * "Search & Add" — talks to the Torvia plugin on the Jellyfin server (see SEARCH-API.md in the
 * Torvia-Jellyfin repo). The feature is only shown when the server has the plugin and the
 * signed-in user is allowed to use it.
 */

data class TorviaStatus(
    val enabled: Boolean,
    val isAdmin: Boolean,
    val tags: List<String>,
    val movieTag: String,
    val tvTag: String,
    val remainingToday: Int?,
    val dailyLimit: Int?,
    val buttonLabel: String,
)

data class TorviaTitle(
    val type: String,          // "movie" or "tv"
    val tmdbId: Int,
    val title: String,
    val year: Int?,
    val overview: String,
    val poster: String?,
    val backdrop: String?,
    val rating: Double?,
) {
    val isTv get() = type == "tv"
}

data class TorviaSeason(val season: Int, val name: String, val episodes: Int, val year: Int?)

data class TorviaDetails(
    val title: TorviaTitle,
    val seasons: List<TorviaSeason>,
    val jellyfinItemId: String?,
)

data class TorviaRelease(
    val hash: String,
    val name: String,
    val fileName: String,
    val sizeBytes: Long,
    val size: String,
    val seeders: Int,
    val speed: String,
    val quality: String,
    val isCam: Boolean,
    val hdr: String,
    val bitDepth: Int,
    val languages: List<String>,
    val kind: String,
    val label: String,
    val playsLabel: String,
    val packSize: String,
    val summary: String,
) {
    val isHdr get() = hdr.isNotBlank() && !hdr.equals("SDR", true)
    val isTenBit get() = bitDepth >= 10
}

data class TorviaReleases(
    val releases: List<TorviaRelease>,
    val jellyfinItemId: String?,
)

data class TorviaAdd(
    val id: String,
    val userName: String,
    val whenText: String,
    val type: String,
    val tmdbId: Int,
    val title: String,
    val year: Int?,
    val poster: String?,
    val label: String,
    val quality: String,
    val size: String,
    val tag: String,
    val canRemove: Boolean,
    val jellyfinItemId: String?,
)

data class TorviaUser(val id: String, val name: String, val count: Int)

data class TorviaAdds(val isAdmin: Boolean, val items: List<TorviaAdd>, val users: List<TorviaUser>)

/** Result of an add or a remove: shown to the user as is */
data class TorviaResult(val ok: Boolean, val message: String, val remainingToday: Int? = null)

class TorviaException(message: String) : Exception(message)

class TorviaApi(private val api: JellyfinApi) {

    private fun JSONObject.str(k: String) = if (isNull(k)) "" else optString(k, "")
    private fun JSONObject.strOrNull(k: String) = if (isNull(k)) null else optString(k).takeIf { it.isNotBlank() }
    private fun JSONObject.intOrNull(k: String) = if (isNull(k) || !has(k)) null else optInt(k)
    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList() else (0 until length()).map { optString(it) }.filter { it.isNotBlank() }
    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    /** Turns an error answer into a readable message */
    private fun fail(code: Int, body: String): Nothing {
        val msg = runCatching { JSONObject(body).let { it.strOrNull("error") ?: it.strOrNull("message") } }.getOrNull()
        throw TorviaException(
            msg ?: when (code) {
                403 -> "You're not allowed to use Search & Add on this server."
                404 -> "Not found."
                502, 503, 504 -> "The server couldn't reach TorBox right now. Please try again."
                else -> "Something went wrong (error $code)."
            }
        )
    }

    private suspend fun get(path: String): JSONObject {
        val (code, body) = api.raw("GET", path)
        if (code !in 200..299) fail(code, body)
        return JSONObject(body)
    }

    /** null when the plugin isn't installed on this server */
    suspend fun status(): TorviaStatus? {
        val (code, body) = runCatching { api.raw("GET", "/TorviaSearch/Status") }.getOrNull() ?: return null
        if (code != 200) return null
        val o = runCatching { JSONObject(body) }.getOrNull() ?: return null
        return TorviaStatus(
            enabled = o.optBoolean("enabled"),
            isAdmin = o.optBoolean("isAdmin"),
            tags = o.optJSONArray("tags").strings(),
            movieTag = o.str("movieTag"),
            tvTag = o.str("tvTag"),
            remainingToday = o.intOrNull("remainingToday"),
            dailyLimit = o.intOrNull("dailyLimit"),
            buttonLabel = o.strOrNull("buttonLabel") ?: "Search & Add",
        )
    }

    private fun title(o: JSONObject) = TorviaTitle(
        type = o.str("type"),
        tmdbId = o.optInt("tmdbId"),
        title = o.str("title"),
        year = o.intOrNull("year")?.takeIf { it > 0 },
        overview = o.str("overview"),
        poster = o.strOrNull("poster"),
        backdrop = o.strOrNull("backdrop"),
        rating = if (o.isNull("rating") || !o.has("rating")) null else o.optDouble("rating").takeIf { it > 0 },
    )

    suspend fun search(query: String): List<TorviaTitle> =
        get("/TorviaSearch/Search?query=${enc(query)}").optJSONArray("results").objects().map(::title)

    suspend fun details(type: String, tmdbId: Int): TorviaDetails {
        val o = get("/TorviaSearch/Title/$type/$tmdbId")
        return TorviaDetails(
            title = title(o.optJSONObject("title") ?: JSONObject()),
            seasons = o.optJSONArray("seasons").objects().map {
                TorviaSeason(it.optInt("season"), it.str("name"), it.optInt("episodes"), it.intOrNull("year"))
            },
            jellyfinItemId = o.strOrNull("jellyfinItemId"),
        )
    }

    /** All cached releases (filters are applied on the device, like the web page does) */
    suspend fun releases(type: String, tmdbId: Int, season: Int? = null, episode: Int? = null, wholeSeason: Boolean = false): TorviaReleases {
        val q = StringBuilder("/TorviaSearch/Releases?type=$type&tmdbId=$tmdbId&quality=all")
        if (season != null) q.append("&season=$season")
        if (wholeSeason) q.append("&wholeSeason=true") else if (episode != null) q.append("&episode=$episode")
        val o = get(q.toString())
        return TorviaReleases(
            releases = o.optJSONArray("releases").objects().map {
                TorviaRelease(
                    hash = it.str("hash"), name = it.str("name"), fileName = it.str("fileName"),
                    sizeBytes = it.optLong("sizeBytes"), size = it.str("size"),
                    seeders = it.optInt("seeders"), speed = it.str("speed"), quality = it.str("quality"),
                    isCam = it.optBoolean("isCam"), hdr = it.str("hdr"), bitDepth = it.optInt("bitDepth"),
                    languages = it.optJSONArray("languages").strings(),
                    kind = it.str("kind"), label = it.str("label"), playsLabel = it.str("playsLabel"),
                    packSize = it.str("packSize"), summary = it.str("summary"),
                )
            },
            jellyfinItemId = o.strOrNull("jellyfinItemId"),
        )
    }

    suspend fun add(hash: String, tag: String?): TorviaResult {
        val body = JSONObject().put("hash", hash)
        if (!tag.isNullOrBlank()) body.put("tag", tag)
        val (code, text) = api.raw("POST", "/TorviaSearch/Add", body)
        val o = runCatching { JSONObject(text) }.getOrNull()
        if (code in 200..299) {
            return TorviaResult(true, o?.strOrNull("message") ?: "Added.", o?.intOrNull("remainingToday"))
        }
        if (code == 400 && o != null) return TorviaResult(false, o.strOrNull("message") ?: o.strOrNull("error") ?: "Couldn't add it.")
        fail(code, text)
    }

    /** The Jellyfin item id once Torvia has synced the title, otherwise null */
    suspend fun inLibrary(type: String, tmdbId: Int): String? {
        val (code, text) = runCatching { api.raw("GET", "/TorviaSearch/InLibrary/$type/$tmdbId") }.getOrNull() ?: return null
        if (code != 200) return null
        return runCatching { JSONObject(text).strOrNull("itemId") }.getOrNull()
    }

    suspend fun adds(all: Boolean, userId: String? = null): TorviaAdds {
        val path = buildString {
            append("/TorviaSearch/Adds")
            if (all) { append("?all=true"); if (userId != null) append("&user=${enc(userId)}") }
        }
        val o = get(path)
        return TorviaAdds(
            isAdmin = o.optBoolean("isAdmin"),
            items = o.optJSONArray("items").objects().map {
                TorviaAdd(
                    id = it.str("id"), userName = it.str("userName"), whenText = it.str("when"),
                    type = it.str("type"), tmdbId = it.optInt("tmdbId"), title = it.str("title"),
                    year = it.intOrNull("year")?.takeIf { y -> y > 0 }, poster = it.strOrNull("poster"),
                    label = it.str("label"), quality = it.str("quality"), size = it.str("size"),
                    tag = it.str("tag"), canRemove = it.optBoolean("canRemove"),
                    jellyfinItemId = it.strOrNull("jellyfinItemId"),
                )
            },
            users = o.optJSONArray("users").objects().map { TorviaUser(it.str("id"), it.str("name"), it.optInt("count")) },
        )
    }

    suspend fun remove(id: String): TorviaResult {
        val (code, text) = api.raw("DELETE", "/TorviaSearch/Adds/${enc(id)}")
        val o = runCatching { JSONObject(text) }.getOrNull()
        if (code in 200..299) return TorviaResult(true, o?.strOrNull("message") ?: "Removed.")
        fail(code, text)
    }
}
