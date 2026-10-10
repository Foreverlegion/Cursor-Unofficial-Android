package com.cursorandroid.app.ui.thread

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RunLineTest {
    @Test
    fun formatsDurationAndTokens() {
        assertEquals("12s", formatDuration(12_357))
        assertEquals("2m 5s", formatDuration(125_000))
        assertEquals("1h 1m", formatDuration(3_660_000))
        assertEquals("12s · 36,170 tokens", lastRunLine(12_357, 36_170))
        assertEquals("36,170 tokens", lastRunLine(null, 36_170))
        assertNull(lastRunLine(0, 0))
    }
}
