package org.phoenicx.nova.syncplay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Groups
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.phoenicx.nova.data.Item
import org.phoenicx.nova.ui.components.FocusSurface
import org.phoenicx.nova.ui.components.OptionSheet
import org.phoenicx.nova.ui.components.SheetOption
import org.phoenicx.nova.ui.theme.Nova
import org.phoenicx.nova.ui.theme.NovaType

/** Work started from the menu keeps going after the menu closes */
private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

private fun say(msg: String) { SyncPlay.notice.value = System.nanoTime() to msg }

/**
 * The "Watch together" button on a title's page. Glows in the theme colours; once you're in a
 * group it shows a live dot and how many people are watching.
 */
@Composable
fun SyncPlayButton(
    item: Item?,
    /** Smaller version (Home banner, top right) */
    compact: Boolean = false,
    modifier: Modifier = Modifier,
    focusRequester: androidx.compose.ui.focus.FocusRequester? = null,
    /** Plain round button with just the icon (title pages), like the other buttons there */
    iconOnly: Boolean = false,
    /** With iconOnly: also write "Watch together" (TV) */
    showLabel: Boolean = false,
) {
    val group by SyncPlay.group
    // After the menu closes, the highlight comes back to this button (not to the first item on the page)
    val req = focusRequester ?: remember { androidx.compose.ui.focus.FocusRequester() }
    var opened by remember { mutableStateOf(false) }
    val sheetNow = SyncPlay.sheet.value
    LaunchedEffect(sheetNow) {
        if (sheetNow == null && opened) {
            opened = false
            repeat(5) { delay(60); if (runCatching { req.requestFocus() }.isSuccess) return@LaunchedEffect }
        }
    }
    val open = { opened = true; SyncPlay.sheet.value = SyncPlay.SheetRequest(item) }
    if (iconOnly) {
        val inGrp = group != null
        FocusSurface(
            onClick = open,
            modifier = modifier.height(48.dp),
            shape = RoundedCornerShape(24.dp),
            focusedScale = 1.06f,
            borderWidth = 0.dp,
            focusRequester = req,
        ) { focused ->
            val fg = if (focused) Nova.OnFocus else if (inGrp) Nova.Cyan else Nova.Text
            Row(
                Modifier
                    .fillMaxHeight()
                    .background(
                        if (focused) Brush.horizontalGradient(listOf(Nova.Focus, Nova.Focus))
                        else Brush.horizontalGradient(listOf(Color(0x24FFFFFF), Color(0x14FFFFFF)))
                    )
                    .border(1.dp, if (focused) Color.Transparent else Nova.Stroke, RoundedCornerShape(24.dp))
                    .padding(horizontal = if (showLabel) 22.dp else 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Groups, "Watch together", tint = fg, modifier = Modifier.size(22.dp))
                if (showLabel) {
                    Spacer(Modifier.width(10.dp))
                    Text("Watch together", style = NovaType.Button, color = fg, maxLines = 1)
                }
                if (inGrp) {
                    Spacer(Modifier.width(6.dp))
                    Text("${group?.members?.size ?: 1}", style = NovaType.Button, color = fg)
                }
            }
        }
        return
    }
    val inGroup = group != null
    val pulse = rememberInfiniteTransition(label = "live")
    val live by pulse.animateFloat(0.35f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "dot")
    FocusSurface(
        onClick = open,
        modifier = modifier.height(if (compact) 38.dp else 48.dp),
        shape = RoundedCornerShape(24.dp),
        focusedScale = if (compact) 1.08f else 1.06f,
        borderWidth = 0.dp,
        focusRequester = req,
    ) { focused ->
        val bg: Brush = when {
            focused -> Brush.horizontalGradient(listOf(Nova.Focus, Nova.Focus))
            inGroup -> Brush.horizontalGradient(listOf(Nova.Violet.copy(alpha = 0.55f), Nova.Cyan.copy(alpha = 0.45f)))
            // On artwork (Home banner): darker glass so it reads on any picture
            compact -> Brush.horizontalGradient(listOf(Color(0x99101418), Color(0x80101418)))
            else -> Brush.horizontalGradient(listOf(Nova.Violet.copy(alpha = 0.22f), Nova.Cyan.copy(alpha = 0.16f)))
        }
        val fg = if (focused) Nova.OnFocus else Nova.Text
        Row(
            Modifier
                .fillMaxHeight()
                .background(bg)
                .border(
                    1.dp,
                    if (focused) Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                    else Brush.horizontalGradient(listOf(Nova.Violet.copy(alpha = 0.8f), Nova.Cyan.copy(alpha = 0.8f))),
                    RoundedCornerShape(24.dp),
                )
                .padding(horizontal = if (compact) 14.dp else 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Groups, null, tint = if (focused) fg else if (compact && !inGroup) Nova.Cyan else fg,
                modifier = Modifier.size(if (compact) 19.dp else 22.dp))
            Spacer(Modifier.width(if (compact) 8.dp else 10.dp))
            Text(
                if (inGroup) "Watching together" else "Watch together",
                style = if (compact) NovaType.Label.copy(fontSize = 13.sp) else NovaType.Button, color = fg, maxLines = 1,
            )
            if (inGroup) {
                Spacer(Modifier.width(10.dp))
                Box(Modifier.size(8.dp).alpha(live).clip(CircleShape).background(Color(0xFF34C759)))
                Spacer(Modifier.width(6.dp))
                Text("${group?.members?.size ?: 1}", style = NovaType.Button, color = fg)
            }
        }
    }
}

/** The SyncPlay menu (start, join, play for everyone, leave). Put it once at the top of a screen. */
@Composable
fun SyncPlaySheet() {
    val req = SyncPlay.sheet.value ?: return
    val group by SyncPlay.group
    var page by remember(req) { mutableStateOf("menu") }          // menu · list · busy
    var groups by remember(req) { mutableStateOf<List<SyncPlay.ListedGroup>?>(null) }
    val close = { SyncPlay.sheet.value = null }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val item = req.item

    LaunchedEffect(page) {
        if (page == "list") groups = SyncPlay.listGroups()
    }

    val g = group
    when {
        page == "busy" -> OptionSheet(
            title = "SyncPlay",
            options = listOf(SheetOption("Connecting…", "One moment", false)),
            onSelect = { }, onDismiss = close,
        )

        g != null -> {
            val opts = buildList {
                if (SyncPlay.currentItemId != null && !SyncPlay.playerOpen) {
                    add(SheetOption("Back to the group's video", "Carry on watching with everyone", false) to "rejoin")
                }
                if (item != null && item.id.replace("-", "").lowercase() != SyncPlay.currentItemId) {
                    add(SheetOption("Play ${item.name} for everyone", "Everyone in the group starts it together", false) to "play")
                }
                add(SheetOption("Watching now (${g.members.size})", g.members.joinToString(", ").ifEmpty { "Just you" }, false) to "none")
                add(SheetOption("Leave watch party", "The others keep watching", false) to "leave")
            }
            OptionSheet(
                title = g.name,
                options = opts.map { it.first },
                onSelect = { i ->
                    when (opts[i].second) {
                        "play" -> { close(); uiScope.launch { SyncPlay.playForEveryone(item!!) } }
                        "rejoin" -> { close(); SyncPlay.rejoinPlayer(ctx) }
                        "leave" -> { close(); SyncPlay.leave() }
                    }
                },
                onDismiss = close,
            )
        }

        page == "list" -> {
            val list = groups
            val opts = when {
                list == null -> listOf(SheetOption("Looking for watch parties…", "", false))
                list.isEmpty() -> listOf(SheetOption("No watch parties right now", "Start one, and others can join it", false))
                else -> list.map { l ->
                    SheetOption(
                        l.name,
                        listOfNotNull(
                            l.members.joinToString(", ").ifEmpty { null },
                            when (l.state) { "Playing" -> "Playing"; "Paused" -> "Paused"; "Waiting" -> "Starting"; else -> null },
                        ).joinToString(" · "),
                        false,
                    )
                }
            }
            OptionSheet(
                title = "Join a watch party",
                options = opts,
                onSelect = { i ->
                    val l = list?.getOrNull(i)
                    if (l == null) { if (list != null) page = "menu" }
                    else {
                        page = "busy"
                        uiScope.launch {
                            val err = SyncPlay.join(l.id)
                            SyncPlay.sheet.value = null
                            if (err != null) say(err)
                        }
                    }
                },
                onDismiss = { page = "menu" },
            )
        }

        else -> {
            OptionSheet(
                title = "Watch together",
                options = listOf(
                    SheetOption(
                        "Start a watch party",
                        if (item != null) "Play ${item.name} for everyone who joins, in sync" else "Create a group others can join",
                        false,
                    ),
                    SheetOption("Join a watch party", "See the groups on your server", false),
                ),
                onSelect = { i ->
                    if (i == 0) {
                        page = "busy"
                        uiScope.launch {
                            val err = SyncPlay.create(item)
                            SyncPlay.sheet.value = null
                            if (err != null) say(err)
                        }
                    } else page = "list"
                },
                onDismiss = close,
            )
        }
    }
}

/** Short SyncPlay messages ("Divya joined", "Waiting for everyone…") */
@Composable
fun SyncPlayNotice(alignment: Alignment, modifier: Modifier = Modifier) {
    val n = SyncPlay.notice.value
    var visible by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    LaunchedEffect(n) {
        if (n == null) return@LaunchedEffect
        text = n.second; visible = true
        delay(3500)
        visible = false
    }
    Box(Modifier.fillMaxSize().then(modifier), contentAlignment = alignment) {
        AnimatedVisibility(
            visible,
            enter = fadeIn() + slideInVertically { if (alignment == Alignment.TopCenter) -it else it },
            exit = fadeOut() + slideOutVertically { if (alignment == Alignment.TopCenter) -it else it },
        ) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xEE101418))
                    .border(1.dp, Brush.horizontalGradient(listOf(Nova.Violet, Nova.Cyan)), RoundedCornerShape(22.dp))
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Groups, null, tint = Nova.Cyan, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(text, style = NovaType.Label.copy(fontSize = 14.sp), color = Nova.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
