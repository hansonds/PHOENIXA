package org.phoenicx.nova.tv

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.phoenicx.nova.NovaApp
import org.phoenicx.nova.ui.theme.Nova
import org.phoenicx.nova.ui.theme.NovaType

/**
 * TVs get no push notifications, so messages from the server owner (Torvia → Push notifications →
 * Send a message) are picked up while PHOENIXA is open, every minute, and shown as a banner at
 * the top of the screen. It also appears over a playing movie, without pausing it.
 */
object ServerMessages {
    data class Msg(val id: String, val title: String, val body: String)

    /** Messages waiting to be shown, oldest first; the banner shows the first one */
    val queue = mutableStateOf<List<Msg>>(emptyList())

    private const val EVERY_MS = 60_000L
    private const val FRESH_MS = 24L * 60 * 60 * 1000   // the very first time, only messages from the last day

    private fun sp(ctx: Context) = ctx.getSharedPreferences("server_messages", Context.MODE_PRIVATE)

    /** Keep checking while this screen is on (TVs only) */
    fun watch(activity: ComponentActivity) {
        if (!NovaApp.instance.isTv) return
        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    runCatching { check(activity.applicationContext) }
                    delay(EVERY_MS)
                }
            }
        }
    }

    private suspend fun check(ctx: Context) {
        val app = NovaApp.instance
        val prefs = app.prefs
        if (!app.api.isLoggedIn) return
        val (code, body) = app.api.raw("GET", "/TorviaPush/Messages")
        if (code != 200) return   // older Torvia, or no Torvia on this server
        val arr = JSONObject(body).optJSONArray("messages") ?: return

        val key = "seen|" + prefs.activeKey
        val sp = sp(ctx)
        val firstTime = !sp.contains(key)
        val seen = sp.getString(key, "")!!.split(',').filter { it.isNotBlank() }.toMutableList()
        val now = System.currentTimeMillis()
        val fresh = mutableListOf<Msg>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id").takeIf { it.isNotBlank() } ?: continue
            if (id in seen) continue
            seen += id
            val sent = runCatching { java.time.Instant.parse(o.optString("sentAt")).toEpochMilli() }.getOrNull()
            if (firstTime && (sent == null || now - sent > FRESH_MS)) continue
            val text = o.optString("body").trim().take(500)
            if (text.isEmpty()) continue
            fresh += Msg(id, o.optString("title").trim().take(80).ifEmpty { "PHOENIXA" }, text)
        }
        // Remember only the last 60, so this never grows
        sp.edit().putString(key, seen.takeLast(60).joinToString(",")).apply()
        if (fresh.isNotEmpty()) {
            // The server lists newest first: show them oldest first
            val showing = queue.value.map { it.id }.toSet()
            queue.value = queue.value + fresh.reversed().filter { it.id !in showing }
        }
    }

    fun dismiss(id: String) {
        queue.value = queue.value.filterNot { it.id == id }
    }
}

/** The message banner, top centre, over everything (put it last in the screen's root Box) */
@Composable
fun ServerMessageBanner() {
    if (!NovaApp.instance.isTv) return
    val queue by ServerMessages.queue
    val msg = queue.firstOrNull()
    var shown by remember { mutableStateOf<ServerMessages.Msg?>(null) }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(msg?.id) {
        val m = msg ?: return@LaunchedEffect
        shown = m
        visible = true
        // Long enough to read: about 12 s, plus time for longer messages (at most 40 s)
        delay((12_000L + m.body.length * 60L).coerceAtMost(40_000L))
        visible = false
        delay(400)
        ServerMessages.dismiss(m.id)
    }

    Box(Modifier.fillMaxSize().padding(top = 28.dp), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible = visible && shown != null,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
        ) {
            val m = shown ?: return@AnimatedVisibility
            Row(
                Modifier
                    .widthIn(min = 360.dp, max = 720.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xF0101418))
                    .border(1.dp, Nova.Cyan.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
                    .padding(horizontal = 22.dp, vertical = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(Nova.Cyan.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Campaign, null, tint = Nova.Cyan, modifier = Modifier.size(26.dp))
                }
                Column(Modifier.weight(1f, fill = false)) {
                    Text(m.title, style = NovaType.Section, color = Nova.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(6.dp))
                    Text(m.body, style = NovaType.Body, color = Nova.TextDim, maxLines = 8, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(8.dp))
                    Text("Message from your server", style = NovaType.Label, color = Nova.TextFaint)
                }
                Spacer(Modifier.width(2.dp))
            }
        }
    }
}
