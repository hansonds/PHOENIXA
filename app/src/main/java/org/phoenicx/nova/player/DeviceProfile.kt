package org.phoenicx.nova.player

import android.media.MediaCodecList
import org.json.JSONArray
import org.json.JSONObject

/** Builds a Jellyfin DeviceProfile from the decoders this TV actually has. */
object DeviceProfile {

    private fun hasDecoder(mime: String): Boolean = try {
        MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.any { info ->
            !info.isEncoder && info.supportedTypes.any { it.equals(mime, ignoreCase = true) }
        }
    } catch (e: Exception) {
        false
    }

    private var cached: JSONObject? = null
    private var cachedProgressive: JSONObject? = null

    /** Built once per app run — scanning the TV's decoders is slow. */
    @Synchronized
    fun get(progressive: Boolean = false): JSONObject =
        if (progressive) cachedProgressive ?: build(true).also { cachedProgressive = it }
        else cached ?: build(false).also { cached = it }

    /** progressive = transcode to a single MPEG-TS stream instead of HLS (last-resort fallback). */
    fun build(progressive: Boolean = false): JSONObject {
        val video = mutableListOf("h264")
        if (hasDecoder("video/hevc")) video += listOf("hevc", "h265")
        if (hasDecoder("video/x-vnd.on2.vp9")) video += "vp9"
        if (hasDecoder("video/av01")) video += "av1"
        if (hasDecoder("video/mp4v-es")) video += "mpeg4"
        if (hasDecoder("video/mpeg2")) video += "mpeg2video"

        // With the FFmpeg decoder built in, the player handles every common audio format itself
        // (passthrough to your TV/receiver when it supports it, otherwise decoded in the app).
        val audio = mutableListOf(
            "aac", "mp3", "mp2", "opus", "flac", "vorbis", "alac", "pcm_s16le", "pcm_s24le",
            "ac3", "eac3", "dts", "dca", "truehd", "mlp",
        )

        val v = video.joinToString(",")
        val a = audio.joinToString(",")

        val directPlay = JSONArray()
            .put(JSONObject().put("Type", "Video").put("Container", "mp4,m4v,mkv,webm,mov,ts,mpegts,avi")
                .put("VideoCodec", v).put("AudioCodec", a))
            .put(JSONObject().put("Type", "Audio").put("Container", "mp3,aac,flac,m4a,ogg,opus,wav"))

        val transcodeAudio = buildList {
            add("aac"); if ("ac3" in audio) add("ac3"); if ("eac3" in audio) add("eac3")
        }.joinToString(",")

        val transcoding = JSONArray()
            .put(
                if (progressive)
                    JSONObject().put("Type", "Video").put("Container", "ts").put("Protocol", "http")
                        .put("VideoCodec", "h264").put("AudioCodec", "aac")
                        .put("Context", "Streaming").put("MaxAudioChannels", "2")
                else
                    JSONObject().put("Type", "Video").put("Container", "ts").put("Protocol", "hls")
                        .put("VideoCodec", "h264")
                        .put("AudioCodec", transcodeAudio)
                        .put("Context", "Streaming")
                        .put("MaxAudioChannels", "6")
                        .put("MinSegments", 1)
                        .put("BreakOnNonKeyFrames", false)
            )
            .put(JSONObject().put("Type", "Audio").put("Container", "mp3").put("AudioCodec", "mp3")
                .put("Protocol", "http").put("Context", "Streaming"))

        val subs = JSONArray()
        listOf("srt", "subrip", "ass", "ssa", "vtt", "webvtt", "pgs", "pgssub", "dvdsub", "dvbsub").forEach {
            subs.put(JSONObject().put("Format", it).put("Method", "Embed"))
        }
        listOf("srt", "subrip", "vtt", "webvtt", "ass", "ssa").forEach {
            subs.put(JSONObject().put("Format", it).put("Method", "External"))
        }

        return JSONObject()
            .put("Name", "PHOENIXA")
            .put("MaxStreamingBitrate", 200_000_000)
            .put("MaxStaticBitrate", 200_000_000)
            .put("MusicStreamingTranscodingBitrate", 320_000)
            .put("DirectPlayProfiles", directPlay)
            .put("TranscodingProfiles", transcoding)
            .put("SubtitleProfiles", subs)
            .put("ContainerProfiles", JSONArray())
            .put("CodecProfiles", JSONArray())
    }
}
