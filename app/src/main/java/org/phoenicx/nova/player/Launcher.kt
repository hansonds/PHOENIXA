package org.phoenicx.nova.player

import android.content.Context
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.phoenicx.nova.NovaApp
import org.phoenicx.nova.data.Item

object Launcher {
    /** Plays a movie/episode directly; for a series or season it finds the next episode to watch. */
    fun play(context: Context, scope: CoroutineScope, item: Item, fromStart: Boolean = false, sel: org.phoenicx.nova.data.PlaySelection? = null) {
        val api = NovaApp.instance.api
        // In a watch party, Play starts it for everyone in the group
        if (org.phoenicx.nova.syncplay.SyncPlay.inGroup) {
            // The title the group is already watching: just go back to it, in sync
            if (item.id.replace("-", "").lowercase() == org.phoenicx.nova.syncplay.SyncPlay.currentItemId &&
                org.phoenicx.nova.syncplay.SyncPlay.rejoinPlayer(context)) return
            scope.launch { org.phoenicx.nova.syncplay.SyncPlay.playForEveryone(item, fromStart) }
            return
        }
        scope.launch {
            try {
                val target: Item? = when (item.type) {
                    "Series" -> api.nextUpForSeries(item.id)
                        ?: api.episodes(item.id).let { eps -> eps.firstOrNull { !it.played } ?: eps.firstOrNull() }
                    "Season" -> api.episodes(item.seriesId ?: "", item.id).let { eps -> eps.firstOrNull { !it.played } ?: eps.firstOrNull() }
                    else -> item
                }
                if (target == null) {
                    Toast.makeText(context, "Nothing to play", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val ticks = if (fromStart || target.played) 0L else target.playbackPositionTicks
                // Downloaded on this phone: play the file (no data used); start where you got furthest
                val local = org.phoenicx.nova.download.Downloads.playable(target.id)
                if (local != null) {
                    val startTicks = if (fromStart) 0L else maxOf(ticks, local.resumeMs * 10_000)
                    PlayerActivity.startLocal(context, target.id, startTicks)
                    return@launch
                }
                PlayerActivity.start(context, target.id, ticks, if (target.id == item.id) sel else null)
            } catch (e: Exception) {
                Toast.makeText(context, e.message ?: "Playback failed", Toast.LENGTH_LONG).show()
            }
        }
    }
}
