package org.phoenicx.nova

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isBackPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.delay
import org.phoenicx.nova.data.JellyfinApi
import org.phoenicx.nova.data.Library
import org.phoenicx.nova.data.Prefs
import org.phoenicx.nova.player.Player
import org.phoenicx.nova.ui.Nova
import org.phoenicx.nova.ui.NovaTheme
import org.phoenicx.nova.ui.NovaType
import org.phoenicx.nova.ui.PlayerScreen
import org.phoenicx.nova.ui.PlayerUi
import org.phoenicx.nova.ui.TopBar
import org.phoenicx.nova.ui.screens.DetailScreen
import org.phoenicx.nova.ui.screens.FavoritesScreen
import org.phoenicx.nova.ui.screens.HomeScreen
import org.phoenicx.nova.ui.screens.LibraryScreen
import org.phoenicx.nova.ui.screens.LoginScreen
import org.phoenicx.nova.ui.screens.PersonScreen
import org.phoenicx.nova.ui.screens.SearchScreen
import org.phoenicx.nova.ui.screens.SettingsScreen

/** App-wide objects */
object App {
    val prefs = Prefs()
    val api = JellyfinApi(prefs)
    const val NAME = "PHOENIXA"
    val version: String = System.getProperty("jpackage.app-version") ?: "1.1.0"

    /** Libraries of the signed-in user (shown in the top bar) */
    var libraries by mutableStateOf<List<Library>>(emptyList())

    /** Goes up after playback ends, so screens reload watch progress */
    var refreshTick by mutableIntStateOf(0)

    var loggedIn by mutableStateOf(api.isLoggedIn)

    fun signOut() {
        prefs.signOut()
        libraries = emptyList()
        loggedIn = false
    }
}

/** Every page of the app */
sealed interface Screen {
    data object Home : Screen
    data object Favorites : Screen
    data object Settings : Screen
    data object Search : Screen
    data class Lib(val library: Library) : Screen
    data class Detail(val id: String) : Screen
    data class Person(val id: String, val name: String) : Screen
}

class Navigator {
    val stack = mutableStateListOf<Screen>(Screen.Home)
    val current: Screen get() = stack.last()
    val canGoBack: Boolean get() = stack.size > 1

    fun go(s: Screen) {
        if (current == s) return
        // Top-level pages replace each other instead of piling up
        if (s is Screen.Home) { stack.clear(); stack.add(Screen.Home); return }
        stack.add(s)
    }

    fun back(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }
}

@OptIn(ExperimentalComposeUiApi::class)
fun main() = application {
    val windowState = rememberWindowState(
        width = 1440.dp, height = 900.dp,
        position = WindowPosition(Alignment.Center),
    )
    val nav = androidx.compose.runtime.remember { Navigator() }
    var placementBeforeFullscreen by androidx.compose.runtime.remember { mutableStateOf(WindowPlacement.Floating) }
    val isFullscreen = windowState.placement == WindowPlacement.Fullscreen
    fun toggleFullscreen() {
        if (windowState.placement == WindowPlacement.Fullscreen) {
            windowState.placement = placementBeforeFullscreen
        } else {
            placementBeforeFullscreen = windowState.placement
            windowState.placement = WindowPlacement.Fullscreen
        }
    }

    Window(
        onCloseRequest = { Player.stop(); exitApplication() },
        title = App.NAME,
        icon = painterResource("app_icon.png"),
        state = windowState,
        onPreviewKeyEvent = { e ->
            val now = Player.current
            when {
                e.type != KeyEventType.KeyDown -> false
                now != null -> PlayerUi.onKey(e.key, now.session, ::toggleFullscreen, isFullscreen)
                e.key == Key.Escape && App.loggedIn -> nav.back()
                else -> false
            }
        },
    ) {
        window.minimumSize = java.awt.Dimension(1024, 640)

        // Full screen while a video plays (if chosen in Settings), back to normal afterwards
        val playing = Player.current != null
        LaunchedEffect(playing) {
            if (playing && App.prefs.playerFullscreen && windowState.placement != WindowPlacement.Fullscreen) toggleFullscreen()
            if (!playing && windowState.placement == WindowPlacement.Fullscreen) toggleFullscreen()
        }

        NovaTheme {
            Box(
                Modifier.fillMaxSize().onPointerEvent(PointerEventType.Press) { ev ->
                    // Mouse "back" side button
                    if (ev.buttons.isBackPressed && App.loggedIn) {
                        if (Player.current != null) Player.close() else nav.back()
                    }
                }
            ) {
                if (!App.loggedIn) {
                    LoginScreen(onLoggedIn = { nav.stack.clear(); nav.stack.add(Screen.Home); App.loggedIn = true })
                } else {
                    LaunchedEffect(Unit) {
                        runCatching { App.libraries = App.api.libraries() }
                    }
                    Content(nav)
                    TopBar(nav)
                }
                Player.current?.let { now ->
                    PlayerScreen(now, isFullscreen, ::toggleFullscreen)
                }
                PlayerToast(Modifier.align(Alignment.BottomCenter))
            }
        }
    }
}

@Composable
private fun Content(nav: Navigator) {
    when (val s = nav.current) {
        Screen.Home -> HomeScreen(nav)
        Screen.Favorites -> FavoritesScreen(nav)
        Screen.Settings -> SettingsScreen(nav)
        Screen.Search -> SearchScreen(nav)
        is Screen.Lib -> LibraryScreen(nav, s.library)
        is Screen.Detail -> DetailScreen(nav, s.id)
        is Screen.Person -> PersonScreen(nav, s.id, s.name)
    }
}

/** Small message at the bottom while the player opens, or when something goes wrong */
@Composable
private fun PlayerToast(modifier: Modifier) {
    val msg = Player.status
    LaunchedEffect(msg, Player.statusIsError) {
        if (msg != null && Player.statusIsError) { delay(7000); Player.clearStatus() }
    }
    AnimatedVisibility(msg != null, modifier = modifier.padding(bottom = 36.dp), enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier
                .background(if (Player.statusIsError) Nova.Rose.copy(alpha = 0.92f) else Nova.SurfaceHi.copy(alpha = 0.96f), RoundedCornerShape(24.dp))
                .padding(horizontal = 22.dp, vertical = 12.dp)
        ) {
            Text(msg ?: "", style = NovaType.Button, color = if (Player.statusIsError) Nova.OnFocus else Nova.Text)
        }
    }
}
