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

    fun build(): JSONObject {
        val video = mutableListOf("h264")
        if (hasDecoder("video/hevc")) video += listOf("hevc", "h265")
        if (hasDecoder("video/x-vnd.on2.vp9")) video += "vp9"
        if (hasDecoder("video/av01")) video += "av1"
        if (hasDecoder("video/mp4v-es")) video += "mpeg4"
        if (hasDecoder("video/mpeg2")) video += "mpeg2video"

        // ExoPlayer decodes these in software or via the platform on every device
        val audio = mutableListOf("aac", "mp3", "mp2", "opus", "flac", "vorbis", "pcm_s16le", "pcm_s24le", "alac")
        if (hasDecoder("audio/ac3")) audio += "ac3"
        if (hasDecoder("audio/eac3")) audio += "eac3"
        if (hasDecoder("audio/vnd.dts")) audio += "dts"
        if (hasDecoder("audio/true-hd")) audio += "truehd"

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
            .put(JSONObject().put("Type", "Video").put("Container", "ts").put("Protocol", "hls")
                .put("VideoCodec", if ("hevc" in video) "h264,hevc" else "h264")
                .put("AudioCodec", transcodeAudio)
                .put("Context", "Streaming")
                .put("MaxAudioChannels", "6")
                .put("MinSegments", 1)
                .put("BreakOnNonKeyFrames", true))
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
            .put("Name", "Phoenicx Nova")
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
