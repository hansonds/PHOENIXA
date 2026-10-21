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

    override fun onCreate() {
        super.onCreate()
        instance = this
        installCrashLog()
        prefs = Prefs(this)
        api = JellyfinApi(prefs)
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
