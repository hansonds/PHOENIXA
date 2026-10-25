package org.phoenicx.nova.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.phoenicx.nova.NovaApp
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** One colour theme. "accent" and "accent2" are the two highlight colours (the default theme's cyan and violet). */
data class NovaPalette(
    val name: String,
    val bg: Color,
    val bgDeep: Color,
    val surface: Color,
    val surfaceHi: Color,
    val accent: Color,
    val accent2: Color,
    val glow1: Color,
    val glow2: Color,
    val text: Color = Color(0xFFEEF1FA),
    val textDim: Color = Color(0xFFA9B0C8),
    val textFaint: Color = Color(0xFF6E7690),
    val focus: Color = Color(0xFFF4F7FF),
    val onFocus: Color = Color(0xFF12152A),
)

/** All themes. The first one is the original PHOENIXA look and stays the default. */
object NovaThemes {
    val all = listOf(
        NovaPalette("Nova (default)", Color(0xFF121522), Color(0xFF0D0F19), Color(0xFF1B2032), Color(0xFF262C44),
            Color(0xFF6CE5F5), Color(0xFFA58BFF), Color(0x332A3A7A), Color(0x22533A8A)),
        NovaPalette("Phoenix Ember", Color(0xFF181012), Color(0xFF100A0B), Color(0xFF241719), Color(0xFF332022),
            Color(0xFFFF8A4C), Color(0xFFFFC857), Color(0x337A2A1A), Color(0x228A4A1A),
            text = Color(0xFFFAF0EC), textDim = Color(0xFFC8B0A9), textFaint = Color(0xFF8A7470),
            focus = Color(0xFFFFF4EC), onFocus = Color(0xFF2A1210)),
        NovaPalette("Midnight Blue", Color(0xFF0C1220), Color(0xFF080C16), Color(0xFF141C30), Color(0xFF1E2842),
            Color(0xFF5AB0FF), Color(0xFF7C8CFF), Color(0x33204A8A), Color(0x22303A8A),
            textDim = Color(0xFFA6B2CC), textFaint = Color(0xFF68748E), focus = Color(0xFFEFF5FF), onFocus = Color(0xFF0C1630)),
        NovaPalette("Emerald", Color(0xFF0D1815), Color(0xFF09110F), Color(0xFF14231F), Color(0xFF1D302A),
            Color(0xFF45E2A6), Color(0xFF7FD6FF), Color(0x331A6A4A), Color(0x221A5A6A),
            text = Color(0xFFEDF8F3), textDim = Color(0xFFA4C2B7), textFaint = Color(0xFF678A7E),
            focus = Color(0xFFF0FFF8), onFocus = Color(0xFF0C2018)),
        NovaPalette("Royal Purple", Color(0xFF150F22), Color(0xFF0E0A18), Color(0xFF20172F), Color(0xFF2C2040),
            Color(0xFFC495FF), Color(0xFFFF82D2), Color(0x33523A8A), Color(0x227A2A6A),
            text = Color(0xFFF3EEFA), textDim = Color(0xFFB8AACB), textFaint = Color(0xFF7A6D8E),
            focus = Color(0xFFF8F2FF), onFocus = Color(0xFF1C1030)),
        NovaPalette("Crimson Noir", Color(0xFF130C0E), Color(0xFF0C0809), Color(0xFF1F1417), Color(0xFF2C1C20),
            Color(0xFFFF5470), Color(0xFFFF9E7A), Color(0x337A1A2A), Color(0x225A1A2A),
            text = Color(0xFFF9EEF0), textDim = Color(0xFFC6A9AE), textFaint = Color(0xFF8A6D72),
            focus = Color(0xFFFFF1F3), onFocus = Color(0xFF2A0C12)),
        NovaPalette("Ocean Teal", Color(0xFF0B181C), Color(0xFF071013), Color(0xFF122429), Color(0xFF1A3238),
            Color(0xFF35D6C8), Color(0xFF5AAEFF), Color(0x331A5A6A), Color(0x221A3A7A),
            text = Color(0xFFECF7F8), textDim = Color(0xFFA2C0C4), textFaint = Color(0xFF648388),
            focus = Color(0xFFEFFEFF), onFocus = Color(0xFF082024)),
        NovaPalette("Sunset Gold", Color(0xFF17130F), Color(0xFF0F0C09), Color(0xFF231D17), Color(0xFF312820),
            Color(0xFFFFB84D), Color(0xFFFF7B5C), Color(0x336A4A1A), Color(0x227A2A1A),
            text = Color(0xFFFAF4EC), textDim = Color(0xFFC9B9A4), textFaint = Color(0xFF8A7B68),
            focus = Color(0xFFFFF8EC), onFocus = Color(0xFF261A0C)),
        NovaPalette("Rose Quartz", Color(0xFF181117), Color(0xFF100B0F), Color(0xFF241A22), Color(0xFF32242F),
            Color(0xFFFF93BC), Color(0xFFC9A2FF), Color(0x337A2A5A), Color(0x22523A7A),
            text = Color(0xFFFAEFF5), textDim = Color(0xFFC9AABD), textFaint = Color(0xFF8A6D80),
            focus = Color(0xFFFFF2F8), onFocus = Color(0xFF2A1022)),
        NovaPalette("Graphite", Color(0xFF131416), Color(0xFF0C0D0E), Color(0xFF1D1F22), Color(0xFF282B2F),
            Color(0xFFE6E8EC), Color(0xFF9AA0AA), Color(0x22FFFFFF), Color(0x14FFFFFF),
            text = Color(0xFFF2F3F5), textDim = Color(0xFFAEB2B9), textFaint = Color(0xFF70747C),
            focus = Color(0xFFFFFFFF), onFocus = Color(0xFF111214)),
        NovaPalette("Cyber Neon", Color(0xFF0A0B14), Color(0xFF06070D), Color(0xFF12142A), Color(0xFF1B1E3C),
            Color(0xFF00F0D0), Color(0xFFFF3EA5), Color(0x33006A7A), Color(0x227A0A5A),
            focus = Color(0xFFEFFFFC), onFocus = Color(0xFF060A14)),
    )

    var index by mutableIntStateOf(NovaApp.instance.prefs.theme.coerceIn(0, all.lastIndex))
        private set

    val current: NovaPalette get() = all[index]

    fun set(i: Int) {
        index = i.coerceIn(0, all.lastIndex)
        NovaApp.instance.prefs.theme = index
    }
}

/** Colours used across the app. They follow the theme picked in Settings and change instantly. */
object Nova {
    val Bg get() = NovaThemes.current.bg
    val BgDeep get() = NovaThemes.current.bgDeep
    val Surface get() = NovaThemes.current.surface
    val SurfaceHi get() = NovaThemes.current.surfaceHi
    val Glass = Color(0x33FFFFFF)
    val GlassDark get() = Bg.copy(alpha = 0.55f)
    val Stroke = Color(0x1FFFFFFF)

    val Text get() = NovaThemes.current.text
    val TextDim get() = NovaThemes.current.textDim
    val TextFaint get() = NovaThemes.current.textFaint

    /** Main highlight colour (named Cyan from the default theme) */
    val Cyan get() = NovaThemes.current.accent
    /** Second highlight colour (named Violet from the default theme) */
    val Violet get() = NovaThemes.current.accent2
    val Gold = Color(0xFFF5C76C)
    val Rose = Color(0xFFFF7A9C)
    val Green = Color(0xFF7BE0A6)

    /** Light pill behind a focused button, and the dark text on it */
    val Focus get() = NovaThemes.current.focus
    val OnFocus get() = NovaThemes.current.onFocus
    val Glow1 get() = NovaThemes.current.glow1
    val Glow2 get() = NovaThemes.current.glow2

    val AccentBrush get() = Brush.linearGradient(listOf(Cyan, Violet))
    val AccentBrushH get() = Brush.horizontalGradient(listOf(Cyan, Violet))
}

object NovaType {
    private val family = FontFamily.SansSerif
    val Display = TextStyle(fontFamily = family, fontWeight = FontWeight.Black, fontSize = 52.sp, letterSpacing = (-1).sp, lineHeight = 56.sp)
    val Title = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 30.sp, letterSpacing = (-0.5).sp)
    val Section = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, letterSpacing = 0.2.sp)
    val Body = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 23.sp)
    val Label = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 13.sp, letterSpacing = 0.3.sp)
    val Caption = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 1.4.sp)
    val Button = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, letterSpacing = 0.3.sp)
}

@Composable
fun NovaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Nova.Cyan,
            secondary = Nova.Violet,
            background = Nova.Bg,
            surface = Nova.Surface,
            onBackground = Nova.Text,
            onSurface = Nova.Text,
        )
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Nova.Bg)
                .background(
                    Brush.radialGradient(
                        listOf(Nova.Glow1, Color.Transparent),
                        center = Offset(1600f, 0f), radius = 1400f
                    )
                )
                .background(
                    Brush.radialGradient(
                        listOf(Nova.Glow2, Color.Transparent),
                        center = Offset(0f, 1100f), radius = 1200f
                    )
                )
        ) { content() }
    }
}
