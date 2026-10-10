package com.cursorandroid.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.cursorandroid.app.R

enum class UiFont(val id: String, val label: String) {
    System("system", "System default"),
    Inter("inter", "Inter"),
    Serif("serif", "Serif"),
    RobotoMono("roboto_mono", "Roboto Mono"),
    JetBrainsMono("jetbrains_mono", "JetBrains Mono"),
    ;

    companion object {
        fun fromId(id: String?): UiFont = entries.firstOrNull { it.id == id } ?: System
    }
}

enum class CodeFont(val id: String, val label: String) {
    SystemMono("system_mono", "System monospace"),
    RobotoMono("roboto_mono", "Roboto Mono"),
    JetBrainsMono("jetbrains_mono", "JetBrains Mono"),
    ;

    companion object {
        fun fromId(id: String?): CodeFont = entries.firstOrNull { it.id == id } ?: SystemMono
    }
}

enum class ChatDensity(val id: String, val label: String, val summary: String) {
    Comfortable("comfortable", "Comfortable", "Roomy bubbles"),
    Compact("compact", "Compact", "Tighter bubbles, more per screen"),
    ;

    companion object {
        fun fromId(id: String?): ChatDensity = entries.firstOrNull { it.id == id } ?: Comfortable
    }
}

const val MIN_TEXT_SCALE = 85
const val MAX_TEXT_SCALE = 140
const val DEFAULT_TEXT_SCALE = 100

fun clampTextScale(percent: Int): Int = percent.coerceIn(MIN_TEXT_SCALE, MAX_TEXT_SCALE)

/** Parses #RGB, #RRGGBB, or RRGGBB into an opaque ARGB int, or null when it is not a color. */
fun parseHexColor(input: String): Int? {
    val raw = input.trim().removePrefix("#")
    val full = when (raw.length) {
        3 -> raw.map { "$it$it" }.joinToString("")
        6 -> raw
        else -> return null
    }
    val rgb = full.toIntOrNull(16) ?: return null
    return 0xFF000000.toInt() or rgb
}

fun formatHexColor(argb: Int): String = "#%06X".format(argb and 0xFFFFFF)

@Immutable
data class Appearance(
    val uiFont: UiFont = UiFont.System,
    val codeFont: CodeFont = CodeFont.SystemMono,
    val textScalePct: Int = DEFAULT_TEXT_SCALE,
    val density: ChatDensity = ChatDensity.Comfortable,
) {
    val bubblePadH get() = if (density == ChatDensity.Compact) 11 else 14
    val bubblePadV get() = if (density == ChatDensity.Compact) 6 else 10
    val rowGap get() = if (density == ChatDensity.Compact) 4 else 8
}

val LocalAppearance = staticCompositionLocalOf { Appearance() }

@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int, weight: Int) = Font(
    resId = res,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

private val WEIGHTS = listOf(400, 500, 600, 700)

private val InterFamily = FontFamily(WEIGHTS.map { variable(R.font.inter_variable, it) })
private val RobotoMonoFamily = FontFamily(WEIGHTS.map { variable(R.font.roboto_mono_variable, it) })
private val JetBrainsMonoFamily = FontFamily(WEIGHTS.map { variable(R.font.jetbrains_mono_variable, it) })

fun UiFont.family(): FontFamily? = when (this) {
    UiFont.System -> null
    UiFont.Inter -> InterFamily
    UiFont.Serif -> FontFamily.Serif
    UiFont.RobotoMono -> RobotoMonoFamily
    UiFont.JetBrainsMono -> JetBrainsMonoFamily
}

fun CodeFont.family(): FontFamily = when (this) {
    CodeFont.SystemMono -> FontFamily.Monospace
    CodeFont.RobotoMono -> RobotoMonoFamily
    CodeFont.JetBrainsMono -> JetBrainsMonoFamily
}
