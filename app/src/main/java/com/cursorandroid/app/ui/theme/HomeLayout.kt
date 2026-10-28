package com.cursorandroid.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class HomeDensity(val id: String, val label: String, val summary: String) {
    Compact("compact", "Compact", "More folders on screen"),
    Comfortable("comfortable", "Comfortable", "Roomier rows and icons"),
    Large("large", "Large", "Largest touch targets"),
    ;

    companion object {
        fun fromId(id: String?): HomeDensity = entries.firstOrNull { it.id == id } ?: Comfortable
    }
}

@Immutable
data class HomeLayout(
    val density: HomeDensity = HomeDensity.Comfortable,
) {
    val folderRowPadV: Dp
        get() = when (density) {
            HomeDensity.Compact -> 8.dp
            HomeDensity.Comfortable -> 13.dp
            HomeDensity.Large -> 17.dp
        }
    val folderRowGap: Dp
        get() = when (density) {
            HomeDensity.Compact -> 8.dp
            HomeDensity.Comfortable -> 10.dp
            HomeDensity.Large -> 12.dp
        }
    val folderIconSize: Dp
        get() = when (density) {
            HomeDensity.Compact -> 18.dp
            HomeDensity.Comfortable -> 26.dp
            HomeDensity.Large -> 32.dp
        }
    val folderTitleScale: Float
        get() = when (density) {
            HomeDensity.Compact -> 1f
            HomeDensity.Comfortable -> 1.08f
            HomeDensity.Large -> 1.16f
        }
    val folderCountScale: Float
        get() = when (density) {
            HomeDensity.Compact -> 1f
            HomeDensity.Comfortable -> 1.05f
            HomeDensity.Large -> 1.1f
        }
}

val LocalHomeLayout = staticCompositionLocalOf { HomeLayout() }
