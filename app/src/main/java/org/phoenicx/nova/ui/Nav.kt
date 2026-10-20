package org.phoenicx.nova.ui

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import org.phoenicx.nova.data.Library

sealed class Screen {
    data object Login : Screen()
    data object Home : Screen()
    data class Lib(val library: Library) : Screen()
    data object Search : Screen()
    data object Favorites : Screen()
    data object Settings : Screen()
    data class Detail(val itemId: String) : Screen()
    data class Person(val id: String, val name: String) : Screen()
}

class Navigator(start: Screen) {
    val stack = mutableStateListOf(start)
    val libraries = mutableStateOf<List<Library>>(emptyList())

    val current: Screen get() = stack.last()

    /** Set when a screen was opened from the top bar, so it keeps focus on the top bar. */
    var focusNavOnEnter = false

    /** Remembers where you were in each library grid: libraryId -> (sortIndex, itemIndex) */
    val libraryPos = mutableMapOf<String, Pair<Int, Int>>()

    fun push(s: Screen) { stack.add(s) }

    /** Top-level tabs replace everything above Home. */
    fun tab(s: Screen) {
        focusNavOnEnter = true
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
        if (s != Screen.Home) stack.add(s)
    }

    fun reset(s: Screen) {
        stack.clear(); stack.add(s)
    }

    fun back(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }
}
