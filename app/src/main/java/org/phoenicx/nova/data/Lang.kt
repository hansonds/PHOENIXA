package org.phoenicx.nova.data

import org.phoenicx.nova.NovaApp
import java.util.Locale

/** Turns language codes (tam, hi, eng, en-US…) into real names and remembers your language choices. */
object Lang {
    private val names = mapOf(
        "en" to "English", "eng" to "English",
        "ta" to "Tamil", "tam" to "Tamil",
        "hi" to "Hindi", "hin" to "Hindi",
        "ml" to "Malayalam", "mal" to "Malayalam",
        "te" to "Telugu", "tel" to "Telugu",
        "kn" to "Kannada", "kan" to "Kannada",
        "bn" to "Bengali", "ben" to "Bengali",
        "mr" to "Marathi", "mar" to "Marathi",
        "gu" to "Gujarati", "guj" to "Gujarati",
        "pa" to "Punjabi", "pan" to "Punjabi",
        "or" to "Odia", "ori" to "Odia",
        "as" to "Assamese", "asm" to "Assamese",
        "ur" to "Urdu", "urd" to "Urdu",
        "si" to "Sinhala", "sin" to "Sinhala",
        "ne" to "Nepali", "nep" to "Nepali",
        "sa" to "Sanskrit", "san" to "Sanskrit",
        "ja" to "Japanese", "jpn" to "Japanese",
        "ko" to "Korean", "kor" to "Korean",
        "zh" to "Chinese", "chi" to "Chinese", "zho" to "Chinese",
        "es" to "Spanish", "spa" to "Spanish",
        "fr" to "French", "fre" to "French", "fra" to "French",
        "de" to "German", "ger" to "German", "deu" to "German",
        "it" to "Italian", "ita" to "Italian",
        "pt" to "Portuguese", "por" to "Portuguese",
        "ru" to "Russian", "rus" to "Russian",
        "ar" to "Arabic", "ara" to "Arabic",
        "th" to "Thai", "tha" to "Thai",
        "id" to "Indonesian", "ind" to "Indonesian",
        "ms" to "Malay", "may" to "Malay", "msa" to "Malay",
        "vi" to "Vietnamese", "vie" to "Vietnamese",
        "tr" to "Turkish", "tur" to "Turkish",
        "pl" to "Polish", "pol" to "Polish",
        "nl" to "Dutch", "dut" to "Dutch", "nld" to "Dutch",
        "sv" to "Swedish", "swe" to "Swedish",
        "no" to "Norwegian", "nor" to "Norwegian", "nob" to "Norwegian",
        "da" to "Danish", "dan" to "Danish",
        "fi" to "Finnish", "fin" to "Finnish",
        "el" to "Greek", "gre" to "Greek", "ell" to "Greek",
        "he" to "Hebrew", "heb" to "Hebrew",
        "fa" to "Persian", "per" to "Persian", "fas" to "Persian",
        "uk" to "Ukrainian", "ukr" to "Ukrainian",
        "cs" to "Czech", "cze" to "Czech", "ces" to "Czech",
        "hu" to "Hungarian", "hun" to "Hungarian",
        "ro" to "Romanian", "rum" to "Romanian", "ron" to "Romanian",
        "fil" to "Filipino", "tl" to "Tagalog", "tgl" to "Tagalog",
    )

    private val unknown = setOf("", "und", "unk", "mul", "zxx", "mis", "null", "none")

    /** Readable language name, or null when the track has no language tag. */
    fun name(code: String?): String? {
        val c = code?.trim()?.lowercase() ?: return null
        if (c in unknown) return null
        names[c]?.let { return it }
        val base = c.substringBefore('-').substringBefore('_')
        names[base]?.let { return it }
        val viaLocale = runCatching { Locale.forLanguageTag(c).getDisplayLanguage(Locale.ENGLISH) }.getOrNull()
        return viaLocale?.takeIf { it.isNotBlank() && !it.equals(c, true) && !it.equals(base, true) } ?: c.uppercase()
    }

    fun same(a: String?, b: String?): Boolean {
        val na = name(a) ?: return false
        return na == name(b)
    }

    private fun codecName(codec: String): String = when (codec.lowercase()) {
        "subrip", "srt" -> "SRT"
        "pgssub", "pgs", "hdmv_pgs_subtitle" -> "PGS"
        "ass", "ssa" -> "ASS"
        "webvtt", "vtt" -> "VTT"
        "dvdsub", "dvd_subtitle" -> "DVD"
        "dvbsub", "dvb_subtitle" -> "DVB"
        "mov_text" -> "TEXT"
        "dca", "dts" -> "DTS"
        "truehd" -> "TrueHD"
        "eac3" -> "E-AC3"
        "ac3" -> "AC3"
        else -> codec.uppercase()
    }

    /** Title + detail line for a Jellyfin audio/subtitle stream, e.g. "Tamil" / "AC3 · 5.1 · Default" */
    fun label(s: MediaStreamInfo): Pair<String, String> {
        val lang = name(s.language)
        val title = s.title?.trim()?.takeIf { it.isNotEmpty() }
        val main = when {
            title != null && lang != null && !title.contains(lang, true) -> "$lang · $title"
            title != null -> title
            lang != null -> lang
            else -> "Unknown language"
        }
        val detail = buildList {
            if (s.codec.isNotBlank()) add(codecName(s.codec))
            if (s.type == "Audio" && s.channels > 0) add(
                when {
                    s.channels >= 8 -> "7.1"
                    s.channels >= 6 -> "5.1"
                    s.channels == 2 -> "Stereo"
                    s.channels == 1 -> "Mono"
                    else -> "${s.channels}ch"
                }
            )
            if (s.isForced) add("Forced")
            if (s.isExternal) add("External file")
            if (s.isDefault) add("Default")
        }.joinToString(" · ")
        return main to detail
    }

    // ---------- Remembered choices ----------

    private val prefs get() = NovaApp.instance.prefs

    fun rememberAudio(language: String?) {
        if (name(language) != null) prefs.audioLang = language
    }

    /** null language with off=true means "subtitles off" */
    fun rememberSubtitle(language: String?, off: Boolean) {
        when {
            off -> prefs.subLang = "off"
            name(language) != null -> prefs.subLang = language
        }
    }

    /** Which audio stream to use: your remembered language if this file has it, else the server default. */
    fun pickAudio(streams: List<MediaStreamInfo>, serverDefault: Int?): Int? {
        val audio = streams.filter { it.type == "Audio" }
        val want = prefs.audioLang
        if (want != null) audio.firstOrNull { same(it.language, want) }?.let { return it.index }
        return serverDefault ?: audio.firstOrNull()?.index
    }

    /** Which subtitle stream to use (-1 = off). */
    fun pickSubtitle(streams: List<MediaStreamInfo>, serverDefault: Int?): Int {
        val subs = streams.filter { it.type == "Subtitle" }
        return when (val want = prefs.subLang) {
            null -> serverDefault ?: -1
            "off" -> subs.firstOrNull { it.isForced && it.index == serverDefault }?.index ?: -1
            else -> {
                val match = subs.filter { same(it.language, want) }
                (match.firstOrNull { !it.isForced } ?: match.firstOrNull())?.index ?: (serverDefault ?: -1)
            }
        }
    }
}
