package org.phoenicx.nova.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.phoenicx.nova.MainActivity
import org.phoenicx.nova.NovaApp
import org.phoenicx.nova.R
import org.phoenicx.nova.data.Item
import java.util.concurrent.TimeUnit

/**
 * Phones and tablets: every hour, quietly ask the server what was added most recently,
 * and show a notification for anything new ("New on PHOENIXA · Tumbbad (2018)"). Tapping it opens the title.
 */
object NewTitles {
    const val EXTRA_OPEN_ID = "open_item_id"
    private const val CHANNEL = "new_titles"
    private const val WORK = "new-titles-check"
    private const val GROUP = "org.phoenicx.nova.NEW_TITLES"

    /** Turn the regular check on or off (follows the Settings switch; never on TVs) */
    fun schedule(context: Context) {
        val app = NovaApp.instance
        val wm = WorkManager.getInstance(context)
        if (app.isTv || !app.prefs.notifyNewTitles) { wm.cancelUniqueWork(WORK); return }
        val req = PeriodicWorkRequestBuilder<CheckWorker>(1, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    private fun channel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "New movies & shows", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "When something new is added to your server"
            }
        )
    }

    /** Looks for new titles and notifies. The very first time it only remembers what's there. */
    suspend fun check(context: Context) {
        val app = NovaApp.instance
        val api = app.api
        val prefs = app.prefs
        if (!api.isLoggedIn || !prefs.notifyNewTitles) return
        val latest = runCatching { api.newestAdded(40) }.getOrNull() ?: return
        val seen = prefs.seenTitles()
        val firstTime = seen.isEmpty()
        prefs.saveSeenTitles((latest.map { it.id } + seen).distinct().take(400))
        if (firstTime) return
        val fresh = latest.filter { it.id !in seen }
        if (fresh.isEmpty()) return
        if (!canNotify(context)) return
        channel(context)

        // Episodes of the same show become one line: "Lupin · 2 new episodes"
        data class Entry(val openId: String, val title: String, val text: String, val image: String?)
        val entries = buildList {
            fresh.filter { !it.isEpisode }.forEach { m ->
                add(Entry(m.id, m.name + (m.year?.let { " ($it)" } ?: ""), if (m.type == "Series") "New series" else "New movie", api.poster(m, 300)))
            }
            // A brand-new show already has its own line, so its episodes aren't listed again
            val newShows = fresh.filter { it.type == "Series" }.map { it.id }.toSet()
            fresh.filter { it.isEpisode && it.seriesId !in newShows }.groupBy { it.seriesId ?: it.id }.forEach { (sid, eps) ->
                val e = eps.first()
                val name = e.seriesName ?: e.name
                val text = if (eps.size == 1) listOfNotNull(e.episodeLabel, e.name).joinToString(" · ") else "${eps.size} new episodes"
                add(Entry(sid, name, text, api.poster(e, 300)))
            }
        }
        val nm = NotificationManagerCompat.from(context)
        entries.take(4).forEach { en ->
            val n = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notify)
                .setContentTitle(en.title)
                .setContentText(en.text)
                .setSubText("New on PHOENIXA")
                .setLargeIcon(en.image?.let { loadBitmap(it) })
                .setContentIntent(openIntent(context, en.openId))
                .setAutoCancel(true)
                .setGroup(GROUP)
                .build()
            runCatching { nm.notify(en.openId.hashCode(), n) }
        }
        if (entries.size > 1) {
            // Summary that holds them together (and lists the rest when there are many)
            val style = NotificationCompat.InboxStyle().setSummaryText("New on PHOENIXA")
            entries.take(7).forEach { style.addLine("${it.title} · ${it.text}") }
            val summary = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notify)
                .setContentTitle("${entries.size} new on PHOENIXA")
                .setContentText(entries.joinToString(", ") { it.title })
                .setStyle(style)
                .setContentIntent(openIntent(context, null))
                .setAutoCancel(true)
                .setGroup(GROUP)
                .setGroupSummary(true)
                .build()
            runCatching { nm.notify(GROUP.hashCode(), summary) }
        }
    }

    fun canNotify(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    private fun openIntent(context: Context, id: String?): PendingIntent {
        val i = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (id != null) i.putExtra(EXTRA_OPEN_ID, id)
        return PendingIntent.getActivity(
            context, id?.hashCode() ?: 0, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private suspend fun loadBitmap(url: String): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            NovaApp.instance.api.http.newCall(Request.Builder().url(url).build()).execute().use { r ->
                if (!r.isSuccessful) null else r.body?.bytes()?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
            }
        }.getOrNull()
    }
}

class CheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        runCatching { NewTitles.check(applicationContext) }
        return Result.success()
    }
}
