package com.cursorandroid.app.ui.thread

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtifactNameTest {
    @Test
    fun shortNamesAreUntouched() {
        assertEquals("fleet-widget-preview.png", middleEllipsize("fleet-widget-preview.png"))
    }

    @Test
    fun veryLongNamesKeepBothEndsAndTheExtension() {
        val name = "a".repeat(200) + ".png"
        val shown = middleEllipsize(name)
        assertEquals(MAX_HISTORY_NAME, shown.length)
        assertTrue(shown.startsWith("aaaa"))
        assertTrue(shown.endsWith(".png"))
        assertTrue('…' in shown)
    }
}
