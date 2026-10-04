package com.cursorandroid.app.ui.thread

import com.cursorandroid.app.data.repo.TranscriptLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreadScrollTest {
    @Test
    fun thinkingGrowthChangesKeyWhenUserIsQueued() {
        val user = TranscriptLine("user-r1", "user", "do the work", "r1")
        val think = TranscriptLine("think-r1", "thinking", "plan", "r1")
        val queued = TranscriptLine("user-local-1", "user", "also this", queued = true)
        val rows = groupChatRows(listOf(user, think, queued), showTools = true, showThinking = true)
        val before = threadScrollKey(rows, think, showTyping = false)
        val after = threadScrollKey(rows, think.copy(text = "plan\nmore thinking"), showTyping = false)
        assertNotEquals(before, after)
    }

    @Test
    fun historyRowsDoNotIncludeThinking() {
        val user = TranscriptLine("user-r1", "user", "do the work", "r1")
        val think = TranscriptLine("think-r1", "thinking", "plan", "r1")
        val assistant = TranscriptLine("assistant-r1", "assistant", "done", "r1")
        val later = TranscriptLine("user-r2", "user", "next", "r2")
        val rows = groupChatRows(
            listOf(user, think, assistant, later),
            showTools = true,
            showThinking = true,
        )
        assertTrue(rows.none { it is ChatRow.Message && it.line.kind == "thinking" })
        assertEquals(listOf("user", "assistant", "user"), rows.filterIsInstance<ChatRow.Message>().map { it.line.kind })
    }

    @Test
    fun liveThinkingOnlyForTheWorkingRun() {
        val old = TranscriptLine("think-r1", "thinking", "old plan", "r1")
        val live = TranscriptLine("think-r2", "thinking", "now", "r2")
        assertEquals(live, liveThinkingLine(listOf(old, live), "r2", working = true))
        assertNull(liveThinkingLine(listOf(old, live), "r2", working = false))
        assertNull(liveThinkingLine(listOf(old), "r2", working = true))
    }
}
