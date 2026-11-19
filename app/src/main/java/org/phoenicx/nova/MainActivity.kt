package org.phoenicx.nova

import kotlinx.coroutines.launch
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.phoenicx.nova.ui.Navigator
import org.phoenicx.nova.ui.Screen
import org.phoenicx.nova.ui.screens.DetailScreen
import org.phoenicx.nova.ui.screens.HomeScreen
import org.phoenicx.nova.ui.screens.HomeState
import org.phoenicx.nova.ui.screens.LibraryScreen
import org.phoenicx.nova.ui.screens.LoginScreen
import org.phoenicx.nova.ui.screens.SearchScreen
import org.phoenicx.nova.ui.screens.PersonScreen
import org.phoenicx.nova.ui.screens.FavoritesScreen
import org.phoenicx.nova.ui.screens.SettingsScreen
import org.phoenicx.nova.ui.theme.NovaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Draw behind the system bars and camera cut-out (Android 15+ default), handled with insets
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        org.phoenicx.nova.ui.theme.Lay.update(resources.configuration)
        if (!NovaApp.instance.isTv) {
            requestedOrientation = if (NovaApp.instance.isTablet) android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_USER
            else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            @Suppress("DEPRECATION")
            window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        immersiveOnPhone()
        setContent {
            NovaTheme { NovaRoot() }
        }
        openFromTvHome(intent)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        openFromTvHome(intent)
    }

    /** Picked from the TV home screen's "Continue watching" row: start playing straight away */
    private fun openFromTvHome(intent: android.content.Intent?) {
        val id = intent?.getStringExtra(org.phoenicx.nova.tv.WatchNext.EXTRA_PLAY_ID) ?: return
        intent.removeExtra(org.phoenicx.nova.tv.WatchNext.EXTRA_PLAY_ID)
        if (!NovaApp.instance.api.isLoggedIn) return
        val ticks = intent.getLongExtra(org.phoenicx.nova.tv.WatchNext.EXTRA_PLAY_TICKS, 0L)
        org.phoenicx.nova.player.PlayerActivity.start(this, id, ticks)
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        org.phoenicx.nova.ui.theme.Lay.update(newConfig)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) immersiveOnPhone()
    }
}

@Composable
fun NovaRoot() {
    val app = NovaApp.instance
    val prefs = app.prefs
    val nav = remember {
        val profiles = prefs.profiles
        val start = when {
            // More than one person on this device: ask who's watching
            profiles.size > 1 -> Screen.Profiles
            app.api.isLoggedIn -> Screen.Home
            profiles.size == 1 -> { prefs.switchTo(profiles[0]); Screen.Home }
            else -> Screen.Login
        }
        Navigator(start)
    }
    var homeState by remember { mutableStateOf(HomeState()) }

    // Everything that belongs to the signed-in person starts fresh
    fun freshSession() {
        org.phoenicx.nova.tv.WatchNext.sync(app)
        homeState = HomeState()
        nav.libraries.value = emptyList()
        nav.torvia.value = null
        nav.libraryPos.clear()
    }

    BackHandler(enabled = nav.stack.size > 1) { nav.back() }

    // Phones: a bottom bar (Home · Movies · Shows · Search · Profile) on the main pages
    val showBottom = org.phoenicx.nova.ui.theme.Lay.phone &&
        nav.current != Screen.Login && nav.current != Screen.Profiles
    androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
    AnimatedContent(
        targetState = nav.current,
        transitionSpec = {
            fadeIn(tween(180)) togetherWith fadeOut(tween(120))
        },
        label = "screens",
        modifier = Modifier.weight(1f).fillMaxSize(),
    ) { screen ->
        when (screen) {
            Screen.Login -> LoginScreen(
                onLoggedIn = { freshSession(); nav.reset(Screen.Home) },
                // Adding a profile: Back returns to "Who's watching?" (and keeps the previous sign-in)
                onCancel = if (prefs.profiles.isNotEmpty()) ({
                    prefs.profiles.firstOrNull { it.key == prefs.activeKey }?.let { prefs.switchTo(it) }
                        ?: prefs.profiles.firstOrNull()?.let { prefs.switchTo(it) }
                    nav.reset(Screen.Profiles)
                }) else null,
            )
            Screen.Profiles -> org.phoenicx.nova.ui.screens.ProfilesScreen(
                onChoose = { p ->
                    val same = p.key == prefs.activeKey && app.api.isLoggedIn
                    prefs.switchTo(p)
                    if (!same) freshSession()
                    if (same && nav.stack.size > 1) nav.back() else nav.reset(Screen.Home)
                },
                onAdd = { prefs.prepareNewSignIn(); nav.reset(Screen.Login) },
                onBack = if (nav.stack.size > 1 && app.api.isLoggedIn) ({ nav.back() }) else null,
            )
            Screen.Home -> HomeScreen(nav, homeState)
            is Screen.Lib -> LibraryScreen(nav, screen.library)
            Screen.Search -> SearchScreen(nav)
            Screen.Favorites -> FavoritesScreen(nav)
            Screen.Settings -> SettingsScreen(nav, onSignOut = {
                val tok = prefs.token
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch { app.api.logout(tok) }
                val me = prefs.profiles.firstOrNull { it.key == prefs.activeKey }
                if (me != null) prefs.removeProfile(me) else prefs.signOut()
                freshSession()
                nav.reset(if (prefs.profiles.isNotEmpty()) Screen.Profiles else Screen.Login)
            })
            is Screen.Detail -> DetailScreen(nav, screen.itemId)
            is Screen.Person -> PersonScreen(nav, screen.id, screen.name)
            Screen.Torvia -> org.phoenicx.nova.ui.screens.TorviaScreen(nav)
            is Screen.TorviaTitle -> org.phoenicx.nova.ui.screens.TorviaTitleScreen(nav, screen.type, screen.tmdbId, screen.name)
        }
    }
    if (showBottom) org.phoenicx.nova.ui.components.BottomNav(nav)
    }
}
