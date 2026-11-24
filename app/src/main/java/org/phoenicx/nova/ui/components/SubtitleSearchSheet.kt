package org.phoenicx.nova.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import org.phoenicx.nova.data.MediaStreamInfo
import org.phoenicx.nova.data.RemoteSubs

/**
 * "Find subtitles online" on a movie / episode page: pick a language, pick a result,
 * the server downloads it and it's chosen for playback.
 */
@Composable
fun SubtitleSearchSheet(
    itemId: String,
    mediaSourceId: String?,
    onAdded: (MediaStreamInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    var lang by remember { mutableStateOf<Pair<String, String>?>(null) }
    var results by remember { mutableStateOf<List<RemoteSubs.Result>?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(lang) {
        val l = lang ?: return@LaunchedEffect
        results = null; message = null
        val (list, err) = RemoteSubs.search(itemId, l.first)
        results = list
        message = err ?: if (list.isEmpty()) "No ${l.second} subtitles found. (Your server needs the OpenSubtitles plugin.)" else null
    }

    val l = lang
    when {
        busy -> OptionSheet(
            "Adding subtitles…",
            listOf(SheetOption(message ?: "Downloading to your server…", "This takes a few seconds", false)),
            onSelect = { }, onDismiss = { },
        )

        l == null -> OptionSheet(
            "Find subtitles",
            RemoteSubs.languages.map { SheetOption(it.second, "", false) },
            onSelect = { i -> lang = RemoteSubs.languages[i] },
            onDismiss = onDismiss,
        )

        else -> {
            val list = results
            val opts = buildList {
                when {
                    list == null -> add(SheetOption("Searching…", "", false))
                    message != null -> add(SheetOption(message!!, "", false))
                    else -> list.forEach { add(SheetOption(it.name, it.detail, false)) }
                }
                add(SheetOption("Other language", "", false))
            }
            OptionSheet(
                "${l.second} subtitles",
                opts,
                onSelect = { i ->
                    if (i == opts.lastIndex) { lang = null; return@OptionSheet }
                    val r = list?.takeIf { message == null }?.getOrNull(i) ?: return@OptionSheet
                    busy = true; message = null
                    scope.launch {
                        val (added, err) = RemoteSubs.download(itemId, mediaSourceId, r)
                        busy = false
                        if (added != null) onAdded(added) else message = err
                    }
                },
                onDismiss = { lang = null },
            )
        }
    }
}
