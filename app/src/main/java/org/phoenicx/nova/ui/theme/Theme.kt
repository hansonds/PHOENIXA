package org.phoenicx.nova.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object Nova {
    val Bg = Color(0xFF121522)
    val BgDeep = Color(0xFF0D0F19)
    val Surface = Color(0xFF1B2032)
    val SurfaceHi = Color(0xFF262C44)
    val Glass = Color(0x33FFFFFF)
    val GlassDark = Color(0x8C121522)
    val Stroke = Color(0x1FFFFFFF)

    val Text = Color(0xFFEEF1FA)
    val TextDim = Color(0xFFA9B0C8)
    val TextFaint = Color(0xFF6E7690)

    val Cyan = Color(0xFF6CE5F5)
    val Violet = Color(0xFFA58BFF)
    val Gold = Color(0xFFF5C76C)
    val Rose = Color(0xFFFF7A9C)
    val Green = Color(0xFF7BE0A6)

    val AccentBrush = Brush.linearGradient(listOf(Cyan, Violet))
    val AccentBrushH = Brush.horizontalGradient(listOf(Cyan, Violet))
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
                        listOf(Color(0x332A3A7A), Color.Transparent),
                        center = Offset(1600f, 0f), radius = 1400f
                    )
                )
                .background(
                    Brush.radialGradient(
                        listOf(Color(0x22533A8A), Color.Transparent),
                        center = Offset(0f, 1100f), radius = 1200f
                    )
                )
        ) { content() }
    }
}
