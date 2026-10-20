package org.phoenicx.nova.data

import android.content.Context
import java.util.UUID

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("nova", Context.MODE_PRIVATE)

    var server: String
        get() = sp.getString("server", "https://media.phoenicx.org") ?: ""
        set(v) = sp.edit().putString("server", v).apply()

    var userId: String?
        get() = sp.getString("userId", null)
        set(v) = sp.edit().putString("userId", v).apply()

    var userName: String?
        get() = sp.getString("userName", null)
        set(v) = sp.edit().putString("userName", v).apply()

    var token: String?
        get() = sp.getString("token", null)
        set(v) = sp.edit().putString("token", v).apply()

    /** Max streaming bitrate in Mbps. 0 = unlimited */
    var maxBitrateMbps: Int
        get() = sp.getInt("maxBitrate", 0)
        set(v) = sp.edit().putInt("maxBitrate", v).apply()

    var autoPlayNext: Boolean
        get() = sp.getBoolean("autoPlayNext", true)
        set(v) = sp.edit().putBoolean("autoPlayNext", v).apply()

    var heroAutoRotate: Boolean
        get() = sp.getBoolean("heroAutoRotate", true)
        set(v) = sp.edit().putBoolean("heroAutoRotate", v).apply()

    /** Only ever direct play (no server transcoding). */
    var directPlayOnly: Boolean
        get() = sp.getBoolean("directOnly", true)
        set(v) = sp.edit().putBoolean("directOnly", v).apply()

    /** 0 = Small, 1 = Medium, 2 = Large */
    var cardSize: Int
        get() = sp.getInt("cardSize", 1)
        set(v) = sp.edit().putInt("cardSize", v).apply()

    val deviceId: String
        get() {
            var id = sp.getString("deviceId", null)
            if (id == null) {
                id = UUID.randomUUID().toString()
                sp.edit().putString("deviceId", id).apply()
            }
            return id
        }

    fun signOut() {
        sp.edit().remove("userId").remove("token").remove("userName").apply()
    }
}
