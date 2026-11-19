package org.phoenicx.nova.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.delay
import org.phoenicx.nova.NovaApp
import org.phoenicx.nova.data.Profile
import org.phoenicx.nova.ui.components.FocusSurface
import org.phoenicx.nova.ui.components.NovaButton
import org.phoenicx.nova.ui.components.OptionSheet
import org.phoenicx.nova.ui.components.SheetOption
import org.phoenicx.nova.ui.theme.Lay
import org.phoenicx.nova.ui.theme.Nova
import org.phoenicx.nova.ui.theme.NovaType

/** Soft colours for people without a profile picture */
private val avatarColors = listOf(
    0xFFFF6B3D, 0xFF4FC3F7, 0xFFAB7BFF, 0xFF3DDC97, 0xFFFFC14D, 0xFFFF5C8A, 0xFF5C8DFF,
)

/** "Who's watching?": pick a saved profile, add someone, or remove one */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfilesScreen(
    onChoose: (Profile) -> Unit,
    onAdd: () -> Unit,
    onBack: (() -> Unit)?,
) {
    val prefs = NovaApp.instance.prefs
    var profiles by remember { mutableStateOf(prefs.profiles) }
    var manage by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<Profile?>(null) }
    var refocus by remember { mutableIntStateOf(0) }
    val firstRequester = remember { FocusRequester() }
    val active = prefs.activeKey
    val manyServers = profiles.map { it.server }.distinct().size > 1

    LaunchedEffect(refocus) { delay(120); runCatching { firstRequester.requestFocus() } }
    if (onBack != null) androidx.activity.compose.BackHandler { onBack() }

    val avatar = if (Lay.phone) 96.dp else 132.dp
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Nova.BgDeep, Nova.Bg)))) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(org.phoenicx.nova.R.drawable.logo_stacked),
                contentDescription = null,
                modifier = Modifier.size(if (Lay.phone) 84.dp else 100.dp),
            )
            Spacer(Modifier.height(18.dp))
            Text(
                if (manage) "Manage profiles" else "Who's watching?",
                style = NovaType.Display.copy(fontSize = if (Lay.phone) 30.sp else 42.sp), color = Nova.Text,
            )
            Spacer(Modifier.height(if (Lay.phone) 28.dp else 44.dp))
            FlowRow(
                Modifier.widthIn(max = 1100.dp),
                horizontalArrangement = Arrangement.spacedBy(if (Lay.phone) 22.dp else 40.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(26.dp),
            ) {
                profiles.forEachIndexed { i, p ->
                    ProfileTile(
                        name = p.userName,
                        sub = if (manyServers) p.server.substringAfter("://").substringBefore('/') else null,
                        size = avatar,
                        color = Color(avatarColors[(p.userName.hashCode() and 0x7fffffff) % avatarColors.size]),
                        image = NovaApp.instance.api.userImage(p),
                        badge = if (manage) Icons.Outlined.Close else null,
                        current = !manage && p.key == active && onBack != null,
                        requester = if (i == 0) firstRequester else null,
                    ) { if (manage) confirm = p else onChoose(p) }
                }
                if (!manage) {
                    ProfileTile(
                        name = "Add profile", sub = null, size = avatar, color = Color(0x22FFFFFF),
                        image = null, icon = Icons.Outlined.Add, badge = null, current = false,
                        requester = if (profiles.isEmpty()) firstRequester else null,
                    ) { onAdd() }
                }
            }
            Spacer(Modifier.height(if (Lay.phone) 30.dp else 46.dp))
            if (profiles.isNotEmpty()) {
                NovaButton(if (manage) "Done" else "Manage profiles", if (manage) null else Icons.Outlined.Edit) {
                    manage = !manage; refocus++
                }
            }
        }
    }

    confirm?.let { p ->
        OptionSheet(
            title = "Remove ${p.userName}?",
            options = listOf(
                SheetOption("Remove from this device", "Signs ${p.userName} out here. Nothing changes on the server.", false),
                SheetOption("Cancel", "", false),
            ),
            onSelect = { i ->
                if (i == 0) {
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launchLogout(p)
                    prefs.removeProfile(p)
                    profiles = prefs.profiles
                    if (profiles.isEmpty()) manage = false
                }
                confirm = null; refocus++
            },
            onDismiss = { confirm = null; refocus++ },
        )
    }
}

/** Ends that person's session on their server (best effort) */
private fun kotlinx.coroutines.CoroutineScope.launchLogout(p: Profile) {
    launch {
        runCatching {
            val req = okhttp3.Request.Builder()
                .url(p.server.trimEnd('/') + "/Sessions/Logout")
                .header(
                    "Authorization",
                    "MediaBrowser Client=\"PHOENIXA\", Device=\"${android.os.Build.MODEL}\", DeviceId=\"${p.deviceId}\", Version=\"1.0.0\", Token=\"${p.token}\"",
                )
                .post(ByteArray(0).toRequestBody())
                .build()
            NovaApp.instance.api.http.newCall(req).execute().close()
        }
    }
}

@Composable
private fun ProfileTile(
    name: String,
    sub: String?,
    size: androidx.compose.ui.unit.Dp,
    color: Color,
    image: String?,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    badge: androidx.compose.ui.graphics.vector.ImageVector?,
    current: Boolean,
    requester: FocusRequester?,
    onClick: () -> Unit,
) {
    Column(Modifier.width(size + 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        FocusSurface(onClick = onClick, shape = CircleShape, focusedScale = 1.1f, focusRequester = requester) { focused ->
            Box(
                Modifier.size(size).clip(CircleShape)
                    .background(Brush.linearGradient(listOf(color, color.copy(alpha = 0.55f))))
                    .border(if (focused) 4.dp else if (current) 3.dp else 0.dp, if (focused) Color.White else Nova.Cyan, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (icon != null) {
                    Icon(icon, null, tint = Color.White, modifier = Modifier.size(size * 0.42f))
                } else {
                    Text(name.take(1).uppercase(), style = NovaType.Display.copy(fontSize = (size.value * 0.42f).sp), color = Color.White)
                    if (image != null) {
                        AsyncImage(image, name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                }
                if (badge != null) {
                    Box(Modifier.fillMaxSize().background(Color(0x99000000)), contentAlignment = Alignment.Center) {
                        Icon(badge, null, tint = Color.White, modifier = Modifier.size(size * 0.36f))
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(name, style = NovaType.Section, color = Nova.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth())
        if (sub != null) Text(sub, style = NovaType.Label, color = Nova.TextFaint, maxLines = 1, overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}
