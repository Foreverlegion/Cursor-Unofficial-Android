package com.cursorandroid.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Ink = Color(0xFF0E0E10)
private val Surface = Color(0xFF1A1A1F)
private val OnInk = Color(0xFFF2F2F3)
private val Muted = Color(0xFF9A9AA3)

const val DefaultThemeColor = 0xFFF54E00.toInt()

val ThemeColorPresets = listOf(
    0xFFF54E00.toInt(),
    0xFFFF8A00.toInt(),
    0xFFEAB308.toInt(),
    0xFF22C55E.toInt(),
    0xFF14B8A6.toInt(),
    0xFF3B82F6.toInt(),
    0xFF8B5CF6.toInt(),
    0xFFEC4899.toInt(),
)

fun themeAccent(argb: Int): Color {
    val packed = if ((argb ushr 24) == 0) DefaultThemeColor else argb
    return Color(packed)
}

@Composable
fun CursorTheme(
    accentArgb: Int = DefaultThemeColor,
    content: @Composable () -> Unit,
) {
    val accent = themeAccent(accentArgb)
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = accent,
            onPrimary = Color.White,
            background = Ink,
            onBackground = OnInk,
            surface = Surface,
            onSurface = OnInk,
            surfaceVariant = Color(0xFF24242B),
            onSurfaceVariant = Muted,
            outline = Color(0xFF3A3A44),
            error = Color(0xFFE5484D),
        ),
        content = content,
    )
}
