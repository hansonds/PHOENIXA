package org.phoenicx.nova.player

import org.json.JSONArray
import org.json.JSONObject

/**
 * Tells the Jellyfin server what this PC can play. mpv plays almost everything,
 * so every file is allowed to direct play; transcoding is only a fallback.
 */
object DesktopProfile {
    fun json(maxBitrateMbps: Int): JSONObject {
        val max = if (maxBitrateMbps > 0) maxBitrateMbps * 1_000_000 else 1_000_000_000
        val subs = JSONArray()
        listOf("srt", "subrip", "ass", "ssa", "vtt", "webvtt").forEach {
            subs.put(JSONObject().put("Format", it).put("Method", "External"))
        }
        listOf("srt", "subrip", "ass", "ssa", "vtt", "webvtt", "pgssub", "pgs", "dvdsub", "dvbsub", "sub", "mov_text").forEach {
            subs.put(JSONObject().put("Format", it).put("Method", "Embed"))
        }
        return JSONObject()
            .put("Name", "PHOENIXA Desktop")
            .put("MaxStreamingBitrate", max)
            .put("MaxStaticBitrate", max)
            .put("MusicStreamingTranscodingBitrate", 384000)
            .put(
                "DirectPlayProfiles", JSONArray()
                    .put(JSONObject().put("Type", "Video"))
                    .put(JSONObject().put("Type", "Audio"))
            )
            .put(
                "TranscodingProfiles", JSONArray().put(
                    JSONObject()
                        .put("Container", "ts")
                        .put("Type", "Video")
                        .put("VideoCodec", "h264,hevc")
                        .put("AudioCodec", "aac,ac3,eac3,mp3")
                        .put("Protocol", "hls")
                        .put("Context", "Streaming")
                        .put("MaxAudioChannels", "8")
                        .put("MinSegments", "1")
                        .put("BreakOnNonKeyFrames", true)
                )
            )
            .put("ContainerProfiles", JSONArray())
            .put("CodecProfiles", JSONArray())
            .put("SubtitleProfiles", subs)
    }
}
