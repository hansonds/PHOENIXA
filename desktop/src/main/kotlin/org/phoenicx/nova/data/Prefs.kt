package org.phoenicx.nova.data

import java.io.File
import java.util.Properties
import java.util.UUID

/** Settings saved in %APPDATA%\PHOENIXA\settings.properties */
class Prefs {
    private val dir: File = File(System.getenv("APPDATA") ?: System.getProperty("user.home"), "PHOENIXA").apply { mkdirs() }
    private val file = File(dir, "settings.properties")
    private val props = Properties().apply {
        runCatching { if (file.exists()) file.inputStream().use { load(it) } }
    }

    val folder: File get() = dir

    @Synchronized
    private fun save() {
        runCatching { file.outputStream().use { props.store(it, "PHOENIXA settings") } }
    }

    private fun str(key: String): String? = props.getProperty(key)
    private fun put(key: String, v: String?) {
        if (v == null) props.remove(key) else props.setProperty(key, v)
        save()
    }
    private fun int(key: String, def: Int) = str(key)?.toIntOrNull() ?: def
    private fun bool(key: String, def: Boolean) = str(key)?.toBooleanStrictOrNull() ?: def

    var server: String
        get() = str("server") ?: ""
        set(v) = put("server", v)

    var userId: String?
        get() = str("userId")
        set(v) = put("userId", v)

    var userName: String?
        get() = str("userName")
        set(v) = put("userName", v)

    var token: String?
        get() = str("token")
        set(v) = put("token", v)

    /** Max streaming bitrate in Mbps. 0 = unlimited */
    var maxBitrateMbps: Int
        get() = int("maxBitrate", 0)
        set(v) = put("maxBitrate", v.toString())

    var autoPlayNext: Boolean
        get() = bool("autoPlayNext", true)
        set(v) = put("autoPlayNext", v.toString())

    var heroAutoRotate: Boolean
        get() = bool("heroAutoRotate", true)
        set(v) = put("heroAutoRotate", v.toString())

    /** Only ever direct play (no server transcoding). */
    var directPlayOnly: Boolean
        get() = bool("directOnly", true)
        set(v) = put("directOnly", v.toString())

    /** Open the player in full screen */
    var playerFullscreen: Boolean
        get() = bool("playerFullscreen", true)
        set(v) = put("playerFullscreen", v.toString())

    var audioLang: String?
        get() = str("audioLang")
        set(v) = put("audioLang", v)

    var subLang: String?
        get() = str("subLang")
        set(v) = put("subLang", v)

    /** 0 = Small, 1 = Medium, 2 = Large */
    var cardSize: Int
        get() = int("cardSize", 1)
        set(v) = put("cardSize", v.toString())

    var theme: Int
        get() = int("theme", 0)
        set(v) = put("theme", v.toString())

    val deviceId: String
        get() = str("deviceId") ?: UUID.randomUUID().toString().also { put("deviceId", it) }

    fun signOut() {
        props.remove("userId"); props.remove("token"); props.remove("userName")
        save()
    }
}
