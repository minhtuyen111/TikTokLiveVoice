package com.tlv.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

val BgDark = Color(0xFF0B0F1A)
val SurfaceDark = Color(0xFF141A2B)
val SurfaceHiDark = Color(0xFF1B2340)
val TextDim = Color(0xFFA8B0C4)
val Neon = Color(0xFFA855F7)
val Indigo = Color(0xFF7C83FF)
val Sky = Color(0xFF38BDF8)
val Ok = Color(0xFF34D399)
val Warn = Color(0xFFFBBF24)
val Bad = Color(0xFFF87171)

private val accents = listOf(Neon, Indigo, Sky)

class Motion(val reduce: Boolean)
val LocalMotion = staticCompositionLocalOf { Motion(false) }

@Composable
fun TlvTheme(dark: Boolean = true, accent: Int = 0, reduceMotion: Boolean = false, content: @Composable () -> Unit) {
    val a = accents.getOrElse(accent) { Neon }
    val scheme = if (dark) darkColorScheme(
        primary = a, onPrimary = Color.White,
        secondary = Sky, tertiary = Indigo,
        background = BgDark, onBackground = Color.White,
        surface = SurfaceDark, onSurface = Color.White,
        surfaceVariant = SurfaceHiDark, onSurfaceVariant = TextDim,
        surfaceContainer = SurfaceDark, surfaceContainerHigh = SurfaceHiDark,
        outline = Color(0xFF3A4366), outlineVariant = Color(0x22FFFFFF),
        error = Bad,
    ) else lightColorScheme(
        primary = a.copy(alpha = 1f), onPrimary = Color.White,
        secondary = Color(0xFF0284C7), tertiary = Color(0xFF4F46E5),
        background = Color(0xFFF5F6FB), surface = Color.White,
        surfaceVariant = Color(0xFFE8EBF7), onSurfaceVariant = Color(0xFF4A5170),
        surfaceContainer = Color.White, surfaceContainerHigh = Color(0xFFEDEFFA),
        outlineVariant = Color(0x22000000), error = Color(0xFFDC2626),
    )
    CompositionLocalProvider(LocalMotion provides Motion(reduceMotion)) {
        MaterialTheme(
            colorScheme = scheme,
            shapes = Shapes(
                small = RoundedCornerShape(12.dp),
                medium = RoundedCornerShape(18.dp),
                large = RoundedCornerShape(24.dp),
            ),
            content = content,
        )
    }
}
