package com.cursorandroid.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density

private val Ink = Color(0xFF0E0E10)
private val Surface = Color(0xFF1A1A1F)
private val OnInk = Color(0xFFF2F2F3)
private val Muted = Color(0xFF9A9AA3)

const val DefaultThemeColor = 0xFFF54E00.toInt()

val ThemeColorPresets = listOf(
    0xFFF54E00.toInt(),
    0xFFFF8A00.toInt(),
    0xFFEAB308.toInt(),
    0xFF84CC16.toInt(),
    0xFF22C55E.toInt(),
    0xFF14B8A6.toInt(),
    0xFF06B6D4.toInt(),
    0xFF3B82F6.toInt(),
    0xFF6366F1.toInt(),
    0xFF8B5CF6.toInt(),
    0xFFEC4899.toInt(),
    0xFFEF4444.toInt(),
)

fun themeAccent(argb: Int): Color {
    val packed = if ((argb ushr 24) == 0) DefaultThemeColor else argb
    return Color(packed)
}

@Composable
fun CursorTheme(
    accentArgb: Int = DefaultThemeColor,
    appearance: Appearance = Appearance(),
    content: @Composable () -> Unit,
) {
    val accent = themeAccent(accentArgb)
    val base = LocalDensity.current
    val scaled = remember(base, appearance.textScalePct) {
        Density(base.density, base.fontScale * clampTextScale(appearance.textScalePct) / 100f)
    }
    val typography = remember(appearance.uiFont) { Typography().withFont(appearance.uiFont.family()) }
    CompositionLocalProvider(LocalDensity provides scaled, LocalAppearance provides appearance) {
        CursorMaterial(accent, typography, content)
    }
}

private fun Typography.withFont(family: FontFamily?): Typography {
    if (family == null) return this
    fun TextStyle.f() = copy(fontFamily = family)
    return copy(
        displayLarge = displayLarge.f(), displayMedium = displayMedium.f(), displaySmall = displaySmall.f(),
        headlineLarge = headlineLarge.f(), headlineMedium = headlineMedium.f(), headlineSmall = headlineSmall.f(),
        titleLarge = titleLarge.f(), titleMedium = titleMedium.f(), titleSmall = titleSmall.f(),
        bodyLarge = bodyLarge.f(), bodyMedium = bodyMedium.f(), bodySmall = bodySmall.f(),
        labelLarge = labelLarge.f(), labelMedium = labelMedium.f(), labelSmall = labelSmall.f(),
    )
}

@Composable
private fun CursorMaterial(
    accent: Color,
    typography: Typography,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        typography = typography,
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
