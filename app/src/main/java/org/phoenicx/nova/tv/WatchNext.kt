package org.phoenicx.nova.tv

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.phoenicx.nova.MainActivity
import org.phoenicx.nova.NovaApp
import org.phoenicx.nova.data.Item

/**
 * Android TV / Google TV: puts what you're watching in the TV home screen's "Continue watching" row.
 * Fire TV has no such row, so nothing happens there.
 */
object WatchNext {
    const val EXTRA_PLAY_ID = "wn_play_id"
    const val EXTRA_PLAY_TICKS = "wn_play_ticks"

    private val lock = Mutex()

    private fun supported(context: Context): Boolean {
        val app = NovaApp.instance
        if (!app.isTv) return false
        if (context.packageManager.hasSystemFeature("amazon.hardware.fire_tv")) return false
        return runCatching { context.packageManager.resolveContentProvider(TvContractCompat.AUTHORITY, 0) != null }.getOrDefault(false)
    }

    /** Re-reads Continue Watching and Next Up from the server and updates the TV's row */
    fun sync(context: Context) {
        val ctx = context.applicationContext
        if (!supported(ctx)) return
        CoroutineScope(Dispatchers.IO).launch {
            lock.withLock {
                runCatching {
                    val api = NovaApp.instance.api
                    if (!api.isLoggedIn) { clear(ctx); return@runCatching }
                    val resume = runCatching { api.resume() }.getOrDefault(emptyList())
                    val next = runCatching { api.nextUp() }.getOrDefault(emptyList())
                    clear(ctx)
                    val now = System.currentTimeMillis()
                    resume.take(10).forEachIndexed { i, item -> insert(ctx, item, TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE, now - i * 60_000L) }
                    next.filter { n -> resume.none { it.id == n.id } }.take(8).forEachIndexed { i, item ->
                        insert(ctx, item, TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_NEXT, now - (20 + i) * 60_000L)
                    }
                }
            }
        }
    }

    /** Removes everything PHOENIXA put in the row (e.g. after switching profile or signing out) */
    @SuppressLint("RestrictedApi")
    private fun clear(ctx: Context) {
        val cr = ctx.contentResolver
        cr.query(TvContractCompat.WatchNextPrograms.CONTENT_URI, arrayOf(android.provider.BaseColumns._ID), null, null, null)?.use { c ->
            while (c.moveToNext()) {
                runCatching { cr.delete(TvContractCompat.buildWatchNextProgramUri(c.getLong(0)), null, null) }
            }
        }
    }

    @SuppressLint("RestrictedApi")
    private fun insert(ctx: Context, item: Item, watchType: Int, engaged: Long) {
        val api = NovaApp.instance.api
        val image = api.landscape(item, 640) ?: api.poster(item, 400) ?: return
        val intent = Intent(ctx, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .putExtra(EXTRA_PLAY_ID, item.id)
            .putExtra(EXTRA_PLAY_TICKS, if (watchType == TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE) item.playbackPositionTicks else 0L)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val b = WatchNextProgram.Builder()
            .setWatchNextType(watchType)
            .setLastEngagementTimeUtcMillis(engaged)
            .setInternalProviderId(item.id)
            .setIntentUri(Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME)))
            .setPosterArtUri(Uri.parse(image))
            .setPosterArtAspectRatio(
                if (api.landscape(item, 640) != null) TvContractCompat.PreviewProgramColumns.ASPECT_RATIO_16_9
                else TvContractCompat.PreviewProgramColumns.ASPECT_RATIO_2_3
            )
            .setDescription(item.overview.take(300))
        if (item.isEpisode) {
            b.setType(TvContractCompat.PreviewProgramColumns.TYPE_TV_EPISODE)
                .setTitle(item.seriesName ?: item.name)
                .setEpisodeTitle(item.name)
            item.parentIndexNumber?.let { b.setSeasonNumber(it) }
            item.indexNumber?.let { b.setEpisodeNumber(it) }
        } else {
            b.setType(TvContractCompat.PreviewProgramColumns.TYPE_MOVIE).setTitle(item.name)
        }
        val durMs = item.runTimeTicks / 10_000
        if (durMs > 0) b.setDurationMillis(durMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        if (watchType == TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE && item.playbackPositionTicks > 0) {
            b.setLastPlaybackPositionMillis((item.playbackPositionTicks / 10_000).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        }
        ctx.contentResolver.insert(TvContractCompat.WatchNextPrograms.CONTENT_URI, b.build().toContentValues())
    }
}
