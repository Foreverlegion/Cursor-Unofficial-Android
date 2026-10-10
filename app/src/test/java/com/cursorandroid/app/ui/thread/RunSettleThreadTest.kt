package com.cursorandroid.app.ui.thread

import com.cursorandroid.app.data.api.ConversationMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RunSettleThreadTest {
    @Test
    fun segmentsAfterToolCallsAreSeparated() {
        val buf = StringBuilder()
        appendAssistantSegment(buf, "ship a release APK.", afterOther = false)
        appendAssistantSegment(buf, "The compositor returns", afterOther = true)
        assertEquals("ship a release APK.\n\nThe compositor returns", buf.toString())
    }

    @Test
    fun consecutiveChunksStayJoined() {
        val buf = StringBuilder()
        appendAssistantSegment(buf, "The comp", afterOther = false)
        appendAssistantSegment(buf, "ositor", afterOther = false)
        assertEquals("The compositor", buf.toString())
    }

    @Test
    fun firstSegmentGetsNoLeadingBreak() {
        val buf = StringBuilder()
        appendAssistantSegment(buf, "hello", afterOther = true)
        assertEquals("hello", buf.toString())
    }

    @Test
    fun conversationCoversResultOnlyOnceListed() {
        val before = listOf(ConversationMessage("m1", "assistant_message", "working"))
        val after = before + ConversationMessage("m2", "assistant_message", "All done.\n\nShipped.")
        assertFalse(conversationCovers(before, "All done. Shipped."))
        assertTrue(conversationCovers(after, "All done. Shipped."))
        assertTrue(conversationCovers(before, null))
        assertFalse(conversationCovers(emptyList(), ""))
    }
}
