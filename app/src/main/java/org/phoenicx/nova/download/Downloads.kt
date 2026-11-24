package org.phoenicx.nova.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.StatFs
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.phoenicx.nova.NovaApp
import org.phoenicx.nova.R
import org.phoenicx.nova.data.ExternalSub
import org.phoenicx.nova.data.Item
import org.phoenicx.nova.data.MediaStreamInfo
import org.phoenicx.nova.data.PlaybackSource
import org.phoenicx.nova.data.PlaySelection
import org.phoenicx.nova.player.DeviceProfile
import java.io.File
import java.io.RandomAccessFile

/** One downloaded (or downloading) movie or episode */
data class DownloadRec(
    val id: String,
    val name: String,
    val type: String,
    val year: Int?,
    val overview: String,
    val runTimeTicks: Long,
    val seriesId: String?,
    val seriesName: String?,
    val season: Int?,
    val episode: Int?,
    val profileKey: String,
    val mediaSourceId: String?,
    val audioIndex: Int?,
    val subtitleIndex: Int?,
    val totalBytes: Long,
    val doneBytes: Long,
    /** queued · downloading · done · failed */
    val state: String,
    val error: String? = null,
    val resumeMs: Long = 0,
    val addedAt: Long = System.currentTimeMillis(),
    val videoFile: String? = null,
    /** Track info saved with the file, so audio / subtitle names and choices work offline */
    val source: JSONObject? = null,
) {
    val done get() = state == "done"
    val title get() = if (type == "Episode" && seriesName != null)
        "$seriesName · " + (if (season != null && episode != null) "S$season:E$episode · " else "") + name
    else name + (year?.let { " ($it)" } ?: "")
    val percent get() = if (totalBytes > 0) ((doneBytes * 100) / totalBytes).toInt().coerceIn(0, 100) else 0

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("type", type).put("year", year ?: JSONObject.NULL)
        .put("overview", overview).put("runTimeTicks", runTimeTicks)
        .put("seriesId", seriesId ?: JSONObject.NULL).put("seriesName", seriesName ?: JSONObject.NULL)
        .put("season", season ?: JSONObject.NULL).put("episode", episode ?: JSONObject.NULL)
        .put("profileKey", profileKey).put("mediaSourceId", mediaSourceId ?: JSONObject.NULL)
        .put("audioIndex", audioIndex ?: JSONObject.NULL).put("subtitleIndex", subtitleIndex ?: JSONObject.NULL)
        .put("totalBytes", totalBytes).put("doneBytes", doneBytes).put("state", state)
        .put("error", error ?: JSONObject.NULL).put("resumeMs", resumeMs).put("addedAt", addedAt)
        .put("videoFile", videoFile ?: JSONObject.NULL).put("source", source ?: JSONObject.NULL)

    companion object {
        fun parse(o: JSONObject): DownloadRec {
            fun s(k: String) = if (o.isNull(k)) null else o.optString(k)
            fun i(k: String) = if (o.isNull(k) || !o.has(k)) null else o.optInt(k)
            return DownloadRec(
                id = o.getString("id"), name = o.optString("name"), type = o.optString("type"), year = i("year"),
                overview = o.optString("overview"), runTimeTicks = o.optLong("runTimeTicks"),
                seriesId = s("seriesId"), seriesName = s("seriesName"), season = i("season"), episode = i("episode"),
                profileKey = o.optString("profileKey"), mediaSourceId = s("mediaSourceId"),
                audioIndex = i("audioIndex"), subtitleIndex = i("subtitleIndex"),
                totalBytes = o.optLong("totalBytes"), doneBytes = o.optLong("doneBytes"), state = o.optString("state"),
                error = s("error"), resumeMs = o.optLong("resumeMs"), addedAt = o.optLong("addedAt"),
                videoFile = s("videoFile"), source = o.optJSONObject("source"),
            )
        }
    }
}

/**
 * Downloads for watching offline (phones and tablets).
 *
 * Files go into PHOENIXA's private storage, which other apps and file managers can't open,
 * and they're left out of backups. They play only inside PHOENIXA.
 */
object Downloads {
    private const val CHANNEL = "downloads"

    /** Everything on this device (all profiles); the UI shows the current profile's */
    val all = mutableStateOf<List<DownloadRec>>(emptyList())
    private var loaded = false

    private fun app() = NovaApp.instance
    fun root(ctx: Context = app()): File = File(ctx.filesDir, "downloads").apply { mkdirs() }
    fun dir(id: String): File = File(root(), id)
    private fun index() = File(root(), "index.json")

    fun mine(): List<DownloadRec> {
        ensureLoaded()
        val key = app().prefs.activeKey ?: ""
        return all.value.filter { it.profileKey == key }.sortedByDescending { it.addedAt }
    }

    fun get(id: String): DownloadRec? { ensureLoaded(); return all.value.firstOrNull { it.id == id && it.profileKey == (app().prefs.activeKey ?: "") } }
    fun playable(id: String): DownloadRec? = get(id)?.takeIf { it.done && it.videoFile != null && File(dir(id), it.videoFile).exists() }

    @Synchronized
    fun ensureLoaded() {
        if (loaded) return
        loaded = true
        all.value = runCatching {
            val a = JSONArray(index().readText())
            (0 until a.length()).mapNotNull { runCatching { DownloadRec.parse(a.getJSONObject(it)) }.getOrNull() }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    private fun save() {
        runCatching {
            val a = JSONArray()
            all.value.forEach { a.put(it.toJson()) }
            val tmp = File(root(), "index.tmp")
            tmp.writeText(a.toString())
            tmp.renameTo(index())
        }
    }

    @Synchronized
    fun update(id: String, persist: Boolean = true, f: (DownloadRec) -> DownloadRec) {
        ensureLoaded()
        all.value = all.value.map { if (it.id == id) f(it) else it }
        if (persist) save()
    }

    /** Space left on the phone, in bytes */
    fun freeBytes(): Long = runCatching { StatFs(root().path).availableBytes }.getOrDefault(0L)
    fun usedBytes(): Long = mine().sumOf { it.doneBytes }

    /**
     * Start downloading a movie or episode. Returns an error message, or null when it started.
     */
    suspend fun start(item: Item, sel: PlaySelection?): String? = withContext(Dispatchers.IO) {
        ensureLoaded()
        val api = app().api
        if (get(item.id) != null) return@withContext null
        // The server owner decides who may download
        val (code, body) = runCatching { api.raw("GET", "/Users/${api.userId}") }.getOrElse { return@withContext "Couldn't reach the server" }
        if (code == 200) {
            val allowed = runCatching { JSONObject(body).optJSONObject("Policy")?.optBoolean("EnableContentDownloading", true) }.getOrNull() ?: true
            if (!allowed) return@withContext "Your account isn't allowed to download. The server owner can turn on \"Allow media downloading\" for you (Dashboard → Users)."
        }
        val full = runCatching { api.item(item.id) }.getOrElse { item }
        val ms = full.mediaSources.firstOrNull { it.id == sel?.mediaSourceId } ?: full.mediaSources.firstOrNull()
        val size = ms?.size ?: 0L
        if (size > 0 && size + 300L * 1024 * 1024 > freeBytes()) {
            return@withContext "Not enough space: it needs ${formatSize(size)}, your phone has ${formatSize(freeBytes())} free."
        }
        val rec = DownloadRec(
            id = full.id, name = full.name, type = full.type, year = full.year, overview = full.overview,
            runTimeTicks = full.runTimeTicks, seriesId = full.seriesId, seriesName = full.seriesName,
            season = full.parentIndexNumber, episode = full.indexNumber,
            profileKey = app().prefs.activeKey ?: "", mediaSourceId = ms?.id ?: sel?.mediaSourceId,
            audioIndex = sel?.audioIndex, subtitleIndex = sel?.subtitleIndex,
            totalBytes = size, doneBytes = 0, state = "queued",
        )
        dir(full.id).mkdirs()
        // Poster, for the Downloads page (works offline)
        val poster = File(dir(full.id), "poster.jpg")
        runCatching { download(api.image(full.id, "Primary", null, 400)!!, poster) }
            .onFailure { runCatching { download(api.image(full.seriesId, "Primary", null, 400)!!, poster) } }
        synchronized(this@Downloads) { all.value = all.value.filterNot { it.id == full.id } + rec; save() }
        enqueue(full.id)
        null
    }

    /** One background task works through the list, one title at a time */
    @Suppress("UNUSED_PARAMETER")
    fun enqueue(id: String? = null) {
        val req = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .addTag("download")
            .build()
        WorkManager.getInstance(app()).enqueueUniqueWork("downloads", ExistingWorkPolicy.APPEND_OR_REPLACE, req)
    }

    /** App start: carry on with anything still waiting */
    fun resumePending() {
        ensureLoaded()
        if (all.value.any { it.state == "queued" || it.state == "downloading" }) enqueue()
    }

    fun retry(id: String) {
        update(id) { it.copy(state = "queued", error = null) }
        enqueue(id)
    }

    /** Stop (if downloading) and remove the files */
    fun delete(id: String) {
        // (The background task notices it's gone and stops that download)
        synchronized(this) {
            all.value = all.value.filterNot { it.id == id && it.profileKey == (app().prefs.activeKey ?: "") }
            save()
        }
        // Another profile on this phone may have the same title downloaded: keep the files then
        if (all.value.none { it.id == id }) dir(id).deleteRecursively()
    }

    fun saveResume(id: String, ms: Long) = update(id) { it.copy(resumeMs = ms) }

    /** Rebuilds the saved track info into what the player uses */
    fun sourceFor(rec: DownloadRec): PlaybackSource? {
        val o = rec.source ?: return null
        val file = File(dir(rec.id), rec.videoFile ?: return null)
        val streams = o.optJSONArray("streams")?.let { a -> (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let(::streamFrom) } } ?: emptyList()
        val subs = o.optJSONArray("subs")?.let { a ->
            (0 until a.length()).mapNotNull { i ->
                val s = a.optJSONObject(i) ?: return@mapNotNull null
                val f = File(dir(rec.id), s.optString("file"))
                if (!f.exists()) return@mapNotNull null
                ExternalSub(android.net.Uri.fromFile(f).toString(), s.optString("language").ifBlank { null }, s.optString("title"), "text/vtt", s.optInt("index", -1))
            }
        } ?: emptyList()
        fun ints(k: String) = o.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.optInt(it) } } ?: emptyList()
        return PlaybackSource(
            url = android.net.Uri.fromFile(file).toString(),
            mediaSourceId = o.optString("mediaSourceId"),
            playSessionId = null,
            isTranscoding = false,
            externalSubs = subs,
            embeddedAudio = ints("embeddedAudio"),
            embeddedSubs = ints("embeddedSubs"),
            streams = streams,
            defaultAudioIndex = if (o.isNull("defaultAudioIndex")) null else o.optInt("defaultAudioIndex"),
            defaultSubtitleIndex = if (o.isNull("defaultSubtitleIndex")) null else o.optInt("defaultSubtitleIndex"),
        )
    }

    internal fun sourceJson(src: PlaybackSource, subFiles: List<Pair<ExternalSub, String>>): JSONObject = JSONObject()
        .put("mediaSourceId", src.mediaSourceId)
        .put("embeddedAudio", JSONArray(src.embeddedAudio))
        .put("embeddedSubs", JSONArray(src.embeddedSubs))
        .put("defaultAudioIndex", src.defaultAudioIndex ?: JSONObject.NULL)
        .put("defaultSubtitleIndex", src.defaultSubtitleIndex ?: JSONObject.NULL)
        .put("streams", JSONArray().apply { src.streams.forEach { put(streamJson(it)) } })
        .put("subs", JSONArray().apply {
            subFiles.forEach { (s, f) -> put(JSONObject().put("file", f).put("language", s.language ?: "").put("title", s.title).put("index", s.index)) }
        })

    private fun streamJson(s: MediaStreamInfo) = JSONObject()
        .put("type", s.type).put("codec", s.codec).put("displayTitle", s.displayTitle).put("width", s.width).put("height", s.height)
        .put("videoRange", s.videoRange).put("channels", s.channels).put("index", s.index).put("language", s.language ?: JSONObject.NULL)
        .put("title", s.title ?: JSONObject.NULL).put("isDefault", s.isDefault).put("isForced", s.isForced)
        .put("isExternal", s.isExternal).put("isTextSubtitle", s.isTextSubtitle).put("videoRangeType", s.videoRangeType)

    private fun streamFrom(o: JSONObject) = MediaStreamInfo(
        type = o.optString("type"), codec = o.optString("codec"), displayTitle = o.optString("displayTitle"),
        width = o.optInt("width"), height = o.optInt("height"), videoRange = o.optString("videoRange"), channels = o.optInt("channels"),
        index = o.optInt("index"), language = if (o.isNull("language")) null else o.optString("language"),
        title = if (o.isNull("title")) null else o.optString("title"), isDefault = o.optBoolean("isDefault"),
        isForced = o.optBoolean("isForced"), isExternal = o.optBoolean("isExternal"),
        isTextSubtitle = o.optBoolean("isTextSubtitle"), videoRangeType = o.optString("videoRangeType"),
    )

    /** Small files (poster, subtitles) */
    internal fun download(url: String, to: File) {
        val req = Request.Builder().url(url).header("Authorization", app().api.authHeader()).build()
        app().api.http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) error("HTTP ${r.code}")
            to.outputStream().use { out -> r.body!!.byteStream().copyTo(out) }
        }
    }

    fun channel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(NotificationChannel(CHANNEL, "Downloads", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Progress of movies and episodes downloading for offline viewing"
                })
            }
        }
    }

    internal fun notification(ctx: Context, title: String, text: String, percent: Int, ongoing: Boolean) =
        NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(title)
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(ongoing)
            .setSilent(true)
            .apply { if (ongoing) setProgress(100, percent, percent == 0) }
            .setContentIntent(
                android.app.PendingIntent.getActivity(
                    ctx, 4242, android.content.Intent(ctx, org.phoenicx.nova.MainActivity::class.java)
                        .putExtra(OPEN_DOWNLOADS, true)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
                )
            )
            .build()

    const val OPEN_DOWNLOADS = "open_downloads"
}

fun formatSize(b: Long): String = when {
    b >= 1L shl 30 -> String.format(java.util.Locale.US, "%.1f GB", b / (1L shl 30).toDouble())
    b >= 1L shl 20 -> String.format(java.util.Locale.US, "%.0f MB", b / (1L shl 20).toDouble())
    else -> String.format(java.util.Locale.US, "%.0f KB", b / 1024.0)
}

/** Downloads the waiting titles one after another in the background (screen off is fine; resumes after a dropped connection) */
class DownloadWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    private val notifId = 0x0D10AD
    private var currentTitle = "Downloading"

    override suspend fun getForegroundInfo(): ForegroundInfo = info("Preparing download…", 0)

    private fun info(text: String, percent: Int): ForegroundInfo {
        Downloads.channel(applicationContext)
        val n = Downloads.notification(applicationContext, currentTitle, text, percent, ongoing = true)
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(notifId, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(notifId, n)
    }

    private fun gone(id: String) = Downloads.all.value.none { it.id == id }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Downloads.ensureLoaded()
        runCatching { setForeground(info("Starting…", 0)) }
        while (isActive) {
            val next = Downloads.all.value.filter { it.state == "queued" || it.state == "downloading" }.minByOrNull { it.addedAt }
                ?: break
            currentTitle = next.title
            try {
                downloadOne(next)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (gone(next.id)) { Downloads.dir(next.id).takeIf { Downloads.all.value.none { r -> r.id == next.id } }?.deleteRecursively(); continue }
                if (e is java.io.IOException && runAttemptCount < 8) {
                    // Connection dropped: try again a little later, from where it stopped
                    Downloads.update(next.id) { it.copy(state = "queued", error = "Connection lost, trying again…") }
                    return@withContext Result.retry()
                }
                Downloads.update(next.id) { it.copy(state = "failed", error = e.message ?: "Download failed") }
            }
        }
        Result.success()
    }

    private suspend fun downloadOne(rec: DownloadRec) {
        val id = rec.id
        val api = NovaApp.instance.api
        Downloads.update(id) { it.copy(state = "downloading", error = null) }
        // A fresh link every time (links to TorBox can expire)
        val sel = PlaySelection(mediaSourceId = rec.mediaSourceId, audioIndex = rec.audioIndex, subtitleIndex = rec.subtitleIndex)
        val src = api.playbackInfo(id, 0, DeviceProfile.get(progressive = false), allowDirect = true, forceDirect = true, sel = sel)
        if (src.isTranscoding) {
            Downloads.update(id) { it.copy(state = "failed", error = "This video can't be downloaded as it is") }
            return
        }
        val dir = Downloads.dir(id).apply { mkdirs() }
        val ext = src.url.substringBefore('?').substringAfterLast('.', "").takeIf { it.length in 2..4 && it != "stream" } ?: "mkv"
        val part = File(dir, "video.part")
        val final = File(dir, "video.$ext")

        var have = if (part.exists()) part.length() else 0L
        val req = Request.Builder().url(src.url).header("Authorization", api.authHeader())
            .apply { if (have > 0) header("Range", "bytes=$have-") }.build()
        val client = api.http.newBuilder().readTimeout(60, java.util.concurrent.TimeUnit.SECONDS).build()
        client.newCall(req).execute().use { r ->
            if (r.code != 416) {
                if (!r.isSuccessful) error("The server answered ${r.code}")
                if (have > 0 && r.code != 206) have = 0   // the server ignored the resume: start over
                val body = r.body ?: error("Empty answer")
                val total = if (r.code == 206) have + body.contentLength() else body.contentLength()
                if (total > 0) Downloads.update(id) { it.copy(totalBytes = total) }
                RandomAccessFile(part, "rw").use { out ->
                    out.setLength(have); out.seek(have)
                    val buf = ByteArray(256 * 1024)
                    val input = body.byteStream()
                    var lastUi = 0L; var lastSave = 0L
                    var lastBytes = have; var lastTime = System.currentTimeMillis()
                    while (true) {
                        if (!kotlinx.coroutines.currentCoroutineContext().isActive) throw kotlinx.coroutines.CancellationException()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        have += n
                        val now = System.currentTimeMillis()
                        if (now - lastUi > 1000) {
                            if (gone(id)) { out.close(); Downloads.dir(id).deleteRecursively(); return }
                            val speed = (have - lastBytes) * 1000 / (now - lastTime).coerceAtLeast(1)
                            lastBytes = have; lastTime = now; lastUi = now
                            val save = now - lastSave > 5000
                            if (save) lastSave = now
                            Downloads.update(id, persist = save) { it.copy(doneBytes = have) }
                            val pct = if (total > 0) ((have * 100) / total).toInt() else 0
                            runCatching { setForeground(info("$pct% · ${formatSize(have)} of ${formatSize(total)} · ${formatSize(speed)}/s", pct)) }
                        }
                    }
                }
            }
        }
        if (gone(id)) { Downloads.dir(id).deleteRecursively(); return }
        if (!part.renameTo(final)) error("Couldn't save the file")

        // Subtitle files that sit next to the video on the server
        val subFiles = src.externalSubs.mapIndexedNotNull { i, s ->
            val name = "sub_${s.index.takeIf { it >= 0 } ?: i}.vtt"
            runCatching { Downloads.download(s.url, File(dir, name)); s to name }.getOrNull()
        }
        Downloads.update(id) {
            it.copy(state = "done", doneBytes = final.length(), totalBytes = final.length(), videoFile = final.name,
                source = Downloads.sourceJson(src, subFiles), error = null)
        }
        runCatching {
            androidx.core.app.NotificationManagerCompat.from(applicationContext).notify(
                ("done" + id).hashCode(),
                Downloads.notification(applicationContext, "Downloaded", rec.title + " · ready to watch offline", 100, ongoing = false),
            )
        }
    }
}
