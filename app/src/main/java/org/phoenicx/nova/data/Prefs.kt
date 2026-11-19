package org.phoenicx.nova.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** One saved sign-in ("Who's watching?") */
data class Profile(
    val server: String,
    val userId: String,
    val userName: String,
    val token: String,
    val deviceId: String,
    val imageTag: String? = null,
) {
    val key get() = "$server|$userId"
}

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("nova", Context.MODE_PRIVATE)

    var server: String
        get() = sp.getString("server", "") ?: ""
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

    /** Last audio language you picked (e.g. "tam"), used for every title */
    var audioLang: String?
        get() = sp.getString("audioLang", null)
        set(v) = sp.edit().putString("audioLang", v).apply()

    /** Last subtitle language you picked, or "off" */
    var subLang: String?
        get() = sp.getString("subLang", null)
        set(v) = sp.edit().putString("subLang", v).apply()

    /** Audio / subtitle language picked for one series (so every episode of it starts the same way) */
    fun seriesAudio(seriesId: String): String? = sp.getString("sa_$seriesId", null)
    fun seriesSub(seriesId: String): String? = sp.getString("ss_$seriesId", null)
    fun setSeriesAudio(seriesId: String, lang: String) = sp.edit().putString("sa_$seriesId", lang).apply()
    fun setSeriesSub(seriesId: String, lang: String) = sp.edit().putString("ss_$seriesId", lang).apply()

    /** 0 = Small, 1 = Medium, 2 = Large */
    var cardSize: Int
        get() = sp.getInt("cardSize", 1)
        set(v) = sp.edit().putInt("cardSize", v).apply()

    /** Player picture size: 0 = Fit, 1 = Zoom, 2 = Stretch */
    var videoFit: Int
        get() = sp.getInt("videoFit", 0)
        set(v) = sp.edit().putInt("videoFit", v).apply()

    var theme: Int
        get() = sp.getInt("theme", 1) // 1 = Phoenix Ember
        set(v) = sp.edit().putInt("theme", v).apply()

    /** This device's id for the active profile (each profile has its own, so signing in another
     *  person doesn't end the other sessions on the server) */
    var deviceId: String
        get() {
            var id = sp.getString("deviceId", null)
            if (id == null) {
                id = UUID.randomUUID().toString()
                sp.edit().putString("deviceId", id).apply()
            }
            return id
        }
        set(v) = sp.edit().putString("deviceId", v).apply()

    var userImageTag: String?
        get() = sp.getString("userImageTag", null)
        set(v) = sp.edit().putString("userImageTag", v).apply()

    // ---------- Profiles ----------

    /** Every saved sign-in. The first time, the current sign-in becomes the first profile. */
    val profiles: List<Profile>
        get() {
            val raw = sp.getString("profiles", null)
            if (raw == null) {
                val cur = currentProfile() ?: return emptyList()
                saveProfiles(listOf(cur))
                return listOf(cur)
            }
            return runCatching {
                val a = JSONArray(raw)
                (0 until a.length()).mapNotNull { i ->
                    val o = a.optJSONObject(i) ?: return@mapNotNull null
                    Profile(
                        server = o.optString("server"), userId = o.optString("userId"), userName = o.optString("userName"),
                        token = o.optString("token"), deviceId = o.optString("deviceId"),
                        imageTag = o.optString("imageTag").takeIf { it.isNotBlank() && it != "null" },
                    ).takeIf { it.token.isNotBlank() && it.userId.isNotBlank() }
                }
            }.getOrDefault(emptyList())
        }

    private fun saveProfiles(list: List<Profile>) {
        val a = JSONArray()
        list.forEach { p ->
            a.put(JSONObject().put("server", p.server).put("userId", p.userId).put("userName", p.userName)
                .put("token", p.token).put("deviceId", p.deviceId).put("imageTag", p.imageTag ?: ""))
        }
        sp.edit().putString("profiles", a.toString()).apply()
    }

    private fun currentProfile(): Profile? {
        val u = userId ?: return null
        val t = token?.takeIf { it.isNotBlank() } ?: return null
        return Profile(server, u, userName ?: "User", t, deviceId, userImageTag)
    }

    val activeKey: String? get() = currentProfile()?.key

    /** After a sign-in: add (or update) the signed-in person in the profile list */
    fun saveCurrentAsProfile() {
        val cur = currentProfile() ?: return
        val list = profiles.filterNot { it.key == cur.key } + cur
        saveProfiles(list)
    }

    /** Makes a saved profile the active one */
    fun switchTo(p: Profile) {
        sp.edit()
            .putString("server", p.server).putString("userId", p.userId).putString("userName", p.userName)
            .putString("token", p.token).putString("deviceId", p.deviceId).putString("userImageTag", p.imageTag)
            .apply()
    }

    /** Before signing in someone new: their own device id (the previous one is kept in its profile) */
    fun prepareNewSignIn() {
        profiles // makes sure the current sign-in is saved as a profile first
        sp.edit().putString("deviceId", UUID.randomUUID().toString()).apply()
    }

    fun removeProfile(p: Profile) {
        saveProfiles(profiles.filterNot { it.key == p.key })
        if (activeKey == p.key) signOut()
    }

    fun signOut() {
        sp.edit().remove("userId").remove("token").remove("userName").remove("userImageTag").apply()
    }
}
