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
    val nav = remember { Navigator(if (app.api.isLoggedIn) Screen.Home else Screen.Login) }
    val homeState = remember { HomeState() }

    BackHandler(enabled = nav.stack.size > 1) { nav.back() }

    AnimatedContent(
        targetState = nav.current,
        transitionSpec = {
            fadeIn(tween(180)) togetherWith fadeOut(tween(120))
        },
        label = "screens",
        modifier = Modifier.fillMaxSize(),
    ) { screen ->
        when (screen) {
            Screen.Login -> LoginScreen(onLoggedIn = { nav.reset(Screen.Home) })
            Screen.Home -> HomeScreen(nav, homeState)
            is Screen.Lib -> LibraryScreen(nav, screen.library)
            Screen.Search -> SearchScreen(nav)
            Screen.Favorites -> FavoritesScreen(nav)
            Screen.Settings -> SettingsScreen(nav, onSignOut = {
                val tok = app.prefs.token
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch { app.api.logout(tok) }
                app.prefs.signOut()
                homeState.loaded = false
                homeState.featured = emptyList()
                homeState.rows = emptyList()
                nav.reset(Screen.Login)
            })
            is Screen.Detail -> DetailScreen(nav, screen.itemId)
            is Screen.Person -> PersonScreen(nav, screen.id, screen.name)
        }
    }
}
