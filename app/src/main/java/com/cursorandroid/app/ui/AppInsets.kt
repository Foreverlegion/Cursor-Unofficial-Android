package com.cursorandroid.app.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

object AppInsets {
    val bars: WindowInsets
        @Composable
        get() = WindowInsets.systemBars.union(WindowInsets.displayCutout)

    val barsAndIme: WindowInsets
        @Composable
        get() = bars.union(WindowInsets.ime)

    val navigation: WindowInsets
        @Composable
        get() = WindowInsets.navigationBars.union(
            WindowInsets.displayCutout.only(WindowInsetsSides.Bottom),
        )
}

@Composable
fun Modifier.scaffoldBars(padding: PaddingValues): Modifier {
    return this
        .padding(padding)
        .consumeWindowInsets(padding)
        .windowInsetsPadding(AppInsets.barsAndIme)
}

@Composable
fun Modifier.screenInsets(): Modifier = windowInsetsPadding(AppInsets.barsAndIme)
