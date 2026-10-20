package org.phoenicx.nova.player

import android.content.Context
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.phoenicx.nova.NovaApp
import org.phoenicx.nova.data.Item

object Launcher {
    /** Plays a movie/episode directly; for a series or season it finds the next episode to watch. */
    fun play(context: Context, scope: CoroutineScope, item: Item, fromStart: Boolean = false) {
        val api = NovaApp.instance.api
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
                PlayerActivity.start(context, target.id, if (fromStart || target.played) 0L else target.playbackPositionTicks)
            } catch (e: Exception) {
                Toast.makeText(context, e.message ?: "Playback failed", Toast.LENGTH_LONG).show()
            }
        }
    }
}
