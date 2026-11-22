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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.phoenicx.nova.MainActivity
import org.phoenicx.nova.NovaApp
import org.phoenicx.nova.R

/** One title in a push from the server */
data class PushTitle(
    val id: String,
    val type: String,            // Movie, Series or Episode
    val name: String,
    val year: Int?,
    val seriesId: String?,
    val seriesName: String?,
    val season: Int?,
    val episode: Int?,
)

/**
 * "New on PHOENIXA" notifications (phones and tablets). The server (Torvia) pushes them the moment
 * something is added, like WhatsApp does, so nothing runs in the background on the phone.
 * Tapping one opens the title.
 */
object NewTitles {
    const val EXTRA_OPEN_ID = "open_item_id"
    private const val CHANNEL = "new_titles"
    private const val MSG_CHANNEL = "server_messages"
    private const val GROUP = "org.phoenicx.nova.NEW_TITLES"

    /** The old every-10-minutes check is gone: clear anything earlier versions scheduled */
    fun stopOldChecks(context: Context) {
        runCatching {
            val wm = androidx.work.WorkManager.getInstance(context)
            wm.cancelUniqueWork("new-titles-check")
            wm.cancelUniqueWork("new-titles-next")
        }
    }

    fun parse(json: String?): List<PushTitle> = runCatching {
        val a = JSONArray(json ?: return emptyList())
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            fun str(k: String) = if (o.isNull(k)) null else o.optString(k).takeIf { it.isNotBlank() }
            fun num(k: String) = if (o.isNull(k) || !o.has(k)) null else o.optInt(k)
            PushTitle(
                id = str("id") ?: return@mapNotNull null,
                type = str("type") ?: "Movie",
                name = str("name") ?: return@mapNotNull null,
                year = num("year"), seriesId = str("seriesId"), seriesName = str("seriesName"),
                season = num("season"), episode = num("episode"),
            )
        }
    }.getOrDefault(emptyList())

    /** Show the titles the server just pushed */
    suspend fun show(context: Context, fresh: List<PushTitle>) {
        if (fresh.isEmpty() || !canNotify(context)) return
        val api = NovaApp.instance.api
        channel(context)
        fun poster(id: String?) = api.image(id, "Primary", null, 300)

        // Episodes of the same show become one line: "Lupin · 2 new episodes"
        data class Entry(val openId: String, val title: String, val text: String, val image: String?)
        val entries = buildList {
            fresh.filter { it.type != "Episode" }.forEach { m ->
                add(Entry(m.id, m.name + (m.year?.let { " ($it)" } ?: ""), if (m.type == "Series") "New series" else "New movie", poster(m.id)))
            }
            // A brand-new show already has its own line, so its episodes aren't listed again
            val newShows = fresh.filter { it.type == "Series" }.map { it.id }.toSet()
            fresh.filter { it.type == "Episode" && it.seriesId !in newShows }.groupBy { it.seriesId ?: it.id }.forEach { (sid, eps) ->
                val e = eps.first()
                val name = e.seriesName ?: e.name
                val label = if (e.season != null && e.episode != null) "S${e.season}:E${e.episode}" else null
                val text = if (eps.size == 1) listOfNotNull(label, e.name).joinToString(" · ") else "${eps.size} new episodes"
                add(Entry(sid, name, text, poster(e.seriesId ?: e.id)))
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

    /** A quick notification to prove push works (Settings → "Send a test") */
    fun showTest(context: Context) {
        if (!canNotify(context)) return
        channel(context)
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle("PHOENIXA notifications work")
            .setContentText("You'll hear here when new movies and shows are added")
            .setContentIntent(openIntent(context, null))
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(7001, n) }
    }

    /** A message the server owner wrote (Torvia → Push notifications → Send a message) */
    fun showMessage(context: Context, title: String?, body: String?) {
        val text = body?.trim().orEmpty().take(500)
        val head = title?.trim().orEmpty().take(80).ifEmpty { "PHOENIXA" }
        if (text.isEmpty() || !canNotify(context)) return
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(MSG_CHANNEL) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(MSG_CHANNEL, "Messages from the server", NotificationManager.IMPORTANCE_DEFAULT).apply {
                        description = "Announcements written by your server's owner"
                    }
                )
            }
        }
        val n = NotificationCompat.Builder(context, MSG_CHANNEL)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(head)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openIntent(context, null))
            .setAutoCancel(true)
            .build()
        // Each message gets its own notification, so a new one doesn't replace an unread one
        runCatching { NotificationManagerCompat.from(context).notify((System.currentTimeMillis() and 0x3fffffff).toInt(), n) }
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
