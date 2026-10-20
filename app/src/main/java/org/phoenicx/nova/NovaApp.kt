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
        prefs = Prefs(this)
        api = JellyfinApi(prefs)
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

    companion object {
        lateinit var instance: NovaApp
            private set
    }
}
