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
    val posterWidth: Int get() = ((if (Lay.phone && !Lay.tablet) 108 else 128) * factor).toInt()
    val landscapeWidth: Int get() = (232 * factor).toInt()

    fun set(i: Int) {
        cardSizeIndex = i.coerceIn(0, 2)
        NovaApp.instance.prefs.cardSize = cardSizeIndex
    }
}

/** Layout values that differ between TV / landscape (wide) and phone / portrait (narrow, touch). */
object Lay {
    /**
     * Narrow portrait layout. Phones always use it; tablets use it when held upright and the
     * wide TV-style layout when turned sideways. Updated by MainActivity when the screen rotates.
     */
    var phone: Boolean by androidx.compose.runtime.mutableStateOf(!NovaApp.instance.isTv)
        private set

    val tablet: Boolean get() = NovaApp.instance.isTablet

    fun update(config: android.content.res.Configuration) {
        phone = !NovaApp.instance.isTv && config.orientation != android.content.res.Configuration.ORIENTATION_LANDSCAPE
    }

    /** Space between the screen edge and the content */
    val side: androidx.compose.ui.unit.Dp get() = when {
        !phone -> androidx.compose.ui.unit.Dp(56f)
        tablet -> androidx.compose.ui.unit.Dp(28f)
        else -> androidx.compose.ui.unit.Dp(16f)
    }
}
