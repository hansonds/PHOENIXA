package org.phoenicx.nova.data

import kotlinx.coroutines.delay
import org.json.JSONArray
import org.phoenicx.nova.NovaApp

/**
 * Subtitles from the internet, through the server's subtitle plugin (OpenSubtitles).
 * Used by the player and by the movie / episode page.
 */
object RemoteSubs {
    data class Result(val id: String, val name: String, val detail: String)

    /** Languages offered (ISO 639-2 codes, as Jellyfin wants them) */
    val languages = listOf(
        "mal" to "Malayalam", "eng" to "English", "tam" to "Tamil", "hin" to "Hindi", "tel" to "Telugu",
        "kan" to "Kannada", "ara" to "Arabic", "ger" to "German", "fre" to "French", "spa" to "Spanish",
    )

    const val NOT_ALLOWED =
        "Your account isn't allowed to download subtitles. The server owner can turn on \"Allow subtitle management\" for you (Dashboard → Users)."

    /** Results (best first), or an error message */
    suspend fun search(itemId: String, lang3: String): Pair<List<Result>, String?> {
        val api = NovaApp.instance.api
        val (code, body) = runCatching {
            api.raw("GET", "/Items/$itemId/RemoteSearch/Subtitles/$lang3?isPerfectMatch=false")
        }.getOrElse { e -> return emptyList<Result>() to (e.message ?: "Couldn't reach the server") }
        if (code == 401 || code == 403) return emptyList<Result>() to NOT_ALLOWED
        if (code !in 200..299) return emptyList<Result>() to "The server couldn't search (error $code)"
        val a = runCatching { JSONArray(body) }.getOrNull() ?: return emptyList<Result>() to null
        val list = (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("Id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val hash = o.optBoolean("IsHashMatch", false)
            val detail = listOfNotNull(
                o.optString("ProviderName").takeIf { it.isNotBlank() },
                o.optString("Format").takeIf { it.isNotBlank() }?.uppercase(),
                o.optInt("DownloadCount", 0).takeIf { it > 0 }?.let { "⬇ $it" },
                if (hash) "Exact match for this file" else null,
                if (o.optBoolean("HearingImpaired", false)) "SDH" else null,
            ).joinToString(" · ")
            Result(id, o.optString("Name").ifBlank { "Subtitle" }, detail) to (if (hash) 1_000_000 else 0) + o.optInt("DownloadCount", 0)
        }.sortedByDescending { it.second }.map { it.first }
        return list to null
    }

    /**
     * Has the server download it and waits until it's part of the video.
     * Returns (the new subtitle's stream index, null) or (null, error message).
     */
    suspend fun download(itemId: String, mediaSourceId: String?, sub: Result): Pair<MediaStreamInfo?, String?> {
        val api = NovaApp.instance.api
        val before = runCatching { api.item(itemId) }.getOrNull()
            ?.mediaSources?.flatMap { it.subtitleStreams }?.map { it.index }?.toSet() ?: emptySet()
        val (code, _) = runCatching {
            api.raw("POST", "/Items/$itemId/RemoteSearch/Subtitles/${android.net.Uri.encode(sub.id)}")
        }.getOrElse { e -> return null to (e.message ?: "Couldn't reach the server") }
        if (code == 401 || code == 403) return null to NOT_ALLOWED
        if (code !in 200..299) return null to "The server couldn't download it (error $code)"
        // The server adds it to the video in the background: wait for it to show up (up to ~20 s)
        repeat(20) {
            delay(1000)
            val fresh = runCatching { api.item(itemId) }.getOrNull() ?: return@repeat
            val src = fresh.mediaSources.firstOrNull { it.id == mediaSourceId } ?: fresh.mediaSources.firstOrNull()
            val added = src?.subtitleStreams?.filter { it.index !in before }?.maxByOrNull { it.index }
            if (added != null) return added to null
        }
        return null to "Downloaded, but the server is still adding it. Try again in a minute."
    }
}
