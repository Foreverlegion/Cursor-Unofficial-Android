package com.cursorandroid.app.ui.theme

import com.cursorandroid.app.ui.thread.TextSegment
import com.cursorandroid.app.ui.thread.splitCodeBlocks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppearanceTest {
    @Test
    fun hexColorsParse() {
        assertEquals(0xFFF54E00.toInt(), parseHexColor("#F54E00"))
        assertEquals(0xFFF54E00.toInt(), parseHexColor("f54e00"))
        assertEquals(0xFFAABBCC.toInt(), parseHexColor("#abc"))
        assertNull(parseHexColor("#12"))
        assertNull(parseHexColor("#GGGGGG"))
        assertEquals("#F54E00", formatHexColor(0xFFF54E00.toInt()))
    }

    @Test
    fun textScaleIsClamped() {
        assertEquals(MIN_TEXT_SCALE, clampTextScale(10))
        assertEquals(MAX_TEXT_SCALE, clampTextScale(400))
        assertEquals(110, clampTextScale(110))
    }

    @Test
    fun unknownIdsFallBackToDefaults() {
        assertEquals(UiFont.System, UiFont.fromId("nope"))
        assertEquals(CodeFont.SystemMono, CodeFont.fromId(null))
        assertEquals(ChatDensity.Comfortable, ChatDensity.fromId("x"))
        assertEquals(UiFont.Inter, UiFont.fromId("inter"))
    }

    @Test
    fun compactDensityIsTighter() {
        val roomy = Appearance()
        val tight = Appearance(density = ChatDensity.Compact)
        assert(tight.bubblePadV < roomy.bubblePadV)
        assert(tight.rowGap < roomy.rowGap)
    }

    @Test
    fun plainTextIsOneSegment() {
        assertEquals(listOf(TextSegment("hello", false)), splitCodeBlocks("hello"))
    }

    @Test
    fun fencedBlocksSplitOut() {
        val out = splitCodeBlocks("Run:\n```kotlin\nval a = 1\n```\nDone")
        assertEquals(
            listOf(TextSegment("Run:", false), TextSegment("val a = 1", true), TextSegment("Done", false)),
            out,
        )
    }

    @Test
    fun unclosedFenceRunsToTheEnd() {
        val out = splitCodeBlocks("Try\n```\nline1\nline2")
        assertEquals(listOf(TextSegment("Try", false), TextSegment("line1\nline2", true)), out)
    }
}
