package com.cursorandroid.app.ui.settings

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class NestedScrollTest {
    private val settingsDir = listOf(
        File("src/main/java/com/cursorandroid/app/ui/settings"),
        File("app/src/main/java/com/cursorandroid/app/ui/settings"),
    ).first { it.isDirectory }

    private val verticalScrollers = Regex("""\bverticalScroll\(|\bLazyColumn\(|\bLazyVerticalGrid\(|\bLazyVerticalStaggeredGrid\(""")

    @Test
    fun settingsHostedPagesDoNotScrollVertically() {
        val host = File(settingsDir, "SettingsScreen.kt").readText()
        val pages = Regex("""SettingsPage\.\w+\s*->\s*(\w+Page)\(""").findAll(host).map { it.groupValues[1] }.toSet()
        assertTrue("no hosted pages found", pages.isNotEmpty())
        pages.forEach { page ->
            val src = File(settingsDir, "$page.kt")
            assertTrue("missing $src", src.isFile)
            val hit = verticalScrollers.find(src.readText())
            assertTrue("$page nests ${hit?.value} inside SettingsScreen's verticalScroll", hit == null)
        }
    }
}
