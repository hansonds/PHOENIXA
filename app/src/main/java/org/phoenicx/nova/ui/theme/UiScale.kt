package org.phoenicx.nova.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.phoenicx.nova.NovaApp

/** Live UI options that change instantly from Settings. */
object UiScale {
    val labels = listOf("Small", "Medium", "Large")
    private val factors = listOf(0.82f, 1f, 1.18f)

    var cardSizeIndex by mutableIntStateOf(NovaApp.instance.prefs.cardSize.coerceIn(0, 2))
        private set

    val factor: Float get() = factors[cardSizeIndex]

    /** Base sizes at Medium (dp) */
    val posterWidth: Int get() = (128 * factor).toInt()
    val landscapeWidth: Int get() = (232 * factor).toInt()

    fun set(i: Int) {
        cardSizeIndex = i.coerceIn(0, 2)
        NovaApp.instance.prefs.cardSize = cardSizeIndex
    }
}
