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
    val index: Int = 0,
    val language: String? = null,
    val title: String? = null,
    val isDefault: Boolean = false,
    val isForced: Boolean = false,
    val isExternal: Boolean = false,
    val isTextSubtitle: Boolean = false,
    val videoRangeType: String = "",
) {
    /** Human label for HDR formats, or null for SDR */
    val hdrLabel: String?
        get() {
            val t = videoRangeType.uppercase()
            return when {
                t.startsWith("DOVI") -> "Dolby Vision"
                t.contains("HDR10PLUS") || t.contains("HDR10+") -> "HDR10+"
                t.contains("HDR10") -> "HDR10"
                t.contains("HLG") -> "HLG"
                videoRange.equals("HDR", true) -> "HDR"
                else -> null
            }
        }

    companion object {
        fun parse(s: JSONObject) = MediaStreamInfo(
            type = s.optString("Type"),
            codec = s.optString("Codec"),
            displayTitle = s.optString("DisplayTitle"),
            width = s.optInt("Width"),
            height = s.optInt("Height"),
            videoRange = s.optString("VideoRange"),
            channels = s.optInt("Channels"),
            index = s.optInt("Index"),
            language = s.optStringOrNull("Language"),
            title = s.optStringOrNull("Title"),
            isDefault = s.optBoolean("IsDefault"),
            isForced = s.optBoolean("IsForced"),
            isExternal = s.optBoolean("IsExternal"),
            isTextSubtitle = s.optBoolean("IsTextSubtitleStream"),
            videoRangeType = s.optString("VideoRangeType"),
        )
    }
}

/** One playable version of a title (e.g. 4K HDR vs 1080p). */
data class MediaSourceInfo(
    val id: String,
    val name: String,
    val container: String,
    val size: Long,
    val bitrate: Long,
    val streams: List<MediaStreamInfo>,
    val defaultAudioIndex: Int?,
    val defaultSubtitleIndex: Int?,
) {
    val video get() = streams.firstOrNull { it.type == "Video" }
    val audioStreams get() = streams.filter { it.type == "Audio" }
    val subtitleStreams get() = streams.filter { it.type == "Subtitle" }

    val label: String
        get() {
            val v = video
            val res = when {
                v == null -> null
                v.width >= 3200 || v.height >= 1700 -> "4K"
                v.width >= 1800 || v.height >= 1000 -> "1080p"
                v.width >= 1200 || v.height >= 700 -> "720p"
                v.height > 0 -> "${v.height}p"
                else -> null
            }
            return listOfNotNull(res, v?.hdrLabel, v?.codec?.uppercase()).joinToString(" · ").ifEmpty { name }
        }

    val detail: String
        get() = listOfNotNull(
            name.takeIf { it.isNotBlank() },
            container.uppercase().takeIf { it.isNotBlank() },
            if (size > 0) String.format("%.1f GB", size / 1_073_741_824.0) else null,
            if (bitrate > 0) "${bitrate / 1_000_000} Mbps" else null,
        ).joinToString(" · ")

    companion object {
        fun parse(o: JSONObject): MediaSourceInfo {
            val arr = o.optJSONArray("MediaStreams")
            val streams = if (arr == null) emptyList() else (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(MediaStreamInfo::parse) }
            return MediaSourceInfo(
                id = o.optString("Id"),
                name = o.optString("Name"),
                container = o.optString("Container"),
                size = o.optLong("Size"),
                bitrate = o.optLong("Bitrate"),
                streams = streams,
                defaultAudioIndex = if (o.has("DefaultAudioStreamIndex") && !o.isNull("DefaultAudioStreamIndex")) o.optInt("DefaultAudioStreamIndex") else null,
                defaultSubtitleIndex = if (o.has("DefaultSubtitleStreamIndex") && !o.isNull("DefaultSubtitleStreamIndex")) o.optInt("DefaultSubtitleStreamIndex") else null,
            )
        }
    }
}

/** What the user picked on the details page before pressing Play. */
data class PlaySelection(
    val mediaSourceId: String? = null,
    val audioIndex: Int? = null,
    /** -1 = subtitles off, null = server default */
    val subtitleIndex: Int? = null,
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
    val mediaSources: List<MediaSourceInfo> = emptyList(),
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
            v.hdrLabel?.let { out += it }
            val a = streams.firstOrNull { it.type == "Audio" }
            if (a != null) {
                val at = a.displayTitle.uppercase()
                when {
                    at.contains("ATMOS") -> out += "Atmos"
                    a.codec.equals("truehd", true) -> out += "TrueHD"
                    a.codec.startsWith("dts", true) || a.codec.equals("dca", true) -> out += "DTS"
                }
                if (a.channels >= 6) out += if (a.channels >= 8) "7.1" else "5.1"
            }
            if (mediaSources.size > 1) out += "${mediaSources.size} versions"
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
                (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let(MediaStreamInfo::parse) }
            } ?: emptyList()
            val sources = o.optJSONArray("MediaSources")?.let { arr ->
                (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let(MediaSourceInfo::parse) }
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
                streams = streams.ifEmpty { sources.firstOrNull()?.streams ?: emptyList() },
                premiereDate = o.optStringOrNull("PremiereDate"),
                mediaSources = sources,
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
