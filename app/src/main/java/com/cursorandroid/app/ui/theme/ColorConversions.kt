package com.cursorandroid.app.ui.theme

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Hue in degrees [0, 360), saturation and value in [0, 1]. */
data class HsvColor(val hue: Float, val saturation: Float, val value: Float)

fun displayArgb(color: Int, defaultArgb: Int): Int =
    if (color == 0) defaultArgb else color

fun opaqueArgb(rgb: Int): Int = 0xFF000000.toInt() or (rgb and 0xFFFFFF)

fun argbToHsv(argb: Int): HsvColor {
    val r = ((argb shr 16) and 0xFF) / 255f
    val g = ((argb shr 8) and 0xFF) / 255f
    val b = (argb and 0xFF) / 255f
    val maxC = max(r, max(g, b))
    val minC = min(r, min(g, b))
    val delta = maxC - minC
    val hue = when {
        delta <= 1e-5f -> 0f
        maxC == r -> 60f * (((g - b) / delta) % 6f)
        maxC == g -> 60f * (((b - r) / delta) + 2f)
        else -> 60f * (((r - g) / delta) + 4f)
    }
    val h = (if (hue < 0f) hue + 360f else hue) % 360f
    val s = if (maxC <= 1e-5f) 0f else delta / maxC
    return HsvColor(h, s, maxC)
}

fun hsvToArgb(hsv: HsvColor, alpha: Int = 0xFF): Int {
    val h = ((hsv.hue % 360f) + 360f) % 360f
    val s = hsv.saturation.coerceIn(0f, 1f)
    val v = hsv.value.coerceIn(0f, 1f)
    if (s <= 1e-5f) {
        val gray = (v * 255f + 0.5f).toInt()
        return (alpha shl 24) or (gray shl 16) or (gray shl 8) or gray
    }
    val sector = h / 60f
    val i = sector.toInt()
    val f = sector - i
    val p = v * (1f - s)
    val q = v * (1f - s * f)
    val t = v * (1f - s * (1f - f))
    val (r, g, b) = when (i % 6) {
        0 -> Triple(v, t, p)
        1 -> Triple(q, v, p)
        2 -> Triple(p, v, t)
        3 -> Triple(p, q, v)
        4 -> Triple(t, p, v)
        else -> Triple(v, p, q)
    }
    val ri = (r * 255f + 0.5f).toInt()
    val gi = (g * 255f + 0.5f).toInt()
    val bi = (b * 255f + 0.5f).toInt()
    return (alpha shl 24) or (ri shl 16) or (gi shl 8) or bi
}

fun hsvToArgb(hue: Float, saturation: Float, value: Float, alpha: Int = 0xFF): Int =
    hsvToArgb(HsvColor(hue, saturation, value), alpha)

/** Maps a touch inside the wheel disk to hue (degrees) and saturation. */
fun hsFromWheelOffset(centerX: Float, centerY: Float, radius: Float, x: Float, y: Float): HsvColor? {
    if (radius <= 0f) return null
    val dx = x - centerX
    val dy = y - centerY
    val dist = kotlin.math.sqrt(dx * dx + dy * dy)
    if (dist > radius) return null
    val angle = kotlin.math.atan2(dy, dx)
    val hue = Math.toDegrees(angle.toDouble()).toFloat().let { if (it < 0f) it + 360f else it }
    val sat = (dist / radius).coerceIn(0f, 1f)
    return HsvColor(hue, sat, 0f)
}

fun wheelMarkerOffset(centerX: Float, centerY: Float, radius: Float, hsv: HsvColor): Pair<Float, Float> {
    val dist = hsv.saturation.coerceIn(0f, 1f) * radius
    val rad = Math.toRadians(hsv.hue.toDouble())
    val x = centerX + dist * kotlin.math.cos(rad).toFloat()
    val y = centerY + dist * kotlin.math.sin(rad).toFloat()
    return x to y
}

fun hsvRoundTripCloseEnough(original: Int, tolerance: Float = 0.02f): Boolean {
    val hsv = argbToHsv(original)
    val again = hsvToArgb(hsv)
    if (again != original) {
        val dr = abs(((again shr 16) and 0xFF) - ((original shr 16) and 0xFF))
        val dg = abs(((again shr 8) and 0xFF) - ((original shr 8) and 0xFF))
        val db = abs((again and 0xFF) - (original and 0xFF))
        if (max(dr, max(dg, db)) > 2) return false
    }
    val back = argbToHsv(again)
    return abs(back.hue - hsv.hue) < 1.5f &&
        abs(back.saturation - hsv.saturation) <= tolerance &&
        abs(back.value - hsv.value) <= tolerance
}
