package org.phoenicx.nova

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
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
import org.phoenicx.nova.ui.screens.SettingsScreen
import org.phoenicx.nova.ui.theme.NovaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NovaTheme { NovaRoot() }
        }
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
            (fadeIn(tween(320)) + scaleIn(tween(320), initialScale = 0.985f)) togetherWith fadeOut(tween(160))
        },
        label = "screens",
        modifier = Modifier.fillMaxSize(),
    ) { screen ->
        when (screen) {
            Screen.Login -> LoginScreen(onLoggedIn = { nav.reset(Screen.Home) })
            Screen.Home -> HomeScreen(nav, homeState)
            is Screen.Lib -> LibraryScreen(nav, screen.library)
            Screen.Search -> SearchScreen(nav)
            Screen.Settings -> SettingsScreen(nav, onSignOut = {
                app.prefs.signOut()
                homeState.loaded = false
                homeState.featured = emptyList()
                homeState.rows = emptyList()
                nav.reset(Screen.Login)
            })
            is Screen.Detail -> DetailScreen(nav, screen.itemId)
        }
    }
}
