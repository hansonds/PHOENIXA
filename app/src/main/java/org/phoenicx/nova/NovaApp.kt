package org.phoenicx.nova

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import org.phoenicx.nova.data.JellyfinApi
import org.phoenicx.nova.data.Prefs

class NovaApp : Application(), ImageLoaderFactory {

    lateinit var prefs: Prefs
        private set
    lateinit var api: JellyfinApi
        private set

    /** A touch device with a big screen (7 inches and up): rotates freely */
    val isTablet: Boolean get() = !isTv && resources.configuration.smallestScreenWidthDp >= 600

    /** TV (remote control) or phone/tablet (touch). Fire TV doesn't always report "leanback", but has no touchscreen. */
    val isTv: Boolean by lazy {
        val pm = packageManager
        val ui = getSystemService(UI_MODE_SERVICE) as android.app.UiModeManager
        pm.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK) ||
            pm.hasSystemFeature("amazon.hardware.fire_tv") ||
            ui.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION ||
            !pm.hasSystemFeature(android.content.pm.PackageManager.FEATURE_TOUCHSCREEN)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        installCrashLog()
        prefs = Prefs(this)
        api = JellyfinApi(prefs)
        // Checking which formats this device can decode takes a moment; do it now so Play opens faster
        Thread { runCatching { org.phoenicx.nova.player.DeviceProfile.get(false) } }.start()
        // Warm up the decoder scan in the background so playback starts faster later
        Thread { runCatching { org.phoenicx.nova.player.DeviceProfile.get() } }.start()
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient(api.http)
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.20).build() }
        .diskCache {
            DiskCache.Builder().directory(cacheDir.resolve("images")).maxSizeBytes(250L * 1024 * 1024).build()
        }
        .crossfade(250)
        .respectCacheHeaders(false)
        .build()

    /** Saves the reason for a crash so it can be read in Settings (helps fixing it). */
    private fun installCrashLog() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                val sw = java.io.StringWriter()
                e.printStackTrace(java.io.PrintWriter(sw))
                val when_ = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date())
                val mem = Runtime.getRuntime().maxMemory() / (1024 * 1024)
                crashFile().writeText("$when_ · thread ${t.name} · heap ${mem} MB\n" + sw.toString().take(6000))
            }
            previous?.uncaughtException(t, e)
        }
    }

    fun crashFile() = filesDir.resolve("last_crash.txt")

    companion object {
        lateinit var instance: NovaApp
            private set
    }
}

/** Phones: hide the status and navigation bars for a full-screen cinema look (swipe from the edge to show them). */
fun android.app.Activity.immersiveOnPhone() {
    if (NovaApp.instance.isTv) return
    androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
    if (android.os.Build.VERSION.SDK_INT >= 28) {
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }
    val ctl = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
    ctl.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    ctl.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
}
