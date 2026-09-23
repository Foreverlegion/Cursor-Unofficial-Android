package com.cursorandroid.app.ui.thread

import com.cursorandroid.app.data.repo.FirstChatNotice
import com.cursorandroid.app.data.repo.TranscriptLine
import org.junit.Assert.assertEquals
import org.junit.Test

class ThreadRowsTest {

    @Test
    fun thinkingRendersAboveTheReply() {
        val rows = groupChatRows(
            listOf(
                line("user-r1", "user", "hello", "r1"),
                line("assistant-r1", "assistant", "done", "r1"),
                line("think-r1", "thinking", "plan", "r1"),
            ),
            showTools = true,
            showThinking = true,
        )

        assertEquals(listOf("user", "thinking", "assistant"), kinds(rows))
    }

    @Test
    fun startupNoticeStaysAtTopOfTheThread() {
        val rows = groupChatRows(
            listOf(
                line("user-r1", "user", "hello", "r1"),
                line("assistant-r1", "assistant", "done", "r1"),
                FirstChatNotice.line("cloud"),
            ),
            showTools = true,
            showThinking = true,
        )

        assertEquals(listOf("notice", "user", "assistant"), kinds(rows))
        assertEquals(FirstChatNotice.ID, (rows.first() as ChatRow.Message).line.id)
    }

    private fun kinds(rows: List<ChatRow>): List<String> {
        return rows.map { row ->
            when (row) {
                is ChatRow.Message -> row.line.kind
                is ChatRow.Tools -> "tool"
            }
        }
    }

    private fun line(id: String, kind: String, text: String, runId: String) =
        TranscriptLine(id = id, kind = kind, text = text, runId = runId)
}
