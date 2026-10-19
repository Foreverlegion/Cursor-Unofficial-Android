package com.cursorandroid.app.ui.thread

import com.cursorandroid.app.data.repo.FirstChatNotice
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
    fun finishedThinkingStaysInTheThread() {
        val user = TranscriptLine("user-r1", "user", "do the work", "r1")
        val think = TranscriptLine("think-r1", "thinking", "plan", "r1")
        val assistant = TranscriptLine("assistant-r1", "assistant", "done", "r1")
        val later = TranscriptLine("user-r2", "user", "next", "r2")
        val rows = groupChatRows(
            listOf(user, think, assistant, later),
            showTools = true,
            showThinking = true,
        )
        assertEquals(
            listOf("user", "thinking", "assistant", "user"),
            rows.filterIsInstance<ChatRow.Message>().map { it.line.kind },
        )
        val live = groupChatRows(
            listOf(user, think, assistant, later),
            showTools = true,
            showThinking = true,
            liveThinkId = think.id,
        )
        assertTrue(live.none { it is ChatRow.Message && it.line.id == think.id })
    }

    @Test
    fun startupNoticeDoesNotHideTheFirstTurnReadouts() {
        val lines = listOf(
            FirstChatNotice.line("cloud"),
            TranscriptLine("user-r1", "user", "hello", "r1"),
            TranscriptLine("think-r1", "thinking", "plan the env", "r1"),
            TranscriptLine("tool-1", "tool", "read\nREADME.md", "r1"),
            TranscriptLine("assistant-r1", "assistant", "done", "r1"),
        )
        val shown = groupChatRows(lines, showTools = true, showThinking = true)
        assertEquals(FirstChatNotice.ID, (shown.first() as ChatRow.Message).line.id)
        assertEquals(1, shown.filterIsInstance<ChatRow.Tools>().sumOf { it.tools.size })
        assertTrue(shown.any { it is ChatRow.Message && it.line.id == "think-r1" })

        val live = groupChatRows(lines, showTools = true, showThinking = true, liveThinkId = "think-r1")
        assertEquals(1, live.filterIsInstance<ChatRow.Tools>().sumOf { it.tools.size })
        assertTrue(live.none { it is ChatRow.Message && it.line.id == "think-r1" })
        assertTrue(live.any { it is ChatRow.Message && it.line.id == FirstChatNotice.ID })
    }

    @Test
    fun liveToolCallsLeaveTheListAndStillMoveTheScrollKey() {
        val user = TranscriptLine("user-r1", "user", "go", "r1")
        val tool = TranscriptLine("t1", "tool", "read\nREADME.md", "r1")
        val think = TranscriptLine("think-r1", "thinking", "plan", "r1")
        val lines = listOf(user, tool, think)
        val rows = groupChatRows(
            lines,
            showTools = true,
            showThinking = true,
            liveThinkId = think.id,
            liveToolIds = setOf(tool.id),
        )
        assertTrue(rows.none { it is ChatRow.Tools })
        assertTrue(rows.none { it is ChatRow.Message && it.line.id == think.id })
        val before = threadScrollKey(rows, think, showTyping = false, liveTools = listOf(tool))
        val after = threadScrollKey(
            rows,
            think,
            showTyping = false,
            liveTools = listOf(tool.copy(text = "read\nREADME.md\nmore")),
        )
        assertNotEquals(before, after)
        assertEquals(emptyList<TranscriptLine>(), liveToolLines(lines, "r1", working = false))
        assertEquals(listOf(tool), liveToolLines(lines, "r1", working = true))
    }

    @Test
    fun hiddenToolCallsStayHiddenWhileTheRunIsLive() {
        val rows = groupChatRows(
            listOf(
                TranscriptLine("user-r1", "user", "go", "r1"),
                TranscriptLine("t1", "tool", "read", "r1"),
            ),
            showTools = false,
            showThinking = true,
            liveToolIds = setOf("t1"),
        )
        assertTrue(rows.none { it is ChatRow.Tools })
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
