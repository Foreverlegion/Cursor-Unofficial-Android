package com.cursorandroid.app.ui.thread

import com.cursorandroid.app.data.repo.TranscriptLine
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkBarTest {

    @Test
    fun idleWaitingAgentDoesNotShowBar() {
        val lines = listOf(
            line("user-r1", "user", "do the keys", "r1"),
            line("assistant-r1", "assistant", "done", "r1"),
            line("think-r1", "thinking", "plan", "r1"),
        )
        assertFalse(
            showWorkBar(
                lines = lines,
                receiving = false,
                busy = false,
                agentStatus = "RUNNING",
                runStatus = "RUNNING",
            ),
        )
    }

    @Test
    fun awaitingReplyShowsBar() {
        val lines = listOf(line("user-r2", "user", "next", "r2"))
        assertTrue(
            showWorkBar(
                lines = lines,
                receiving = false,
                busy = false,
                agentStatus = "RUNNING",
                runStatus = "RUNNING",
            ),
        )
    }

    @Test
    fun receivingTokensShowsBar() {
        val lines = listOf(
            line("user-r1", "user", "go", "r1"),
            line("assistant-r1", "assistant", "working", "r1"),
        )
        assertTrue(
            showWorkBar(
                lines = lines,
                receiving = true,
                busy = false,
                agentStatus = "RUNNING",
                runStatus = "RUNNING",
            ),
        )
    }

    @Test
    fun creatingShowsBar() {
        assertTrue(
            showWorkBar(
                lines = emptyList(),
                receiving = false,
                busy = false,
                agentStatus = "CREATING",
                runStatus = null,
            ),
        )
    }

    private fun line(id: String, kind: String, text: String, runId: String) =
        TranscriptLine(id = id, kind = kind, text = text, runId = runId)
}
