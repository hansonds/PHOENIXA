package org.phoenicx.nova.data

import org.json.JSONArray
import org.json.JSONObject

data class Person(
    val id: String,
    val name: String,
    val role: String,
    val type: String,
    val imageTag: String?,
)

data class MediaStreamInfo(
    val type: String,
    val codec: String,
    val displayTitle: String,
    val width: Int,
    val height: Int,
    val videoRange: String,
    val channels: Int,
)

data class Item(
    val id: String,
    val name: String,
    val type: String,
    val overview: String = "",
    val year: Int? = null,
    val communityRating: Double? = null,
    val criticRating: Int? = null,
    val officialRating: String? = null,
    val runTimeTicks: Long = 0,
    val genres: List<String> = emptyList(),
    val primaryTag: String? = null,
    val backdropTag: String? = null,
    val backdropItemId: String? = null,
    val logoTag: String? = null,
    val logoItemId: String? = null,
    val thumbTag: String? = null,
    val thumbItemId: String? = null,
    val seriesId: String? = null,
    val seriesName: String? = null,
    val seriesPrimaryTag: String? = null,
    val seasonId: String? = null,
    val indexNumber: Int? = null,
    val parentIndexNumber: Int? = null,
    val childCount: Int? = null,
    val playbackPositionTicks: Long = 0,
    val playedPercentage: Double? = null,
    val played: Boolean = false,
    val isFavorite: Boolean = false,
    val unplayedCount: Int? = null,
    val collectionType: String? = null,
    val people: List<Person> = emptyList(),
    val taglines: List<String> = emptyList(),
    val studios: List<String> = emptyList(),
    val streams: List<MediaStreamInfo> = emptyList(),
    val premiereDate: String? = null,
) {
    val isSeries get() = type == "Series"
    val isEpisode get() = type == "Episode"
    val isFolderLike get() = type == "Series" || type == "Season" || type == "BoxSet" || type == "CollectionFolder"

    val episodeLabel: String?
        get() = if (isEpisode && parentIndexNumber != null && indexNumber != null)
            "S${parentIndexNumber}:E${indexNumber}" else null

    val runtimeMinutes: Int get() = (runTimeTicks / 600_000_000L).toInt()

    val progress: Float
        get() = when {
            playedPercentage != null -> (playedPercentage / 100.0).toFloat().coerceIn(0f, 1f)
            runTimeTicks > 0 && playbackPositionTicks > 0 -> (playbackPositionTicks.toDouble() / runTimeTicks).toFloat().coerceIn(0f, 1f)
            else -> 0f
        }

    val qualityBadges: List<String>
        get() {
            val v = streams.firstOrNull { it.type == "Video" } ?: return emptyList()
            val out = mutableListOf<String>()
            when {
                v.width >= 3200 || v.height >= 1700 -> out += "4K"
                v.width >= 1800 || v.height >= 1000 -> out += "HD"
            }
            if (v.videoRange.equals("HDR", true)) out += "HDR"
            val a = streams.firstOrNull { it.type == "Audio" }
            if (a != null && a.channels >= 6) out += "5.1"
            return out
        }

    companion object {
        fun parse(o: JSONObject): Item {
            val imageTags = o.optJSONObject("ImageTags")
            val backdrops = o.optJSONArray("BackdropImageTags")
            val parentBackdrops = o.optJSONArray("ParentBackdropImageTags")
            val userData = o.optJSONObject("UserData")

            var backdropTag: String? = backdrops?.optStringOrNull(0)
            var backdropItemId: String? = if (backdropTag != null) o.optString("Id") else null
            if (backdropTag == null && parentBackdrops != null && parentBackdrops.length() > 0) {
                backdropTag = parentBackdrops.optStringOrNull(0)
                backdropItemId = o.optStringOrNull("ParentBackdropItemId")
            }

            var logoTag = imageTags?.optStringOrNull("Logo")
            var logoItemId: String? = if (logoTag != null) o.optString("Id") else null
            if (logoTag == null && o.has("ParentLogoImageTag")) {
                logoTag = o.optStringOrNull("ParentLogoImageTag")
                logoItemId = o.optStringOrNull("ParentLogoItemId")
            }

            var thumbTag = imageTags?.optStringOrNull("Thumb")
            var thumbItemId: String? = if (thumbTag != null) o.optString("Id") else null
            if (thumbTag == null && o.has("ParentThumbImageTag")) {
                thumbTag = o.optStringOrNull("ParentThumbImageTag")
                thumbItemId = o.optStringOrNull("ParentThumbItemId")
            }

            val people = o.optJSONArray("People")?.let { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    val p = arr.optJSONObject(i) ?: return@mapNotNull null
                    Person(
                        id = p.optString("Id"),
                        name = p.optString("Name"),
                        role = p.optString("Role"),
                        type = p.optString("Type"),
                        imageTag = p.optStringOrNull("PrimaryImageTag"),
                    )
                }
            } ?: emptyList()

            val streams = o.optJSONArray("MediaStreams")?.let { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    val s = arr.optJSONObject(i) ?: return@mapNotNull null
                    MediaStreamInfo(
                        type = s.optString("Type"),
                        codec = s.optString("Codec"),
                        displayTitle = s.optString("DisplayTitle"),
                        width = s.optInt("Width"),
                        height = s.optInt("Height"),
                        videoRange = s.optString("VideoRange"),
                        channels = s.optInt("Channels"),
                    )
                }
            } ?: emptyList()

            return Item(
                id = o.optString("Id"),
                name = o.optString("Name"),
                type = o.optString("Type"),
                overview = o.optString("Overview", ""),
                year = if (o.has("ProductionYear")) o.optInt("ProductionYear") else null,
                communityRating = if (o.has("CommunityRating")) o.optDouble("CommunityRating") else null,
                criticRating = if (o.has("CriticRating")) o.optInt("CriticRating") else null,
                officialRating = o.optStringOrNull("OfficialRating"),
                runTimeTicks = o.optLong("RunTimeTicks", 0),
                genres = o.optJSONArray("Genres").toStringList(),
                primaryTag = imageTags?.optStringOrNull("Primary"),
                backdropTag = backdropTag,
                backdropItemId = backdropItemId,
                logoTag = logoTag,
                logoItemId = logoItemId,
                thumbTag = thumbTag,
                thumbItemId = thumbItemId,
                seriesId = o.optStringOrNull("SeriesId"),
                seriesName = o.optStringOrNull("SeriesName"),
                seriesPrimaryTag = o.optStringOrNull("SeriesPrimaryImageTag"),
                seasonId = o.optStringOrNull("SeasonId"),
                indexNumber = if (o.has("IndexNumber")) o.optInt("IndexNumber") else null,
                parentIndexNumber = if (o.has("ParentIndexNumber")) o.optInt("ParentIndexNumber") else null,
                childCount = if (o.has("ChildCount")) o.optInt("ChildCount") else null,
                playbackPositionTicks = userData?.optLong("PlaybackPositionTicks", 0) ?: 0,
                playedPercentage = if (userData?.has("PlayedPercentage") == true) userData.optDouble("PlayedPercentage") else null,
                played = userData?.optBoolean("Played") ?: false,
                isFavorite = userData?.optBoolean("IsFavorite") ?: false,
                unplayedCount = if (userData?.has("UnplayedItemCount") == true) userData.optInt("UnplayedItemCount") else null,
                collectionType = o.optStringOrNull("CollectionType"),
                people = people,
                taglines = o.optJSONArray("Taglines").toStringList(),
                studios = o.optJSONArray("Studios")?.let { arr ->
                    (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString("Name") }
                } ?: emptyList(),
                streams = streams,
                premiereDate = o.optStringOrNull("PremiereDate"),
            )
        }
    }
}

fun JSONObject.optStringOrNull(key: String): String? =
    if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotEmpty() } else null

fun JSONArray.optStringOrNull(i: Int): String? =
    if (i < length() && !isNull(i)) optString(i).takeIf { it.isNotEmpty() } else null

fun JSONArray?.toStringList(): List<String> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optStringOrNull(it) }
