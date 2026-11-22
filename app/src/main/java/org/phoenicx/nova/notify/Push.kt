package org.phoenicx.nova.notify

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.phoenicx.nova.NovaApp
import org.phoenicx.nova.data.Profile
import org.phoenicx.nova.data.clientVersion
import org.phoenicx.nova.data.deviceName
import kotlin.coroutines.resume

/**
 * Push notifications, the WhatsApp way: the phone registers once with the server (Torvia),
 * and Torvia sends a push through Google's Firebase the moment something new is added.
 *
 * The Firebase settings come from the server (Torvia → Push notifications), so nothing secret
 * is built into the app, and each server owner uses their own free Firebase project.
 */
object Push {
    enum class State { Unknown, Ready, Off, NoServer, NoGooglePlay, Failed }

    /** For Settings: what's going on with notifications on this phone */
    val state = mutableStateOf(State.Unknown)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private val jsonType = "application/json".toMediaType()

    private fun sp(ctx: Context) = ctx.getSharedPreferences("push", Context.MODE_PRIVATE)

    private data class Config(val projectId: String, val appId: String, val apiKey: String, val senderId: String)

    private fun cached(ctx: Context): Config? {
        val s = sp(ctx)
        return Config(
            s.getString("projectId", null) ?: return null,
            s.getString("appId", null) ?: return null,
            s.getString("apiKey", null) ?: return null,
            s.getString("senderId", null) ?: return null,
        )
    }

    /** App start: set Firebase up from the last settings the server gave us, so pushes arrive even before the app opens */
    fun initFromCache(ctx: Context) {
        if (NovaApp.instance.isTv) return
        cached(ctx)?.let { runCatching { firebase(ctx, it) } }
    }

    private fun firebase(ctx: Context, c: Config): FirebaseApp? {
        val existing = FirebaseApp.getApps(ctx).firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }
        // A different Firebase project (the server owner changed it) takes effect the next time the app starts
        if (existing != null) return existing.takeIf { it.options.projectId == c.projectId }
        val opts = FirebaseOptions.Builder()
            .setProjectId(c.projectId).setApplicationId(c.appId).setApiKey(c.apiKey).setGcmSenderId(c.senderId)
            .build()
        return FirebaseApp.initializeApp(ctx, opts)
    }

    /** Run [sync] in the background */
    fun syncLater(ctx: Context) {
        if (NovaApp.instance.isTv) return
        scope.launch { runCatching { sync(ctx) } }
    }

    /**
     * Registers this phone for the signed-in person (or removes it when notifications are off).
     * Called on app start, after signing in / switching profile, from the Settings switch and when Google gives a new token.
     */
    suspend fun sync(ctx: Context): State = lock.withLock {
        val app = NovaApp.instance
        val prefs = app.prefs
        if (app.isTv) return@withLock State.Off
        if (!app.api.isLoggedIn) return@withLock State.Unknown.also { state.value = it }

        // Only the person using the app gets notifications: other saved profiles on this phone are removed from the list
        prefs.profiles.filter { it.key != prefs.activeKey }.forEach { unregister(it) }

        val (code, body) = runCatching { app.api.raw("GET", "/TorviaPush/Config") }.getOrElse { return@withLock State.Failed.also { state.value = it } }
        val cfg = if (code == 200) runCatching { JSONObject(body) }.getOrNull() else null
        if (cfg == null || !cfg.optBoolean("enabled", false)) return@withLock State.NoServer.also { state.value = it }

        if (!prefs.notifyNewTitles) {
            unregister(current() ?: return@withLock State.Off.also { state.value = it })
            return@withLock State.Off.also { state.value = it }
        }

        val c = Config(
            cfg.optString("projectId"), cfg.optString("appId"), cfg.optString("apiKey"), cfg.optString("senderId"),
        )
        if (c.projectId.isBlank() || c.appId.isBlank() || c.apiKey.isBlank() || c.senderId.isBlank()) {
            return@withLock State.NoServer.also { state.value = it }
        }
        sp(ctx).edit().putString("projectId", c.projectId).putString("appId", c.appId)
            .putString("apiKey", c.apiKey).putString("senderId", c.senderId).apply()
        if (runCatching { firebase(ctx, c) }.getOrNull() == null) return@withLock State.Failed.also { state.value = it }

        val token = fcmToken() ?: return@withLock State.NoGooglePlay.also { state.value = it }
        val reg = JSONObject()
            .put("token", token)
            .put("deviceId", prefs.deviceId)
            .put("deviceName", deviceName)
            .put("platform", "android")
            .put("appVersion", clientVersion)
        val (rc, _) = runCatching { app.api.raw("POST", "/TorviaPush/Register", reg) }.getOrElse { -1 to "" }
        (if (rc in 200..299) State.Ready else State.Failed).also { state.value = it }
    }

    /** Ask the server to send a test notification to this phone */
    suspend fun test(): Boolean {
        val (rc, _) = runCatching { NovaApp.instance.api.raw("POST", "/TorviaPush/Test") }.getOrElse { -1 to "" }
        return rc in 200..299
    }

    private fun current(): Profile? {
        val prefs = NovaApp.instance.prefs
        return prefs.profiles.firstOrNull { it.key == prefs.activeKey }
    }

    /** Stop notifications for a profile on this phone (sign out, removed profile, switched to someone else) */
    suspend fun unregister(p: Profile) = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url(p.server.trimEnd('/') + "/TorviaPush/Unregister")
                .header(
                    "Authorization",
                    "MediaBrowser Client=\"PHOENIXA\", Device=\"$deviceName\", DeviceId=\"${p.deviceId}\", Version=\"$clientVersion\", Token=\"${p.token}\"",
                )
                .post(JSONObject().put("deviceId", p.deviceId).toString().toRequestBody(jsonType))
                .build()
            NovaApp.instance.api.http.newCall(req).execute().close()
        }
    }

    private suspend fun fcmToken(): String? = withTimeoutOrNull(20_000) {
        suspendCancellableCoroutine { cont ->
            runCatching {
                FirebaseMessaging.getInstance().token.addOnCompleteListener { t ->
                    cont.resume(if (t.isSuccessful) t.result else null)
                }
            }.onFailure { cont.resume(null) }
        }
    }

    /** A push arrived */
    internal fun handle(ctx: Context, data: Map<String, String>) {
        val prefs = NovaApp.instance.prefs
        if (NovaApp.instance.isTv || !prefs.notifyNewTitles) return
        // Meant for whoever is using PHOENIXA on this phone right now
        val forUser = data["userId"]
        if (forUser != null && !forUser.equals(prefs.userId, ignoreCase = true)) return
        when (data["type"]) {
            "new_titles" -> runBlocking {
                withTimeoutOrNull(15_000) { NewTitles.show(ctx, NewTitles.parse(data["items"])) }
            }
            "test" -> NewTitles.showTest(ctx)
            "message" -> NewTitles.showMessage(ctx, data["title"], data["body"])
            else -> Unit   // something a newer server sends that this version doesn't know: ignore it
        }
    }
}

/** Receives pushes from Google (Firebase) */
class PushService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        runCatching { Push.handle(applicationContext, message.data) }
    }

    override fun onNewToken(token: String) {
        Push.syncLater(applicationContext)
    }
}
